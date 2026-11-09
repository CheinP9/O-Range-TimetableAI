package com.orange.mail.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class SecurityFilter extends OncePerRequestFilter {

    @Value("${internal.api.key:SieuCapVipPro_2026}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Bỏ qua preflight request hoặc endpoint health check
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/".equals(path) || "/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Kiểm tra header X-Internal-Key cho các endpoint /api/**
        if (path.startsWith("/api/")) {
            String clientKey = request.getHeader("X-Internal-Key");
            if (clientKey == null || !clientKey.equals(expectedApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized: Invalid or missing X-Internal-Key\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

