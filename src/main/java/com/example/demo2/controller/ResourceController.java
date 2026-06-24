package com.example.demo2.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ResourceController {

    @GetMapping("/data")
    public Mono<ResponseEntity<Map<String, Object>>> getData() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Request succeeded",
                "timestamp", Instant.now().toString()
        )));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiter-demo"
        )));
    }
}