package com.kunirank.api.member.security;

import com.kunirank.api.member.model.MemberAccount;
import com.kunirank.api.member.repository.DjangoMemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Disabled by default. Stateless one-hour HMAC session, bound to Django's password hash. */
@Service
public class MemberSessionService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY_HASH = "pbkdf2_sha256$120000$DjangoSaltSampleX9$"
            + "Si6mgnD8qGfnr783SJ0iG5wEEv2k8jDRydqqkDJKmRM=";
    private final DjangoMemberRepository members;
    private final DjangoPasswordVerifier passwords;
    private final String secret;
    private final boolean enabled;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private record Window(long until, int count) {}

    public MemberSessionService(DjangoMemberRepository members, DjangoPasswordVerifier passwords,
                                @Value("${MEMBER_AUTH_SECRET:}") String secret,
                                @Value("${MEMBER_AUTH_ENABLED:false}") boolean enabled) {
        this.members = members;
        this.passwords = passwords;
        this.secret = secret;
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled || secret.length() < 32)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "새 회원 기능은 검증 중입니다.");
    }

    private boolean allowed(String username) {
        long now = Instant.now().getEpochSecond();
        // In-process defense-in-depth, not a substitute for edge rate limiting.
        if (attempts.size() > 10000) attempts.clear();
        Window window = attempts.compute(username.toLowerCase(Locale.ROOT), (key, old) ->
                old == null || old.until < now ? new Window(now + 600, 1)
                        : new Window(old.until, old.count + 1));
        return window.count <= 5;
    }

    public record Login(MemberAccount.PublicProfile profile, String token) {}

    public Login login(String username, String password) {
        requireEnabled();
        if (!allowed(username)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "잠시 후 다시 시도해 주세요.");
        Optional<MemberAccount> found = members.byUsername(username);
        char[] chars = password.toCharArray();
        boolean valid;
        try {
            valid = passwords.matches(chars, found.map(MemberAccount::passwordHash).orElse(DUMMY_HASH));
        } finally {
            Arrays.fill(chars, '\0');
        }
        if (!valid || found.isEmpty() || !found.get().active()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        MemberAccount member = found.get();
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String data = "v1:" + member.id() + ":" + expiry + ":"
                + passwordTag(member.passwordHash()) + ":" + ENCODER.encodeToString(nonce);
        String signed = ENCODER.encodeToString(data.getBytes(StandardCharsets.UTF_8))
                + "." + ENCODER.encodeToString(sign(data));
        return new Login(member.publicProfile(), signed);
    }

    public MemberAccount requireMember(String token) {
        requireEnabled();
        if (token == null || token.length() > 1024) unauthorized();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) unauthorized();
            byte[] payload = DECODER.decode(parts[0]);
            byte[] claimed = DECODER.decode(parts[1]);
            String data = new String(payload, StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(data), claimed)) unauthorized();
            String[] fields = data.split(":", -1);
            if (fields.length != 5 || !"v1".equals(fields[0])) unauthorized();
            long id = Long.parseLong(fields[1]);
            long expires = Long.parseLong(fields[2]);
            if (id <= 0 || expires <= Instant.now().getEpochSecond()
                    || expires > Instant.now().plusSeconds(3600).getEpochSecond()) unauthorized();
            MemberAccount member = members.byId(id).filter(MemberAccount::active)
                    .orElseThrow(this::unauthorizedException);
            if (!MessageDigest.isEqual(fields[3].getBytes(StandardCharsets.US_ASCII),
                    passwordTag(member.passwordHash()).getBytes(StandardCharsets.US_ASCII))) unauthorized();
            return member;
        } catch (IllegalArgumentException ex) {
            unauthorized();
            throw ex;
        }
    }

    private String passwordTag(String hash) {
        try {
            return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(hash.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    private ResponseStatusException unauthorizedException() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    private void unauthorized() { throw unauthorizedException(); }
}
