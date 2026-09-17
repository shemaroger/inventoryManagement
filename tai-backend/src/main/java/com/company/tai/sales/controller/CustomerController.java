package com.company.tai.sales.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.sales.dto.CustomerDto;
import com.company.tai.sales.dto.CustomerRequest;
import com.company.tai.sales.dto.CustomerStatementEntryDto;
import com.company.tai.sales.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ApiResponse<List<CustomerDto>> listAll() {
        return ApiResponse.ok(customerService.listAll());
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<List<CustomerStatementEntryDto>> statement(@PathVariable Long id) {
        return ApiResponse.ok(customerService.getStatement(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<CustomerDto> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok("Customer created", customerService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<CustomerDto> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok("Customer updated", customerService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
        return ApiResponse.ok("Customer deactivated", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.ok("Customer deleted", null);
    }
}
