package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @GetMapping("/")
    public Map<String, String> hello() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from the DevOps pipeline demo!");
        response.put("version", appVersion);
        response.put("podHostname", hostname());
        return response;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
