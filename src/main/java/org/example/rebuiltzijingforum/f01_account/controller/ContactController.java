package org.example.rebuiltzijingforum.f01_account.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f01_account.service.ContactService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts/me/contact")
public class ContactController {
    private final ContactService service;
    public ContactController(ContactService service) { this.service = service; }

    public record Contact(@NotBlank @Email String email,
                          @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String mobile) {}

    @GetMapping
    public ApiResponse<Contact> current(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor) {
        return ApiResponse.success(service.current(actor));
    }

    @PutMapping
    public ApiResponse<Contact> update(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @Valid @RequestBody Contact body) {
        return ApiResponse.success(service.update(actor, body));
    }
}
