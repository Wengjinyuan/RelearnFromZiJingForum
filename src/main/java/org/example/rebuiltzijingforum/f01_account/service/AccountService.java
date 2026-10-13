package org.example.rebuiltzijingforum.f01_account.service;

import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.dto.LoginDTO;
import org.example.rebuiltzijingforum.f01_account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.f01_account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.f01_account.vo.AccountVO;
import org.example.rebuiltzijingforum.f01_account.vo.LoginVO;

/**
 * 本文件要完成：
 * 定义 F01 注册、登录、当前用户和修改密码能力。
 * 这里是真正进行加密解密的地方AuthenticationInterceptor只是调用这里的办法 相当于Controller和Mapper的关系
 */
public interface AccountService {

    /**
     * 模板                                              案例
     * 返回VO 方法名(注册DTO dto)                        UserVO signup(SignupDTO dto)
     * 输入只接收允许注册的字段                          dto 只含账号、密码、邮箱
     */
    AccountVO register(RegisterDTO registerDTO);

    /**
     * 模板                                              案例
     * 登录VO 方法名(登录DTO dto)                        TokenVO login(LoginDTO dto)
     * 成功结果同时携带 Token 和用户                     TokenVO(token, user)
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 模板                                              案例
     * 用户VO 方法名(可信用户DTO user)                   UserVO current(CurrentUserDTO user)
     * 用户身份来自服务端鉴权结果                        user.userId() = 1001
     */
    AccountVO getCurrentUser(CurrentUserDTO currentUserDTO);

    /**
     * 模板                                              案例
     * 登录VO 方法名(可信用户DTO user, 改密DTO dto)      TokenVO changePassword(UserDTO user, PasswordDTO dto)
     * 改密成功后返回新的登录凭证                        TokenVO(newToken, user)
     */
    LoginVO resetPassword(CurrentUserDTO currentUserDTO, ResetPasswordDTO resetPasswordDTO);
}
