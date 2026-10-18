package com.kunirank.api.member.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verify Django's standard PBKDF2 password hashes without changing auth_user.
 *
 * Django format: algorithm$iterations$salt$base64encodedDigest.
 * Unsupported algorithms (argon2, bcrypt, scrypt, unusable "!" passwords) fail
 * closed and will require an explicit verified password-migration path.
 */
@Component
public class DjangoPasswordVerifier {
    private static final int MAX_ITERATIONS = 3_000_000;

    public boolean matches(char[] password, String encoded) {
        if (password == null || encoded == null || encoded.length() > 512) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4) return false;
        String algorithm = switch (parts[0]) {
            case "pbkdf2_sha256" -> "PBKDF2WithHmacSHA256";
            case "pbkdf2_sha1" -> "PBKDF2WithHmacSHA1";
            default -> null;
        };
        if (algorithm == null || !parts[1].matches("[0-9]{1,7}")
                || parts[2].isEmpty() || parts[2].length() > 128
                || parts[3].isEmpty() || parts[3].length() > 256) return false;

        try {
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < 1 || rounds > MAX_ITERATIONS) return false;
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (expected.length < 16 || expected.length > 64) return false;
            PBEKeySpec spec = new PBEKeySpec(password, parts[2].getBytes(StandardCharsets.UTF_8),
                    rounds, expected.length * 8);
            try {
                byte[] actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean requiresPasswordMigration(String encoded) {
        if (encoded == null) return true;
        // Any old hasher or unsupported algorithm needs an explicit transition.
        return !encoded.startsWith("pbkdf2_sha256$");
    }
}
