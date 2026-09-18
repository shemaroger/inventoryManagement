package com.company.tai.sales.service;

import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.StockAdjustmentRequest;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.inventory.service.StockService;
import com.company.tai.sales.dto.*;
import com.company.tai.sales.entity.*;
import com.company.tai.sales.repository.SaleRepository;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockService stockService;
    private final UserRepository userRepository;
    private final JournalService journalService;

    // Both reads need @Transactional: same open-in-view=false / lazy-collection reasoning as
    // PurchaseOrderService.search()/getById() — toDto() touches sale.getLines() after the
    // repository call returns.
    @Transactional(readOnly = true)
    public Page<SaleDto> search(Long customerId, SaleStatus status, String searchText, Pageable pageable) {
        return saleRepository.search(customerId, status, searchText, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public SaleDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public SaleDto create(CreateSaleRequest request) {
        Customer customer = customerService.findOrThrow(request.customerId());
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.warehouseId()));

        Sale sale = Sale.builder()
                .customer(customer)
                .warehouse(warehouse)
                .status(SaleStatus.QUOTATION)
                .paymentType(request.paymentType())
                .saleDate(request.saleDate() != null ? request.saleDate() : LocalDate.now())
                .notes(request.notes())
                .createdBy(currentUser())
                .build();

        for (SaleLineRequest lineReq : request.lines()) {
            sale.getLines().add(buildLine(sale, lineReq));
        }

        return toDto(saleRepository.save(sale));
    }

    private SaleLine buildLine(Sale sale, SaleLineRequest lineReq) {
        Product product = productRepository.findById(lineReq.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + lineReq.productId()));
        BigDecimal unitPrice = lineReq.unitPrice() != null ? lineReq.unitPrice() : product.getSellingPrice();
        return SaleLine.builder()
                .sale(sale)
                .product(product)
                .quantity(lineReq.quantity())
                .unitPrice(unitPrice)
                .discountPercent(lineReq.discountPercent() != null ? lineReq.discountPercent() : BigDecimal.ZERO)
                .build();
    }

    // A quotation becoming CONFIRMED is a commitment from the customer, but stock hasn't moved
    // yet — so this only checks current availability per line and returns warnings for any line
    // that no longer fits (stock may have shifted since the quote was drafted). It does not
    // block the transition and does not reserve/hold stock; COMPLETED is the only status that
    // actually touches StockService.
    @Transactional
    public ConfirmSaleResult confirm(Long id) {
        Sale sale = findOrThrow(id);
        if (sale.getStatus() != SaleStatus.QUOTATION) {
            throw new BusinessRuleException("Only a QUOTATION can be confirmed (current status: " + sale.getStatus() + ")");
        }

        List<String> warnings = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            BigDecimal available = stockItemRepository
                    .findByProductIdAndWarehouseId(line.getProduct().getId(), sale.getWarehouse().getId())
                    .map(si -> si.getQuantity())
                    .orElse(BigDecimal.ZERO);
            if (line.getQuantity().compareTo(available) > 0) {
                warnings.add("Only " + available + " of " + line.getProduct().getSku() + " available in "
                        + sale.getWarehouse().getName() + ", but " + line.getQuantity() + " were quoted");
            }
        }

        sale.setStatus(SaleStatus.CONFIRMED);
        return new ConfirmSaleResult(toDto(sale), warnings);
    }

    // COMPLETED is where stock actually moves and, for credit sales, where the customer's
    // balance actually increases — both effects are applied exactly once here, never anywhere
    // else, so this arithmetic stays in one place per the original spec.
    @Transactional
    public SaleDto complete(Long id, boolean override) {
        Sale sale = findOrThrow(id);
        if (sale.getStatus() != SaleStatus.CONFIRMED) {
            throw new BusinessRuleException("Only a CONFIRMED sale can be completed (current status: " + sale.getStatus() + ")");
        }

        if (override && !currentUserIsAdmin()) {
            throw new BusinessRuleException("Only an ADMIN can override the credit limit check");
        }

        if (sale.getPaymentType() == PaymentType.CREDIT && !override) {
            Customer customer = sale.getCustomer();
            BigDecimal available = customer.getCreditLimit().subtract(customer.getCurrentBalance());
            BigDecimal total = grandTotal(sale);
            if (total.compareTo(available) > 0) {
                throw new BusinessRuleException(
                        "Sale total " + total + " (including VAT) exceeds " + customer.getName() + "'s remaining credit limit of "
                                + available + " (limit " + customer.getCreditLimit() + ", currently owing "
                                + customer.getCurrentBalance() + "). Pass override=true as an admin to proceed anyway.");
            }
        }

        for (SaleLine line : sale.getLines()) {
            // Reuses StockService's existing insufficient-stock guard completely — it already
            // reports exactly which product/warehouse came up short, so no need to duplicate
            // that check here. Uses the no-auto-ledger-posting variant since journalService
            // .postSaleEntry() below already posts the full accounting effect (Revenue/COGS/
            // VAT/Inventory) for this exact stock movement — auto-posting here too would double it.
            stockService.applyAdjustmentWithoutLedgerPosting(new StockAdjustmentRequest(
                    line.getProduct().getId(),
                    sale.getWarehouse().getId(),
                    AdjustmentType.DECREASE,
                    line.getQuantity(),
                    "Sold on Sale #" + sale.getId()
            ));
        }

        if (sale.getPaymentType() == PaymentType.CREDIT) {
            Customer customer = sale.getCustomer();
            customer.setCurrentBalance(customer.getCurrentBalance().add(grandTotal(sale)));
        }

        sale.setStatus(SaleStatus.COMPLETED);
        // Same @Transactional boundary as the stock decrease above — a journal-posting failure
        // (e.g. a missing system account) rolls back the whole completion rather than leaving
        // stock moved with no matching accounting entry.
        journalService.postSaleEntry(sale);
        return toDto(sale);
    }

    @Transactional
    public SaleDto cancel(Long id) {
        Sale sale = findOrThrow(id);
        if (sale.getStatus() != SaleStatus.QUOTATION && sale.getStatus() != SaleStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Cannot cancel a sale with status " + sale.getStatus() + " — a completed sale requires a return, not a cancel");
        }
        sale.setStatus(SaleStatus.CANCELLED);
        return toDto(sale);
    }

    // "Total" here means the VAT-exclusive subtotal — the price actually charged for goods,
    // before the 18% VAT added on top at sale time (see VatConstants). grandTotal() is what the
    // customer actually pays / owes, and is what credit-limit checks and Customer.currentBalance
    // must use — using the net subtotal there would silently under-count real exposure by 18%.
    private BigDecimal saleTotal(Sale sale) {
        return sale.getLines().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Exposed for SalePaymentService's overpayment guard — same VAT-inclusive figure used for
    // the credit-limit check and Customer.currentBalance above, so "balance owed" on a sale
    // always means the same number everywhere.
    public BigDecimal grandTotal(Sale sale) {
        BigDecimal subtotal = saleTotal(sale);
        return subtotal.add(VatConstants.vatOn(subtotal));
    }

    private BigDecimal lineTotal(SaleLine line) {
        return line.getUnitPrice()
                .multiply(line.getQuantity())
                .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))));
    }

    Sale findOrThrow(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private SaleDto toDto(Sale sale) {
        List<SaleLineDto> lineDtos = sale.getLines().stream()
                .map(l -> new SaleLineDto(
                        l.getId(), l.getProduct().getId(), l.getProduct().getName(), l.getProduct().getSku(),
                        l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), lineTotal(l)
                ))
                .toList();

        BigDecimal subtotal = saleTotal(sale);
        BigDecimal vatAmount = VatConstants.vatOn(subtotal);

        return new SaleDto(
                sale.getId(),
                sale.getCustomer().getId(), sale.getCustomer().getName(),
                sale.getWarehouse().getId(), sale.getWarehouse().getName(),
                sale.getStatus(), sale.getPaymentType(), sale.getSaleDate(), lineDtos,
                subtotal, vatAmount, subtotal.add(vatAmount), sale.getNotes(),
                sale.getCreatedBy() != null ? sale.getCreatedBy().getFullName() : null,
                sale.getCreatedAt(), sale.getUpdatedAt()
        );
    }
}
