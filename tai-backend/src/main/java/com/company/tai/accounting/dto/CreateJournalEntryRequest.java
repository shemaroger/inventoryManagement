package com.company.tai.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateJournalEntryRequest(
        @NotNull LocalDate entryDate,
        @NotBlank String description,
        String reference,
        @NotEmpty(message = "A journal entry needs at least two lines")
        @Size(min = 2, message = "A journal entry needs at least two lines")
        @Valid List<JournalEntryLineRequest> lines
) {}
