package com.ratelimiter.core;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello! Rate Limiter is running! 🚀";
    }

    @GetMapping("/health")
    public String health() {
        return "Service is healthy ✅";
    }
}