package br.com.serviceemail.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WelcomeEmailRequest(
        @NotBlank @Email @Size(max = 254) String to,
        @NotBlank @Size(max = 100) String name) {
}
