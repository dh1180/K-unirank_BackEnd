package com.kunirank.api.member;

import com.kunirank.api.member.security.DjangoPasswordVerifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DjangoPasswordVerifierTest {
    private final DjangoPasswordVerifier verifier = new DjangoPasswordVerifier();

    // Independently generated with Python's hashlib.pbkdf2_hmac:
    // password: 테스트Password!2026, salt: DjangoSaltSampleX9, rounds: 120000.
    private static final String SHA256 =
            "pbkdf2_sha256$120000$DjangoSaltSampleX9$Si6mgnD8qGfnr783SJ0iG5wEEv2k8jDRydqqkDJKmRM=";
    private static final String SHA1 =
            "pbkdf2_sha1$120000$DjangoSaltSampleX9$Lk4xykQsGtR6LbdqnTXtuRu8sBk=";

    @Test
    void matchesDjangoSha256HashWithKoreanUnicodePassword() {
        assertTrue(verifier.matches("테스트Password!2026".toCharArray(), SHA256));
        assertFalse(verifier.matches("테스트Password!2025".toCharArray(), SHA256));
        assertFalse(verifier.requiresPasswordMigration(SHA256));
    }

    @Test
    void canReadLegacyDjangoSha1HashButMarksForMigration() {
        assertTrue(verifier.matches("테스트Password!2026".toCharArray(), SHA1));
        assertTrue(verifier.requiresPasswordMigration(SHA1));
    }

    @Test
    void rejectsUnsupportedUnusableAndMalformedHashesWithoutThrowing() {
        String[] invalid = {
                "!bGfmsome-unusable-password", "argon2$argon2id$v=19$m=102400", "",
                "pbkdf2_sha256$99999999$salt$abc", "pbkdf2_sha256$-10$salt$abc",
                "pbkdf2_sha256$120000$salt$@@@",
                "pbkdf2_sha256$120000$salt$Lk4xykQsGtR6LbdqnTXtuRu8sBk=",
                "pbkdf2_sha1$120000$salt$not-valid-base64***"
        };
        for (String hash : invalid) {
            assertFalse(verifier.matches("test".toCharArray(), hash), hash);
        }
        assertFalse(verifier.matches(null, SHA256));
        assertFalse(verifier.matches("test".toCharArray(), null));
    }
}
