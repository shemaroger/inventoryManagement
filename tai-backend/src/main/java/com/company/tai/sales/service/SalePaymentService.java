package com.company.tai.sales.service;

import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.sales.dto.SalePaymentDto;
import com.company.tai.sales.dto.SalePaymentRequest;
import com.company.tai.sales.entity.Customer;
import com.company.tai.sales.entity.PaymentType;
import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SalePayment;
import com.company.tai.sales.entity.SaleStatus;
import com.company.tai.sales.repository.SalePaymentRepository;
import com.company.tai.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalePaymentService {

    private final SalePaymentRepository salePaymentRepository;
    private final SaleRepository saleRepository;
    private final JournalService journalService;
    private final SaleService saleService;

    public List<SalePaymentDto> listForSale(Long saleId) {
        return salePaymentRepository.findBySaleIdOrderByPaymentDateDesc(saleId)
                .stream().map(this::toDto).toList();
    }

    // Keeps the balance-owed arithmetic in exactly one other place besides
    // SaleService.complete() (which increases it) — a payment always decreases it, regardless of
    // how much is still owed, matching the same "just record it" style as SupplierPaymentService.
    @Transactional
    public SalePaymentDto recordForSale(Long saleId, SalePaymentRequest request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + saleId));

        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Cannot record a payment against a sale with status " + sale.getStatus()
                            + " — only a COMPLETED sale has a balance owed");
        }

        BigDecimal alreadyPaid = salePaymentRepository.findBySaleIdOrderByPaymentDateDesc(saleId)
                .stream().map(SalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceOwed = saleService.grandTotal(sale).subtract(alreadyPaid);
        if (request.amount().compareTo(balanceOwed) > 0) {
            throw new BusinessRuleException(
                    "Payment amount " + request.amount() + " exceeds the remaining balance owed of "
                            + balanceOwed + " on Sale #" + saleId);
        }

        SalePayment payment = SalePayment.builder()
                .sale(sale)
                .amount(request.amount())
                .paymentDate(request.paymentDate() != null ? request.paymentDate() : LocalDate.now())
                .paymentMethod(request.paymentMethod())
                .notes(request.notes())
                .build();

        if (sale.getPaymentType() == PaymentType.CREDIT) {
            Customer customer = sale.getCustomer();
            customer.setCurrentBalance(customer.getCurrentBalance().subtract(request.amount()));
        }

        SalePayment saved = salePaymentRepository.save(payment);
        journalService.postSalePaymentEntry(saved);
        return toDto(saved);
    }

    private SalePaymentDto toDto(SalePayment p) {
        return new SalePaymentDto(
                p.getId(), p.getSale().getId(), p.getAmount(), p.getPaymentDate(), p.getPaymentMethod(), p.getNotes()
        );
    }
}
