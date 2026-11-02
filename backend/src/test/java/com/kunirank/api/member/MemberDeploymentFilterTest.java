package com.kunirank.api.member;

import com.kunirank.api.deployment.MemberDeploymentFilter;
import com.kunirank.api.member.security.MemberSecurityStore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberDeploymentFilterTest {
    @Test void restrictsServiceTrustOriginRoutesAndSharedNetworkAttempts() throws Exception {
        String token="private-member-service-secret-longer-than-thirty-two-characters";
        var store=mock(MemberSecurityStore.class);
        var filter=new MemberDeploymentFilter(token,"https://frontend.test",store);
        var request=new MockHttpServletRequest("POST","/api/v1/member/login");
        request.setContentType("application/json");
        var response=new MockHttpServletResponse();
        var called=new AtomicBoolean();
        filter.doFilter(request,response,(a,b)->called.set(true));
        assertEquals(403,response.getStatus());assertFalse(called.get());
        request=new MockHttpServletRequest("POST","/api/v1/member/login");
        request.setContentType("application/json");
        request.addHeader("X-Member-Frontend-Token",token);
        request.addHeader("Origin","https://attacker.test");
        response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->called.set(true));
        assertEquals(403,response.getStatus());assertFalse(called.get());
        request=new MockHttpServletRequest("POST","/api/v1/member/login");
        request.setContentType("application/json");
        request.addHeader("X-Member-Frontend-Token",token);
        request.addHeader("Origin","https://frontend.test");
        request.addHeader("X-Member-Network-Key","a".repeat(64));
        response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->called.set(true));
        assertEquals(429,response.getStatus());assertFalse(called.get());
        when(store.attempt(anyString(),eq(30),eq(600))).thenReturn(true);
        response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->called.set(true));
        assertTrue(called.get());
        called.set(false);
        response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/v1/boards/overall/vote"),response,(a,b)->called.set(true));
        assertEquals(404,response.getStatus());assertFalse(called.get());
    }
}
