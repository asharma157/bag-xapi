package com.example.bagxapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps the serving version onto every response, including /health and error responses.
 *
 * <p>This is what makes routing verifiable at a glance: whatever else happens, the response
 * carries the identity of the pod that produced it.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class VersionHeaderFilter extends OncePerRequestFilter {

    public static final String VERSION_HEADER = "x-bag-xapi-version";
    public static final String INSTANCE_HEADER = "x-bag-xapi-instance";

    private final String version;
    private final String instance;

    public VersionHeaderFilter(@Value("${bag.version}") String version,
                               @Value("${bag.instance}") String instance) {
        this.version = version;
        this.instance = instance;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(VERSION_HEADER, version);
        response.setHeader(INSTANCE_HEADER, instance);
        chain.doFilter(request, response);
    }
}
