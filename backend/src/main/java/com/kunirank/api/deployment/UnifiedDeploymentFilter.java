package com.kunirank.api.deployment;

import com.kunirank.api.member.security.MemberSecurityStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("railway-unified")
@Order(Ordered.HIGHEST_PRECEDENCE)
class UnifiedDeploymentFilter extends OncePerRequestFilter {
    private static final Set<String> VOTING_PATHS = Set.of(
            "/api/v1/boards/overall/next",
            "/api/v1/boards/overall/vote",
            "/api/v1/boards/overall/result");

    private final ReadOnlyDeploymentFilter query = new ReadOnlyDeploymentFilter();
    private final MemberDeploymentFilter member;
    private final VotingDeploymentFilter voting;

    UnifiedDeploymentFilter(
            @Value("${MEMBER_FRONTEND_TOKEN}") String memberToken,
            @Value("${MEMBER_ALLOWED_ORIGINS}") String memberOrigins,
            @Value("${VS_FRONTEND_TOKEN}") String votingToken,
            @Value("${VS_ALLOWED_ORIGIN}") String votingOrigins,
            MemberSecurityStore store) {
        member = new MemberDeploymentFilter(memberToken, memberOrigins, store);
        voting = new VotingDeploymentFilter(votingToken, votingOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (path.startsWith("/api/v1/member/") || path.startsWith("/api/v1/admin/")) {
                DeploymentDataSource.select(DeploymentDataSource.Role.MEMBER);
                member.doFilterInternal(request, response, chain);
            } else if (VOTING_PATHS.contains(path)) {
                DeploymentDataSource.select(DeploymentDataSource.Role.VOTING);
                voting.doFilterInternal(request, response, chain);
            } else {
                query.doFilterInternal(request, response, chain);
            }
        } finally {
            // Tomcat reuses request threads. Never carry a write role into the next request.
            DeploymentDataSource.clear();
        }
    }
}
