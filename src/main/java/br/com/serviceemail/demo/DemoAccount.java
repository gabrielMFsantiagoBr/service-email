package br.com.serviceemail.demo;

/** Conta da demonstração. Fica só na memória; {@code email} já vem normalizado (minúsculas). */
public record DemoAccount(String name, String email, String passwordHash) {
}
