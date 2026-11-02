package com.kunirank.api.member.controller;

import com.kunirank.api.member.model.MemberAccount;
import com.kunirank.api.member.security.MemberSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member")
public class MemberController {
    private final MemberSessionService sessions;
    private final boolean secure;
    private final com.kunirank.api.member.service.RegistrationService registration;

    public MemberController(MemberSessionService sessions, com.kunirank.api.member.service.RegistrationService registration,
                            @Value("${MEMBER_COOKIE_SECURE:true}") boolean secure) {
        this.sessions = sessions;
        this.registration = registration;
        this.secure = secure;
    }

    public record Credentials(@NotBlank @Size(max = 150) String username,
                              @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY, format = "password")
                              @NotBlank @Size(max = 256) String password) {}

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from("kunirank_member", value)
                .httpOnly(true).secure(secure).sameSite("Strict").path("/")
                .maxAge(age).build();
    }

    @PostMapping("/login")
    public MemberAccount.PublicProfile login(@Valid @RequestBody Credentials credentials,
                                             HttpServletResponse response) {
        var auth = sessions.login(credentials.username(), credentials.password());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(auth.token(), Duration.ofHours(1)).toString());
        return auth.profile();
    }

    @PostMapping("/register")
    public MemberAccount.PublicProfile register(@Valid @RequestBody com.kunirank.api.member.service.RegistrationService.Input input,
                                                HttpServletResponse response) {
        sessions.requireEnabled();
        registration.create(input);
        var auth = sessions.login(input.username(), input.password());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(auth.token(), Duration.ofHours(1)).toString());
        return auth.profile();
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> error(org.springframework.web.server.ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(
                java.util.Map.of("detail", ex.getReason()==null ? "요청을 처리하지 못했습니다." : ex.getReason()));
    }

    @GetMapping("/me")
    public MemberAccount.PublicProfile me(
            @CookieValue(name = "kunirank_member", required = false) String session) {
        return sessions.requireMember(session).publicProfile();
    }

    @PostMapping("/logout")
    public void logout(@CookieValue(name = "kunirank_member", required = false) String session,
                       HttpServletResponse response) {
        sessions.logout(session);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }
}
