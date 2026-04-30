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

/**
 * 健康检查接口。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final SystemHealthService systemHealthService;

    public HealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    /** 返回服务和依赖组件的当前状态。 */
    @GetMapping("/health")
    public ApiResponse<HealthStatusResponse> health(HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        return ApiResponse.success(systemHealthService.currentStatus(), requestId);
    }

    /** 执行一次最小 Redis 读写探针。 */
    @PostMapping("/health/redis-probe")
    public ApiResponse<RedisProbeResponse> redisProbe(HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        return ApiResponse.success(systemHealthService.probeRedis(), requestId);
    }
}
