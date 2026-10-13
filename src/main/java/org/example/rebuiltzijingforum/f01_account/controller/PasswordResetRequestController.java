package org.example.rebuiltzijingforum.f01_account.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f01_account.service.PasswordResetRequestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PasswordResetRequestController {
    private final PasswordResetRequestService service;

    public PasswordResetRequestController(PasswordResetRequestService service) { this.service = service; }

    public record ResetRequestInput(@NotBlank String account) {}

    @PostMapping("/api/accounts/password-reset-requests")
    public ApiResponse<Void> request(@Valid @RequestBody ResetRequestInput body) {
        service.request(body.account());
        return ApiResponse.success(null);
    }

    @GetMapping("/api/admin/password-reset-requests")
    public ApiResponse<List<PasswordResetRequestService.RequestRow>> pending(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor) {
        return ApiResponse.success(service.pending(actor));
    }

    @PutMapping("/api/admin/password-reset-requests/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor) {
        service.approve(actor, id);
        return ApiResponse.success(null);
    }
}
