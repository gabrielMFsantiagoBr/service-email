package br.com.serviceemail.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new PasswordHasher();
        hasher.iterations = 1000; // rápido para teste
    }

    @Test
    void verify_acceptsCorrectPassword() {
        String stored = hasher.hash("senha-forte-123");
        assertTrue(hasher.verify("senha-forte-123", stored));
    }

    @Test
    void verify_rejectsWrongPassword() {
        String stored = hasher.hash("senha-forte-123");
        assertFalse(hasher.verify("senha-forte-124", stored));
        assertFalse(hasher.verify("", stored));
    }

    @Test
    void hash_usesRandomSalt_soSamePasswordGivesDifferentHashes() {
        assertNotEquals(hasher.hash("mesma-senha-1"), hasher.hash("mesma-senha-1"));
    }

    @Test
    void hash_neverContainsThePlainPassword() {
        assertFalse(hasher.hash("senha-forte-123").contains("senha-forte-123"));
    }

    @Test
    void verify_rejectsMalformedStoredValues() {
        assertFalse(hasher.verify("x", ""));
        assertFalse(hasher.verify("x", "texto-qualquer"));
        assertFalse(hasher.verify("x", "pbkdf2$abc$AAAA$AAAA"));
        assertFalse(hasher.verify("x", "pbkdf2$1000$!!!$!!!"));
        assertFalse(hasher.verify("x", "bcrypt$1000$AAAA$AAAA"));
    }

    @Test
    void verify_usesIterationsStoredInTheHash() {
        String stored = hasher.hash("senha-forte-123");
        hasher.iterations = 2000; // mudar a configuração não invalida hashes antigos
        assertTrue(hasher.verify("senha-forte-123", stored));
    }
}
