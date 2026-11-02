package com.kunirank.api.deployment;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Profile("railway-voting") @Order(Ordered.HIGHEST_PRECEDENCE)
public class VotingDeploymentFilter extends OncePerRequestFilter {
    private final String token;
    private final Set<String> origins;
    private final Map<String,Window> limits=new ConcurrentHashMap<>();
    private record Window(long minute,int count) {}
    public VotingDeploymentFilter(@Value("${VS_FRONTEND_TOKEN:}") String token,@Value("${VS_ALLOWED_ORIGIN:}") String origins) {
        this.token=token.strip();this.origins=new HashSet<>(Arrays.asList(origins.split(",")));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI();String method=request.getMethod();
        if (path.equals("/actuator/health") && Set.of("GET","HEAD").contains(method)) {chain.doFilter(request,response);return;}
        if (com.kunirank.api.config.DocumentationRoutes.isRead(method,path)) {chain.doFilter(request,response);return;}
        boolean route=(path.equals("/api/v1/boards/overall/next") && method.equals("GET")) || ((path.equals("/api/v1/boards/overall/vote") || path.equals("/api/v1/boards/overall/result")) && method.equals("POST"));
        if (!route) {reject(response,503,"VOTING_ONLY_SERVICE");return;}
        String supplied=request.getHeader("X-VS-Frontend-Token");
        if (token.length()<32 || supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {reject(response,403,"INVALID_FRONTEND");return;}
        if (method.equals("POST") && (!origins.contains(request.getHeader("Origin")) || request.getContentType()==null || !request.getContentType().startsWith("application/json") || request.getContentLengthLong()>2048)) {reject(response,403,"INVALID_VOTE_REQUEST");return;}
        String key=request.getHeader("X-VS-Client-Key");
        if (key==null || !key.matches("[a-f0-9]{64}")) {reject(response,403,"INVALID_CLIENT");return;}
        long minute=System.currentTimeMillis()/60000;
        if(limits.size()>10000) limits.entrySet().removeIf(e->e.getValue().minute()<minute);
        Window window=limits.compute(key,(k,w)->w==null || w.minute()!=minute ? new Window(minute,1) : new Window(minute,w.count()+1));
        if(window.count()>120) {response.setHeader("Retry-After","60");reject(response,429,"RATE_LIMITED");return;}
        String network=request.getHeader("X-VS-Network-Key");
        if(network!=null && network.matches("[a-f0-9]{64}")) {
            Window networkWindow=limits.compute("network:"+network,(k,w)->w==null || w.minute()!=minute ? new Window(minute,1) : new Window(minute,w.count()+1));
            if(networkWindow.count()>1200) {response.setHeader("Retry-After","60");reject(response,429,"RATE_LIMITED");return;}
        }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\"}");
    }
}
