package br.com.serviceemail.demo;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Hash de senha com PBKDF2-HMAC-SHA256 (só JDK, sem dependência extra).
 * Formato guardado: {@code pbkdf2$iterações$salt$hash}, com salt e hash em Base64.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    @ConfigProperty(name = "demo.pbkdf2-iterations", defaultValue = "600000")
    int iterations;

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, iterations, KEY_BITS);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(key);
    }

    public boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int rounds = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, rounds, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int rounds, int bits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, bits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível", e);
        } finally {
            spec.clearPassword();
        }
    }
}
