package com.kunirank.api.deployment;

import com.kunirank.api.member.security.MemberSecurityStore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Profile("railway-member") @Order(Ordered.HIGHEST_PRECEDENCE)
public class MemberDeploymentFilter extends OncePerRequestFilter {
    private final String token;
    private final Set<String> origins;
    private final MemberSecurityStore store;
    public MemberDeploymentFilter(@Value("${MEMBER_FRONTEND_TOKEN:}") String token,
            @Value("${MEMBER_ALLOWED_ORIGINS:}") String origins, MemberSecurityStore store) {
        this.token=token; this.origins=new HashSet<>(Arrays.asList(origins.split(","))); this.store=store;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                             FilterChain chain) throws ServletException, IOException {
        String path=req.getRequestURI(), method=req.getMethod();
        if (path.equals("/actuator/health") && Set.of("GET","HEAD").contains(method)) {chain.doFilter(req,res);return;}
        if (com.kunirank.api.config.DocumentationRoutes.isRead(method,path)) {chain.doFilter(req,res);return;}
        boolean read="GET".equals(method) && (path.equals("/api/v1/member/me") || path.equals("/api/v1/member/history")
                || path.matches("/api/v1/member/favorites/(universities|units)")
                || path.equals("/api/v1/admin/overview"));
        boolean write=("POST".equals(method) && path.matches("/api/v1/member/(login|logout|register|link-vote-session)"))
                || (Set.of("PUT","DELETE").contains(method)
                    && path.matches("/api/v1/member/favorites/(universities|units)/[1-9][0-9]*"));
        if (!read && !write) {reject(res,404,"지원하지 않는 요청입니다.");return;}
        String supplied=req.getHeader("X-Member-Frontend-Token");
        if (token.length()<32 || supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            reject(res,403,"인증되지 않은 서비스입니다.");return;
        }
        if (write && !origins.contains(req.getHeader("Origin"))) {reject(res,403,"요청 출처가 올바르지 않습니다.");return;}
        if (path.endsWith("/login") || path.endsWith("/register")) {
            String network=req.getHeader("X-Member-Network-Key");
            if (req.getContentType()==null || !req.getContentType().startsWith("application/json")
                    || req.getContentLengthLong()>2048 || network==null || !network.matches("[a-f0-9]{64}")) {
                reject(res,400,"로그인 요청 형식이 잘못되었습니다.");return;
            }
            if (!store.attempt((path.endsWith("/register")?"signup:":"network:")+network,path.endsWith("/register")?5:30,path.endsWith("/register")?3600:600)) {res.setHeader("Retry-After","600");reject(res,429,"잠시 후 다시 로그인해 주세요.");return;}
        }
        chain.doFilter(req,res);
    }
    private void reject(HttpServletResponse res,int status,String detail) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"detail\":\""+detail+"\"}");
    }
}
