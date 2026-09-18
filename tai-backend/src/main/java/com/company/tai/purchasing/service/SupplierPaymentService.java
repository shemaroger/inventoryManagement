package com.company.tai.purchasing.service;

import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.purchasing.dto.SupplierPaymentDto;
import com.company.tai.purchasing.dto.SupplierPaymentRequest;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchaseOrderLine;
import com.company.tai.purchasing.entity.SupplierPayment;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.purchasing.repository.SupplierPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierPaymentService {

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final JournalService journalService;

    public List<SupplierPaymentDto> listForPurchaseOrder(Long purchaseOrderId) {
        return supplierPaymentRepository.findByPurchaseOrderIdOrderByPaymentDateDesc(purchaseOrderId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public SupplierPaymentDto recordForPurchaseOrder(Long purchaseOrderId, SupplierPaymentRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with id: " + purchaseOrderId));

        // Outstanding balance mirrors the Accounts Payable liability JournalService posts in
        // postPurchaseReceiptEntry(): (received qty * unit cost) + VAT, summed across lines,
        // less whatever has already been paid against this PO. A payment can never exceed that.
        BigDecimal receivedValue = po.getLines().stream()
                .map(l -> l.getQuantityReceived().multiply(l.getUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiceTotal = receivedValue.add(VatConstants.vatOn(receivedValue));

        BigDecimal alreadyPaid = supplierPaymentRepository.findByPurchaseOrderIdOrderByPaymentDateDesc(purchaseOrderId)
                .stream().map(SupplierPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = invoiceTotal.subtract(alreadyPaid);

        if (request.amount().compareTo(outstanding) > 0) {
            throw new BusinessRuleException(
                    "Payment of " + request.amount() + " exceeds the outstanding balance of " + outstanding
                            + " on purchase order #" + po.getId());
        }

        SupplierPayment payment = SupplierPayment.builder()
                .supplier(po.getSupplier())
                .purchaseOrder(po)
                .amount(request.amount())
                .paymentDate(request.paymentDate() != null ? request.paymentDate() : LocalDate.now())
                .notes(request.notes())
                .build();

        SupplierPayment saved = supplierPaymentRepository.save(payment);
        journalService.postSupplierPaymentEntry(saved);
        return toDto(saved);
    }

    private SupplierPaymentDto toDto(SupplierPayment p) {
        return new SupplierPaymentDto(
                p.getId(),
                p.getSupplier().getId(), p.getSupplier().getName(),
                p.getPurchaseOrder() != null ? p.getPurchaseOrder().getId() : null,
                p.getAmount(), p.getPaymentDate(), p.getNotes()
        );
    }
}
