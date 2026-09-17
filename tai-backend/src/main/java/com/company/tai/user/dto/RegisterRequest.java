package com.company.tai.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String phoneNumber,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        String roleName
) {}
