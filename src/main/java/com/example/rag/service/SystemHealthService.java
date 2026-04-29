package com.example.rag.service;

import com.example.rag.model.response.HealthStatusResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class SystemHealthService {

    private final Environment environment;

    public SystemHealthService(Environment environment) {
        this.environment = environment;
    }

    public HealthStatusResponse currentStatus() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        return new HealthStatusResponse("UP", "rag-service", activeProfiles, Instant.now());
    }
}
