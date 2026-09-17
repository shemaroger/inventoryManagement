package com.company.tai.sales.service;

import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.sales.dto.CustomerDto;
import com.company.tai.sales.dto.CustomerRequest;
import com.company.tai.sales.dto.CustomerStatementEntryDto;
import com.company.tai.sales.entity.Customer;
import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SalePayment;
import com.company.tai.sales.entity.SaleStatus;
import com.company.tai.sales.repository.CustomerRepository;
import com.company.tai.sales.repository.SalePaymentRepository;
import com.company.tai.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Mirrors SupplierService's simple-entity CRUD pattern, plus the statement endpoint that
// Suppliers doesn't have.
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SalePaymentRepository salePaymentRepository;

    public List<CustomerDto> listAll() {
        return customerRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public CustomerDto create(CustomerRequest request) {
        Customer customer = Customer.builder()
                .name(request.name())
                .contactPerson(request.contactPerson())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .creditLimit(request.creditLimit())
                .currentBalance(BigDecimal.ZERO)
                .customerCategory(request.customerCategory())
                .active(true)
                .build();
        return toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequest request) {
        Customer customer = findOrThrow(id);
        customer.setName(request.name());
        customer.setContactPerson(request.contactPerson());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAddress(request.address());
        customer.setCreditLimit(request.creditLimit());
        customer.setCustomerCategory(request.customerCategory());
        return toDto(customer);
    }

    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.delete(findOrThrow(id));
    }

    // Merges completed credit sales (debits — increase what the customer owes) and payments
    // (credits — reduce it) into one chronological list with a running balance, matching how
    // Customer.currentBalance is actually kept up to date in SaleService/this same arithmetic
    // applied incrementally instead of recomputed here.
    @Transactional(readOnly = true)
    public List<CustomerStatementEntryDto> getStatement(Long customerId) {
        findOrThrow(customerId);

        List<Sale> sales = saleRepository.findByCustomerIdOrderBySaleDateAsc(customerId);
        List<Long> saleIds = sales.stream().map(Sale::getId).toList();
        List<SalePayment> payments = saleIds.isEmpty()
                ? List.of()
                : salePaymentRepository.findBySaleIdInOrderByPaymentDateAsc(saleIds);
        Map<Long, Sale> saleById = sales.stream().collect(Collectors.toMap(Sale::getId, s -> s));

        record RawEntry(java.time.LocalDate date, String type, String description, BigDecimal debit, BigDecimal credit) {}

        List<RawEntry> raw = new ArrayList<>();
        for (Sale sale : sales) {
            if (sale.getStatus() == SaleStatus.COMPLETED && sale.getPaymentType() == com.company.tai.sales.entity.PaymentType.CREDIT) {
                // VAT-inclusive — must match exactly what SaleService.complete() added to
                // Customer.currentBalance, or this statement won't reconcile to the real balance.
                BigDecimal subtotal = saleTotal(sale);
                BigDecimal total = subtotal.add(VatConstants.vatOn(subtotal));
                raw.add(new RawEntry(sale.getSaleDate(), "SALE", "Sale #" + sale.getId(), total, BigDecimal.ZERO));
            }
        }
        for (SalePayment payment : payments) {
            Sale sale = saleById.get(payment.getSale().getId());
            String label = "Payment on Sale #" + (sale != null ? sale.getId() : payment.getSale().getId());
            raw.add(new RawEntry(payment.getPaymentDate(), "PAYMENT", label, BigDecimal.ZERO, payment.getAmount()));
        }
        raw.sort(Comparator.comparing(RawEntry::date));

        List<CustomerStatementEntryDto> statement = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (RawEntry entry : raw) {
            running = running.add(entry.debit()).subtract(entry.credit());
            statement.add(new CustomerStatementEntryDto(entry.date(), entry.type(), entry.description(), entry.debit(), entry.credit(), running));
        }
        return statement;
    }

    private BigDecimal saleTotal(Sale sale) {
        return sale.getLines().stream()
                .map(line -> line.getUnitPrice()
                        .multiply(line.getQuantity())
                        .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))))
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(
                c.getId(), c.getName(), c.getContactPerson(), c.getPhone(), c.getEmail(), c.getAddress(),
                c.getCreditLimit(), c.getCurrentBalance(), c.getCustomerCategory(), c.isActive()
        );
    }
}
