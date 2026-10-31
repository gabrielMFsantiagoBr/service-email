package br.com.serviceemail.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O {@code title} vira o assunto do email e o {@code message} o corpo. */
public record NotificationEmailRequest(
        @NotBlank @Email @Size(max = 254) String to,

        @NotBlank
        @Size(max = 200)
        @Pattern(regexp = "^[^\\r\\n]*$", message = "não pode conter quebras de linha")
        String title,

        @NotBlank @Size(max = 5000) String message) {
}
