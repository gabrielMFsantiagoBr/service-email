package br.com.serviceemail.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Envio genérico: corpo em texto e, opcionalmente, em HTML. */
public record SendEmailRequest(
        @NotBlank @Email @Size(max = 254) String to,

        @NotBlank
        @Size(max = 200)
        @Pattern(regexp = "^[^\\r\\n]*$", message = "não pode conter quebras de linha")
        String subject,

        @NotBlank @Size(max = 100_000) String text,

        @Size(max = 200_000) String html) {
}
