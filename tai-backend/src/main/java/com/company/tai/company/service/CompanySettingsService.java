package com.company.tai.company.service;

import com.company.tai.company.dto.CompanySettingsDto;
import com.company.tai.company.dto.UpdateCompanySettingsRequest;
import com.company.tai.company.entity.CompanySettings;
import com.company.tai.company.repository.CompanySettingsRepository;
import com.company.tai.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private static final long SINGLETON_ID = 1L;

    private final CompanySettingsRepository companySettingsRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto get() {
        return toDto(load());
    }

    @Transactional
    public CompanySettingsDto update(UpdateCompanySettingsRequest request) {
        CompanySettings settings = load();
        settings.setLegalName(request.legalName());
        settings.setTradingName(request.tradingName());
        settings.setTinNumber(request.tinNumber());
        settings.setRegistrationNumber(request.registrationNumber());
        settings.setAddressLine(request.addressLine());
        settings.setCity(request.city());
        settings.setCountry(request.country());
        settings.setPhone(request.phone());
        settings.setEmail(request.email());
        settings.setWebsite(request.website());
        settings.setLogoUrl(request.logoUrl());
        return toDto(companySettingsRepository.save(settings));
    }

    private CompanySettings load() {
        return companySettingsRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Company settings have not been initialized"));
    }

    private CompanySettingsDto toDto(CompanySettings s) {
        return new CompanySettingsDto(
                s.getLegalName(), s.getTradingName(), s.getTinNumber(), s.getRegistrationNumber(),
                s.getAddressLine(), s.getCity(), s.getCountry(), s.getPhone(), s.getEmail(),
                s.getWebsite(), s.getLogoUrl(), s.getUpdatedAt()
        );
    }
}
