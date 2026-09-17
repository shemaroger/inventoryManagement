package com.company.tai.purchasing.service;

import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.purchasing.dto.SupplierPaymentDto;
import com.company.tai.purchasing.dto.SupplierPaymentRequest;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.SupplierPayment;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.purchasing.repository.SupplierPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
