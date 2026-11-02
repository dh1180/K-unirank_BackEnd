package com.kunirank.api.deployment;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only known, side-effect-free routes may reach the shared production database. */
@Component
@Profile("railway-readonly")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadOnlyDeploymentFilter extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (com.kunirank.api.config.DocumentationRoutes.isRead(request.getMethod(),path)) { chain.doFilter(request,response); return; }
        boolean queryRoute = path.matches("/api/v1/universities(?:/[0-9]+)?/?")
                || path.matches("/api/v1/universities/regions/?")
                || path.matches("/api/v1/universities/[0-9]+/overview/?")
                || path.matches("/api/v1/universities/[0-9]+/admission-reference/?")
                || path.matches("/api/v1/seo/urls/?")
                || path.matches("/api/v1/admissions/(?:results|compare|universities/[0-9]+|units/[0-9]+)/?")
                || path.matches("/api/v1/boards/[^/]+/ranking/?")
                || path.matches("/api/v1/rankings/(?:catalog|admissions|indicators/[a-z-]+)/?")
                || path.matches("/api/v1/results/[a-fA-F0-9-]{36}/?")
                || path.equals("/actuator/health");
        // AI accepts text by POST, but only parses filters and invokes the same read queries.
        // Its controller separately requires an operator flag and private BFF token.
        boolean aiQuery = "POST".equals(request.getMethod()) && path.equals("/api/v1/ai/questions");
        if (READ_METHODS.contains(request.getMethod()) && queryRoute || aiQuery) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"READ_ONLY_DEPLOYMENT\",\"message\":\"Only verified read APIs are enabled\"}");
    }
}
