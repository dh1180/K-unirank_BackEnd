package com.kunirank.api.member.service;

import com.kunirank.api.member.security.DjangoPasswordVerifier;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    public record Input(@NotBlank @Size(max=150) @Pattern(regexp="^[\\p{L}\\p{N}_@.+-]+$") String username,
                        @Size(max=254) @Email String email,
                        @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY, format = "password")
                        @NotBlank @Size(min=8,max=256) String password,
                        @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY, format = "password")
                        @NotBlank @Size(max=256) String passwordConfirm) {}
    private static final Set<String> COMMON = commonPasswords();
    private static Set<String> commonPasswords() {
        try (var stream=RegistrationService.class.getResourceAsStream("/common-passwords.txt.gz")) {
            if(stream==null)throw new IllegalStateException("Common password resource missing");
            try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(stream),java.nio.charset.StandardCharsets.UTF_8))) {
                return Set.copyOf(reader.lines().toList());
            }
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private final JdbcTemplate jdbc;
    private final DjangoPasswordVerifier passwords;
    public RegistrationService(JdbcTemplate jdbc, DjangoPasswordVerifier passwords) {this.jdbc=jdbc;this.passwords=passwords;}
    public void create(Input input) {
        if (!input.password().equals(input.passwordConfirm())) fail("비밀번호 확인이 일치하지 않습니다.");
        String lower=input.password().toLowerCase(Locale.ROOT);
        if (lower.matches("[0-9]+") || (input.username().length()>=3 && lower.contains(input.username().toLowerCase(Locale.ROOT)))
                || COMMON.contains(lower))
            fail("다른 곳에서 자주 쓰는 비밀번호나 아이디와 비슷한 비밀번호는 사용할 수 없습니다.");
        char[] chars=input.password().toCharArray();
        try {
            String hash=passwords.encode(chars);
            jdbc.queryForObject("SELECT kunirank_register_member(?,?,?)", Long.class,
                    input.username(),hash,input.email()==null?"":input.email());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 사용 중인 아이디입니다.");
        } finally {Arrays.fill(chars,'\0');}
    }
    private void fail(String detail) {throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,detail);}
}
