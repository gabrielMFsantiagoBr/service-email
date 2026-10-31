package br.com.serviceemail.service;

public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
