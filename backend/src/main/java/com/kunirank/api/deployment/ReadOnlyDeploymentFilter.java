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
        boolean queryRoute = path.matches("/api/v1/universities(?:/[0-9]+)?/?")
                || path.matches("/api/v1/universities/[0-9]+/overview/?")
                || path.matches("/api/v1/seo/urls/?")
                || path.matches("/api/v1/admissions/(?:results|compare|universities/[0-9]+|units/[0-9]+)/?")
                || path.matches("/api/v1/boards/[^/]+/ranking/?")
                || path.matches("/api/v1/rankings/(?:catalog|admissions|indicators/[a-z-]+)/?")
                || path.equals("/actuator/health");
        if (READ_METHODS.contains(request.getMethod()) && queryRoute) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"READ_ONLY_DEPLOYMENT\",\"message\":\"Only verified read APIs are enabled\"}");
    }
}
