package br.com.serviceemail.demo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Contas e tokens de redefinição de senha da página de teste, guardados só em memória
 * (somem ao reiniciar). Serve para exercitar o envio de email, não é um sistema de
 * autenticação de verdade.
 */
@ApplicationScoped
public class DemoAccountService {

    /** Token em texto puro: existe só aqui, para ir no email. Internamente guardamos apenas o hash. */
    public record IssuedToken(String name, String email, String token) {
    }

    private record ResetToken(String email, Instant expiresAt) {
    }

    private final ConcurrentMap<String, DemoAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResetToken> resetTokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile String dummyHash;

    @Inject
    PasswordHasher hasher;

    /** Vazio se já existe conta com esse email. */
    public Optional<DemoAccount> register(String name, String email, String password) {
        String key = normalize(email);
        DemoAccount account = new DemoAccount(name.trim(), key, hasher.hash(password));
        return accounts.putIfAbsent(key, account) == null ? Optional.of(account) : Optional.empty();
    }

    public Optional<DemoAccount> authenticate(String email, String password) {
        DemoAccount account = accounts.get(normalize(email));
        if (account == null) {
            // Gasta o mesmo tempo de uma conta existente, para não revelar quais emails têm conta.
            hasher.verify(password, dummyHash());
            return Optional.empty();
        }
        return hasher.verify(password, account.passwordHash()) ? Optional.of(account) : Optional.empty();
    }

    /** Gera um token de uso único. Vazio se não há conta (quem chama não deve revelar isso). */
    public Optional<IssuedToken> issueResetToken(String email, Duration ttl) {
        String key = normalize(email);
        DemoAccount account = accounts.get(key);
        if (account == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        // Só o token mais recente de cada conta vale; de passagem, limpa os vencidos.
        resetTokens.values().removeIf(t -> t.expiresAt().isBefore(now) || t.email().equals(key));

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        resetTokens.put(sha256(token), new ResetToken(key, now.plus(ttl)));
        return Optional.of(new IssuedToken(account.name(), key, token));
    }

    /** Troca a senha se o token for válido. O token é consumido mesmo que esteja vencido. */
    public boolean resetPassword(String token, String newPassword) {
        ResetToken found = resetTokens.remove(sha256(token));
        if (found == null || found.expiresAt().isBefore(Instant.now())) {
            return false;
        }
        resetTokens.values().removeIf(t -> t.email().equals(found.email()));
        return accounts.computeIfPresent(found.email(),
                (k, a) -> new DemoAccount(a.name(), a.email(), hasher.hash(newPassword))) != null;
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = hasher.hash("senha-descartavel");
            dummyHash = h;
        }
        return h;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
