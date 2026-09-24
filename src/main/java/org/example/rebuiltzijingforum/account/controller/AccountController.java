package org.example.rebuiltzijingforum.account.controller;

import jakarta.validation.Valid;
import org.example.rebuiltzijingforum.account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.account.dto.LoginDTO;
import org.example.rebuiltzijingforum.account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.account.service.AccountService;
import org.example.rebuiltzijingforum.account.vo.AccountVO;
import org.example.rebuiltzijingforum.account.vo.LoginVO;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 账号接口 Controller。 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 注册账号。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return ApiResponse.success(accountService.register(registerDTO));
    }

    /** 登录并返回 JWT。 */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return ApiResponse.success(accountService.login(loginDTO));
    }

    /** 获取当前登录用户。 */
    @GetMapping("/me")
    public ApiResponse<AccountVO> getCurrentUser(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO) {
        return ApiResponse.success(accountService.getCurrentUser(currentUserDTO));
    }

    /** 修改密码并返回新 JWT。 */
    @PostMapping("/reset-password")
    public ApiResponse<LoginVO> resetPassword(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO,
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO) {
        return ApiResponse.success(accountService.resetPassword(currentUserDTO, resetPasswordDTO));
    }
}
