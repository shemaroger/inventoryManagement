package com.company.tai.company.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Singleton row (fixed id=1, seeded by V18 migration) — there is exactly one business profile
// for this system, so this is intentionally not a normal CRUD-able collection.
@Entity
@Table(name = "company_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettings {

    @Id
    private Long id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "tin_number")
    private String tinNumber;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "address_line")
    private String addressLine;

    private String city;

    private String country;

    private String phone;

    private String email;

    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
