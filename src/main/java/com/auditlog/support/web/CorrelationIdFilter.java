package com.auditlog.support.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a correlation identifier to responses and structured logs without recording request bodies.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_KEY = "traceId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(CORRELATION_ID_HEADER));
        long startedAtNanos = System.nanoTime();
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(CORRELATION_ID_HEADER, traceId);
        try {
            LOGGER.info("HTTP request started method={} path={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
            LOGGER.info("HTTP request completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMillis);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String resolveTraceId(String suppliedTraceId) {
        if (suppliedTraceId != null && !suppliedTraceId.isBlank() && suppliedTraceId.length() <= MAX_CORRELATION_ID_LENGTH) {
            return suppliedTraceId;
        }
        return UUID.randomUUID().toString();
    }
}
