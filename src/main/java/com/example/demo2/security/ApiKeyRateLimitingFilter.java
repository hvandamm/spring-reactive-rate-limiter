package com.example.demo2.security;

import com.example.demo2.service.TokenBucketRateLimiterService;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class ApiKeyRateLimitingFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Map<String, ApiKeyTier> API_KEY_REGISTRY = new ConcurrentHashMap<>();

    static {
        // Hardcoded API keys with their respective tier configurations
        API_KEY_REGISTRY.put("free-key-123", new ApiKeyTier("free", 10, 10.0 / 60.0));
        API_KEY_REGISTRY.put("premium-key-456", new ApiKeyTier("premium", 100, 100.0 / 60.0));
    }

    private final TokenBucketRateLimiterService rateLimiterService;

    public ApiKeyRateLimitingFilter(TokenBucketRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey == null || !API_KEY_REGISTRY.containsKey(apiKey)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"error\": \"Missing or invalid X-API-KEY header\"}".getBytes();
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }

        ApiKeyTier tier = API_KEY_REGISTRY.get(apiKey);

        return rateLimiterService.tryConsume(apiKey, tier.capacity(), tier.refillRate())
                .flatMap(allowed -> {
                    if (allowed) {
                        exchange.getResponse().getHeaders()
                                .add("X-RateLimit-Limit", String.valueOf(tier.capacity()));
                        return chain.filter(exchange);
                    }

                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    byte[] body = ("{\"error\": \"Rate limit exceeded\", " +
                            "\"message\": \"You have exceeded the allowed request rate. " +
                            "Please wait and try again.\"}").getBytes();
                    return exchange.getResponse()
                            .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
                });
    }

    private record ApiKeyTier(String name, long capacity, double refillRate) {}
}