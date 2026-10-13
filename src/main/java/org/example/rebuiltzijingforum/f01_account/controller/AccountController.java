package org.example.rebuiltzijingforum.f01_account.controller;

import jakarta.validation.Valid;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.dto.LoginDTO;
import org.example.rebuiltzijingforum.f01_account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.f01_account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f01_account.service.AccountService;
import org.example.rebuiltzijingforum.f01_account.vo.AccountVO;
import org.example.rebuiltzijingforum.f01_account.vo.LoginVO;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本文件要完成：
 * 接收注册、登录、当前用户和修改密码请求并调用 Service。
 */
@RestController
/**
 表明是在是处理hTTP请求的
 */
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 模板                                              案例
     * @PostMapping("路径")                              @PostMapping("/signup")
     * Result<VO> 方法名(@Valid @RequestBody DTO dto)    Result<UserVO> signup(@Valid @RequestBody SignupDTO dto)
     * return Result.success(service.方法(dto))           return Result.success(userService.signup(dto))
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return ApiResponse.success(accountService.register(registerDTO));
    }

    /**
     * 模板                                              案例
     * @PostMapping("路径")                              @PostMapping("/login")
     * Result<VO> 方法名(@Valid @RequestBody DTO dto)    Result<TokenVO> login(@Valid @RequestBody LoginDTO dto)
     * return Result.success(service.方法(dto))           return Result.success(userService.login(dto))
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO)
    {
        return ApiResponse.success(accountService.login(loginDTO));
    }

    /**
     * 模板                                              案例
     * @GetMapping("路径")                               @GetMapping("/me")
     * 方法名(@RequestAttribute("属性名") DTO user)      current(@RequestAttribute("user") UserDTO user)
     * service.方法(user)                                profileService.current(user)
     */
    @GetMapping("/me")
    public ApiResponse<AccountVO> getCurrentUser(
            //用于检验是不是当前用户-->@RequestAttribute(AuthenticationInterceptor.CURRENT_USER)
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO) {
        return ApiResponse.success(accountService.getCurrentUser(currentUserDTO));
    }

    /**
     * @Valid用于校验数据格式
     * 模板                                              案例
     * @PostMapping("路径")                              @PostMapping("/password")
     * 方法名(@RequestAttribute DTO user, @RequestBody DTO dto) change(UserDTO user, PasswordDTO dto)
     * service.方法(user, dto)                           userService.changePassword(user, dto)
     */
    @PostMapping("/reset-password")
    public ApiResponse<LoginVO> resetPassword(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO,
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO) {
        return ApiResponse.success(accountService.resetPassword(currentUserDTO, resetPasswordDTO));
    }
}
