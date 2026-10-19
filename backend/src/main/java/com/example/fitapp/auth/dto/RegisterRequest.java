package com.example.fitapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
                @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
                String password,
        @NotBlank
                @Size(min = 3, max = 20)
                @Pattern(
                        regexp = "^[A-Za-z0-9_]+$",
                        message = "may contain only letters, digits and underscores")
                String username,
        @NotBlank @Size(max = 80) String name) {}
