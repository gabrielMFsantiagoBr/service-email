package br.com.serviceemail.api.dto;

/** {@code status = "accepted"}: o servidor SMTP aceitou a mensagem (entrega final não é garantida). */
public record EmailResponse(String status) {
}
