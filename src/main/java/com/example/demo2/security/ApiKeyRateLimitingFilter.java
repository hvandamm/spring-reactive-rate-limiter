package com.example.demo2.security;

import com.example.demo2.config.RateLimiterProperties;
import com.example.demo2.config.RateLimiterProperties.TierConfig;
import com.example.demo2.service.TokenBucketRateLimiterService;
import jakarta.annotation.PostConstruct;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
@Order(1)
public class ApiKeyRateLimitingFilter implements WebFilter {

    static final String API_KEY_HEADER = "X-API-KEY";

    private final TokenBucketRateLimiterService rateLimiterService;
    private final RateLimiterProperties rateLimiterProperties;

    /**
     * Map of API key → TierConfig, built dynamically from application.properties.
     */
    private final Map<String, TierConfig> apiKeyRegistry = new HashMap<>();

    public ApiKeyRateLimitingFilter(TokenBucketRateLimiterService rateLimiterService,
                                    RateLimiterProperties rateLimiterProperties) {
        this.rateLimiterService = rateLimiterService;
        this.rateLimiterProperties = rateLimiterProperties;
    }

    @PostConstruct
    void buildKeyRegistry() {
        for (Map.Entry<String, TierConfig> entry : rateLimiterProperties.getTiers().entrySet()) {
            TierConfig config = entry.getValue();
            apiKeyRegistry.put(config.getKey(), config);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey == null || !apiKeyRegistry.containsKey(apiKey)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"error\": \"Missing or invalid X-API-KEY header\"}".getBytes();
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }

        TierConfig tier = apiKeyRegistry.get(apiKey);

        return rateLimiterService.tryConsume(apiKey, tier.getCapacity(), tier.getRefillRate())
                .flatMap(allowed -> {
                    if (allowed) {
                        exchange.getResponse().getHeaders()
                                .add("X-RateLimit-Limit", String.valueOf(tier.getCapacity()));
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
}