package com.kunirank.api.ai;

import com.kunirank.api.ai.controller.AiQuestionController;
import com.kunirank.api.deployment.ReadOnlyDeploymentFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class AiDeploymentBoundaryTest {
    @Test
    void readOnlyDeploymentAllowsOnlyTheExplicitAiQueryPost() throws Exception {
        var filter = new ReadOnlyDeploymentFilter();
        for (String path : new String[]{"/api/v1/member/login", "/api/v1/vs/vote",
                "/api/v1/universities", "/api/v1/ai/questions/extra"}) {
            var response = new MockHttpServletResponse();
            var invoked = new AtomicBoolean();
            filter.doFilter(new MockHttpServletRequest("POST", path), response,
                    (req, res) -> invoked.set(true));
            assertFalse(invoked.get(), path);
            assertEquals(503, response.getStatus());
        }
        var invoked = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/ai/questions"),
                new MockHttpServletResponse(), (req, res) -> invoked.set(true));
        assertTrue(invoked.get());
    }

    @Test
    void aiQueryRequiresFlagAndPrivateServiceTokenBeforeCallingParser() {
        String secret = "private-service-secret-at-least-thirty-two-characters";
        var disabled = new AiQuestionController(null, false, secret);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> disabled.ask(secret, null)).getStatusCode().value());
        var enabled = new AiQuestionController(null, true, secret);
        for (String credential : new String[]{null, "forged"}) {
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> enabled.ask(credential, null)).getStatusCode().value());
        }
    }
}
