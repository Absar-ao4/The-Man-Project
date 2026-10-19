package com.example.fitapp.config;

import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.common.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP token buckets: login and registration, user search, invite code redeeming, deleting the account (which takes a
 * password, so guessing it is as strict as logging in) and the endpoints that cost money or do heavy work.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final ObjectMapper mapper;
    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

    public RateLimitFilter(RateLimitProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String kind;
        int limit;
        if (path.startsWith("/api/auth/")) {
            kind = "auth";
            limit = props.authPerMinute();
        } else if (path.equals("/api/users/search")) {
            kind = "search";
            limit = props.searchPerMinute();
        } else if (path.startsWith("/api/invite-codes/") && path.endsWith("/redeem")) {
            // Guessing invite codes is the only way to abuse them, so redeeming is as strict as logging in.
            kind = "redeem";
            limit = props.authPerMinute();
        } else if (path.equals("/api/me") && "DELETE".equals(request.getMethod())) {
            // Deleting the account takes the password again, so guessing it is the same attack as guessing a login.
            kind = "auth";
            limit = props.authPerMinute();
        } else if (isExpensive(path, request.getMethod())) {
            kind = "expensive";
            limit = props.expensivePerMinute();
        } else {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket =
                buckets.get(kind + ":" + request.getRemoteAddr(), k -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(ErrorCode.LIMIT_REACHED.status().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(
                response.getOutputStream(),
                ProblemDetails.of(ErrorCode.LIMIT_REACHED, "Too many requests, try again later"));
    }

    /** The AI calls, building a diet plan and the data export: each costs money or a lot of work. */
    static boolean isExpensive(String path, String method) {
        return path.startsWith("/api/ai/")
                || path.equals("/api/nutrition/photo-scan")
                || path.equals("/api/plans/generate/explain")
                || path.equals("/api/me/export")
                || (path.equals("/api/diet/plans") && "POST".equals(method));
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(
                        Bandwidth.builder()
                                .capacity(perMinute)
                                .refillIntervally(perMinute, Duration.ofMinutes(1))
                                .build())
                .build();
    }
}
