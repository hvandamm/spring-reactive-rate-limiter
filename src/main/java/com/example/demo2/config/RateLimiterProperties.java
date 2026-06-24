package com.example.demo2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConfigurationProperties(prefix = "app.security")
public class RateLimiterProperties {

    private final Map<String, TierConfig> tiers = new ConcurrentHashMap<>();

    public Map<String, TierConfig> getTiers() {
        return tiers;
    }

    public static class TierConfig {
        private String key;
        private long capacity;
        private double refillRate;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public double getRefillRate() {
            return refillRate;
        }

        public void setRefillRate(double refillRate) {
            this.refillRate = refillRate;
        }
    }
}