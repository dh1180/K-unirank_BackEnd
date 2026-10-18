package com.kunirank.api.ai.controller;

import com.kunirank.api.ai.dto.AiQuestion;
import com.kunirank.api.ai.service.AiQuestionService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiQuestionController {
    private final AiQuestionService ai;
    private final boolean enabled;
    private final String frontendToken;

    public AiQuestionController(AiQuestionService ai,
          @Value("${AI_GATEWAY_ENABLED:false}") boolean enabled,
          @Value("${AI_FRONTEND_TOKEN:}") String frontendToken) {
        this.ai = ai;
        this.enabled = enabled;
        this.frontendToken = frontendToken;
    }

    @PostMapping("/questions")
    public AiQuestion.Response ask(
            @RequestHeader(name = "X-Frontend-Service-Token", required = false) String token,
            @Valid @RequestBody AiQuestion.Request request) {
        // Fail closed until a public-facing rate-limit and operation policies are configured.
        if (!enabled || frontendToken.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능은 준비 중입니다.");
        }
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                frontendToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증되지 않은 AI 요청입니다.");
        }
        return ai.ask(request);
    }
}
