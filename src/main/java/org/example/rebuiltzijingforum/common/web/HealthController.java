package org.example.rebuiltzijingforum.common.web;

import java.time.OffsetDateTime;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthView> health() {
        return ApiResponse.success(new HealthView("UP", OffsetDateTime.now()));
    }

    public record HealthView(String status, OffsetDateTime checkedAt) {
    }
}

