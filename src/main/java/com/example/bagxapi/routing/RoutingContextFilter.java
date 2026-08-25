package com.example.bagxapi.routing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Captures the routing context from the inbound request so outbound calls can replay it.
 *
 * <p>Values are read from the routing cookies first and from their header form second, so the
 * chain works whether the caller was a browser or another service that only had headers.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RoutingContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Map<String, String> cookies = new LinkedHashMap<>();
        Cookie[] inbound = request.getCookies();
        if (inbound != null) {
            for (Cookie cookie : inbound) {
                if (RoutingContext.ROUTING_COOKIES.contains(cookie.getName()) && hasText(cookie.getValue())) {
                    cookies.put(cookie.getName(), cookie.getValue());
                }
            }
        }
        for (String name : RoutingContext.ROUTING_COOKIES) {
            String headerValue = request.getHeader(RoutingContext.HEADER_FOR_COOKIE.get(name));
            if (!cookies.containsKey(name) && hasText(headerValue)) {
                cookies.put(name, headerValue.trim());
            }
        }

        Map<String, String> traceHeaders = new LinkedHashMap<>();
        for (String name : RoutingContext.TRACE_HEADERS) {
            String headerValue = request.getHeader(name);
            if (hasText(headerValue)) {
                traceHeaders.put(name, headerValue);
            }
        }

        RoutingContextHolder.set(new RoutingContext(cookies, traceHeaders));
        try {
            chain.doFilter(request, response);
        } finally {
            RoutingContextHolder.clear();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
