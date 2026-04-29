package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.HealthStatusResponse;
import com.example.rag.model.response.RedisProbeResponse;
import com.example.rag.service.SystemHealthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final SystemHealthService systemHealthService;

    public HealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthStatusResponse> health(HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        return ApiResponse.success(systemHealthService.currentStatus(), requestId);
    }

    @PostMapping("/health/redis-probe")
    public ApiResponse<RedisProbeResponse> redisProbe(HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        return ApiResponse.success(systemHealthService.probeRedis(), requestId);
    }
}
