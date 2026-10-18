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

    public MemberController(MemberSessionService sessions,
                            @Value("${MEMBER_COOKIE_SECURE:true}") boolean secure) {
        this.sessions = sessions;
        this.secure = secure;
    }

    public record Credentials(@NotBlank @Size(max = 150) String username,
                              @NotBlank String password) {}

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

    @GetMapping("/me")
    public MemberAccount.PublicProfile me(
            @CookieValue(name = "kunirank_member", required = false) String session) {
        return sessions.requireMember(session).publicProfile();
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        sessions.requireEnabled();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }
}
