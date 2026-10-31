package br.com.serviceemail.demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requisições e respostas da página de teste. */
public final class DemoDtos {

    private static final String NO_LINE_BREAKS = "^[^\\r\\n]*$";

    private DemoDtos() {
    }

    public record SignupRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = NO_LINE_BREAKS, message = "não pode conter quebras de linha")
            String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record SupportRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = NO_LINE_BREAKS, message = "não pode conter quebras de linha")
            String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 5000) String message) {
    }

    public record UserResponse(String name, String email) {
    }

    /** {@code welcomeEmail} é "sent" ou "failed": a conta é criada mesmo se o email falhar. */
    public record SignupResponse(String name, String email, String welcomeEmail) {
    }

    public record MessageResponse(String message) {
    }

    /** {@code mailMode} é "mock" (nada sai, só log) ou "smtp" (envio real). */
    public record InfoResponse(String mailMode, String smtpHost, String supportTo) {
    }
}
