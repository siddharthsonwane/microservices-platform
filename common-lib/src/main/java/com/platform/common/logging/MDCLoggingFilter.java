package com.platform.common.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MDCLoggingFilter implements Filter {

    private static final String TRACE_ID      = "traceId";
    private static final String SPAN_ID       = "spanId";
    private static final String REQUEST_ID    = "requestId";
    private static final String USER_ID       = "userId";
    private static final String SERVICE_NAME  = "serviceName";
    private static final String HTTP_METHOD   = "httpMethod";
    private static final String REQUEST_URI   = "requestUri";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String traceId   = getOrGenerate(request, "X-Trace-Id");
        String spanId    = getOrGenerate(request, "X-Span-Id");
        String requestId = getOrGenerate(request, "X-Request-Id");

        MDC.put(TRACE_ID,     traceId);
        MDC.put(SPAN_ID,      spanId);
        MDC.put(REQUEST_ID,   requestId);
        MDC.put(HTTP_METHOD,  request.getMethod());
        MDC.put(REQUEST_URI,  request.getRequestURI());

        response.setHeader("X-Trace-Id",   traceId);
        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();
        try {
            log.info("▶ {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("◀ {} {} → {} ({}ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), elapsed);
            MDC.clear();
        }
    }

    private String getOrGenerate(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
