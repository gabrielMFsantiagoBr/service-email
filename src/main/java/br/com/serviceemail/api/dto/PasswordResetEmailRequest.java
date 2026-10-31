package br.com.serviceemail.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O serviço NÃO gera o token de recuperação: o backend que chama monta o link
 * (com o token) e envia aqui só para entrega.
 */
public record PasswordResetEmailRequest(
        @NotBlank @Email @Size(max = 254) String to,
        @NotBlank @Size(max = 100) String name,

        @NotBlank
        @Size(max = 2000)
        @Pattern(regexp = "^https?://\\S+$", message = "deve ser uma URL http(s) sem espaços")
        String resetLink) {
}
