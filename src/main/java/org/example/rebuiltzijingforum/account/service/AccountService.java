package org.example.rebuiltzijingforum.account.service;

import org.example.rebuiltzijingforum.account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.account.dto.LoginDTO;
import org.example.rebuiltzijingforum.account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.account.vo.AccountVO;
import org.example.rebuiltzijingforum.account.vo.LoginVO;

/** 账号业务 Service 接口。 */
public interface AccountService {

    AccountVO register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    AccountVO getCurrentUser(CurrentUserDTO currentUserDTO);

    LoginVO resetPassword(CurrentUserDTO currentUserDTO, ResetPasswordDTO resetPasswordDTO);
}
