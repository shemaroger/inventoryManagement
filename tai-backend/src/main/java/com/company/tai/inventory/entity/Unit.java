package com.company.tai.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "units")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String abbreviation;

    @ManyToOne
    @JoinColumn(name = "base_unit_id")
    private Unit baseUnit;

    @Column(name = "conversion_factor")
    @Builder.Default
    private BigDecimal conversionFactor = BigDecimal.ONE;
}
