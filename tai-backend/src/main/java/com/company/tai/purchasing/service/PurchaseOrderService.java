package com.company.tai.purchasing.service;

import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.StockAdjustmentRequest;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.inventory.service.StockService;
import com.company.tai.purchasing.dto.*;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchaseOrderLine;
import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import com.company.tai.purchasing.entity.PurchasePaymentType;
import com.company.tai.purchasing.entity.Supplier;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierService supplierService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockService stockService;
    private final UserRepository userRepository;
    private final JournalService journalService;

    // Both reads need @Transactional: with spring.jpa.open-in-view=false, the Hibernate session
    // that findOrThrow()/search() runs in closes as soon as the repository call returns — and
    // toDto() lazily loads po.getLines() and po.getSupplier()/getWarehouse() after that point.
    // Without this, both throw LazyInitializationException ("no session").
    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> search(Long supplierId, PurchaseOrderStatus status, String searchText, Pageable pageable) {
        return purchaseOrderRepository.search(supplierId, status, searchText, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public PurchaseOrderDto create(CreatePurchaseOrderRequest request) {
        Supplier supplier = supplierService.findOrThrow(request.supplierId());
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.warehouseId()));

        PurchaseOrder po = PurchaseOrder.builder()
                .supplier(supplier)
                .warehouse(warehouse)
                .status(PurchaseOrderStatus.DRAFT)
                .orderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now())
                .paymentType(request.paymentType() != null ? request.paymentType() : PurchasePaymentType.CREDIT)
                .notes(request.notes())
                .createdBy(currentUser())
                .build();

        for (PurchaseOrderLineRequest lineReq : request.lines()) {
            po.getLines().add(buildLine(po, lineReq));
        }

        return toDto(purchaseOrderRepository.save(po));
    }

    // Edit is only allowed while DRAFT — once SUBMITTED, the PO is locked (matches the existing
    // submit()/receive() guards). This replaces supplier/warehouse/orderDate/notes and the full
    // line set; orphanRemoval on PurchaseOrder.lines cleans up any lines no longer present.
    @Transactional
    public PurchaseOrderDto update(Long id, CreatePurchaseOrderRequest request) {
        PurchaseOrder po = findOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessRuleException("Only a DRAFT purchase order can be edited (current status: " + po.getStatus() + ")");
        }

        Supplier supplier = supplierService.findOrThrow(request.supplierId());
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.warehouseId()));

        po.setSupplier(supplier);
        po.setWarehouse(warehouse);
        po.setOrderDate(request.orderDate() != null ? request.orderDate() : po.getOrderDate());
        po.setPaymentType(request.paymentType() != null ? request.paymentType() : po.getPaymentType());
        po.setNotes(request.notes());

        po.getLines().clear();
        for (PurchaseOrderLineRequest lineReq : request.lines()) {
            po.getLines().add(buildLine(po, lineReq));
        }

        purchaseOrderRepository.saveAndFlush(po);
        return toDto(po);
    }

    private PurchaseOrderLine buildLine(PurchaseOrder po, PurchaseOrderLineRequest lineReq) {
        Product product = productRepository.findById(lineReq.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + lineReq.productId()));
        return PurchaseOrderLine.builder()
                .purchaseOrder(po)
                .product(product)
                .quantityOrdered(lineReq.quantityOrdered())
                .quantityReceived(BigDecimal.ZERO)
                .unitCost(lineReq.unitCost())
                .build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    // Submitting is what "locks" the PO — there is no edit-lines endpoint at all (before or
    // after submit), so this simply guards the status transition rather than un-locking or
    // re-locking any editable state.
    @Transactional
    public PurchaseOrderDto submit(Long id) {
        PurchaseOrder po = findOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessRuleException("Only a DRAFT purchase order can be submitted (current status: " + po.getStatus() + ")");
        }
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto cancel(Long id) {
        PurchaseOrder po = findOrThrow(id);
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot cancel a purchase order with status " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto receive(Long id, ReceivePurchaseOrderRequest request) {
        PurchaseOrder po = findOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.SUBMITTED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessRuleException("Cannot receive against a purchase order with status " + po.getStatus());
        }

        BigDecimal receivedValue = BigDecimal.ZERO;

        for (ReceiveLineRequest lineReq : request.lines()) {
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> l.getId().equals(lineReq.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order line not found with id: " + lineReq.lineId()));

            BigDecimal remaining = line.getQuantityOrdered().subtract(line.getQuantityReceived());
            if (lineReq.quantityReceived().compareTo(remaining) > 0) {
                throw new BusinessRuleException(
                        "Cannot receive " + lineReq.quantityReceived() + " of " + line.getProduct().getSku()
                                + " — only " + remaining + " remain outstanding on this line");
            }

            // Reuses the existing stock-adjustment machinery rather than duplicating the
            // quantity math; the PO reference is carried in the adjustment's reason field
            // since a dedicated GRN audit table would just duplicate what's already here.
            // Uses the no-auto-ledger-posting variant since journalService
            // .postPurchaseReceiptEntry() below already posts the accounting effect
            // (Inventory/Input VAT/Accounts Payable) for this exact stock movement.
            stockService.applyAdjustmentWithoutLedgerPosting(new StockAdjustmentRequest(
                    line.getProduct().getId(),
                    po.getWarehouse().getId(),
                    AdjustmentType.INCREASE,
                    lineReq.quantityReceived(),
                    "Received against PO #" + po.getId()
            ));

            receivedValue = receivedValue.add(lineReq.quantityReceived().multiply(line.getUnitCost()));
            line.setQuantityReceived(line.getQuantityReceived().add(lineReq.quantityReceived()));
        }

        boolean allFullyReceived = po.getLines().stream()
                .allMatch(l -> l.getQuantityReceived().compareTo(l.getQuantityOrdered()) >= 0);
        po.setStatus(allFullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);

        // Same @Transactional boundary as the stock increases above — see the note on
        // SaleService.complete() for why this isn't a fire-and-forget call.
        journalService.postPurchaseReceiptEntry(po, receivedValue);

        return toDto(po);
    }

    private PurchaseOrder findOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with id: " + id));
    }

    private PurchaseOrderDto toDto(PurchaseOrder po) {
        List<PurchaseOrderLineDto> lineDtos = po.getLines().stream()
                .map(l -> new PurchaseOrderLineDto(
                        l.getId(), l.getProduct().getId(), l.getProduct().getName(), l.getProduct().getSku(),
                        l.getQuantityOrdered(), l.getQuantityReceived(), l.getUnitCost()
                ))
                .toList();

        return new PurchaseOrderDto(
                po.getId(),
                po.getSupplier().getId(), po.getSupplier().getName(),
                po.getWarehouse().getId(), po.getWarehouse().getName(),
                po.getStatus(), po.getPaymentType(), po.getOrderDate(), lineDtos,
                po.getNotes(), po.getCreatedBy() != null ? po.getCreatedBy().getFullName() : null,
                po.getCreatedAt(), po.getUpdatedAt()
        );
    }
}
