package com.company.tai.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String phoneNumber,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String roleName
) {}
