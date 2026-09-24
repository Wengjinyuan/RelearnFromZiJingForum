package org.example.rebuiltzijingforum.account.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.rebuiltzijingforum.account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.account.dto.LoginDTO;
import org.example.rebuiltzijingforum.account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.account.service.AccountService;
import org.example.rebuiltzijingforum.account.utils.JwtUtils;
import org.example.rebuiltzijingforum.account.vo.AccountVO;
import org.example.rebuiltzijingforum.account.vo.LoginVO;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AccountService 的具体业务实现。 */
@Service
public class AccountServiceImpl implements AccountService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final Clock clock;

    public AccountServiceImpl(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            Clock clock
    )
    {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.clock = clock;
    }

    /** 注册：标准化输入、查重、加密密码并写入数据库。 */
    @Override
    @Transactional//要么全部成功要么全部回滚
    public AccountVO register(RegisterDTO registerDTO) {
        String account = normalizeAccount(registerDTO.account());
        String email = registerDTO.email().trim().toLowerCase(Locale.ROOT);
        String mobile = normalizeOptional(registerDTO.mobile());
        rejectKnownConflict(account, email, mobile);

        LocalDateTime now = LocalDateTime.now(clock);
        UserAccountEntity entity = new UserAccountEntity();
        entity.setAccount(account);
        entity.setPasswordHash(passwordEncoder.encode(registerDTO.password()));
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setRole("USER");
        entity.setStatus("NORMAL");
        entity.setTokenVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            userAccountMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            // UNIQUE 约束负责拦截两个并发注册请求。
            throw conflict("账号、邮箱或手机号已存在");
        }
        return AccountVO.from(entity);
    }

    /** 登录：账号和密码都正确且未被禁用时签发 JWT。 */
    @Override
    @Transactional(readOnly = true)
    public LoginVO login(LoginDTO loginDTO) {
        UserAccountEntity entity = findByAccount(normalizeAccount(loginDTO.account()));
        if (entity == null || !passwordEncoder.matches(loginDTO.password(), entity.getPasswordHash())) {
            // 不区分账号不存在还是密码错误，避免泄露已注册账号。
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
        }
        ensureCanLogin(entity);
        return LoginVO.from(jwtUtils.createToken(entity), entity);
    }

    /** 根据拦截器提供的当前用户 id 查询最新账号数据。 */
    @Override
    @Transactional(readOnly = true)
    public AccountVO getCurrentUser(CurrentUserDTO currentUserDTO) {
        UserAccountEntity entity = findById(currentUserDTO.userId());
        ensureCanLogin(entity);
        return AccountVO.from(entity);
    }

    /** 修改密码并增加 tokenVersion，使修改前签发的 JWT 全部失效。 */
    @Override
    @Transactional
    public LoginVO resetPassword(CurrentUserDTO currentUserDTO, ResetPasswordDTO resetPasswordDTO) {
        UserAccountEntity entity = findById(currentUserDTO.userId());
        ensureCanLogin(entity);
        if (!passwordEncoder.matches(resetPasswordDTO.currentPassword(), entity.getPasswordHash())) {
            throw new BusinessException("CURRENT_PASSWORD_INCORRECT", "当前密码错误", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(resetPasswordDTO.newPassword(), entity.getPasswordHash())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与当前密码相同", HttpStatus.BAD_REQUEST);
        }

        // id + 旧 tokenVersion 组成乐观锁条件，避免并发改密互相覆盖。
        int oldVersion = entity.getTokenVersion();
        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getId, entity.getId())
                .eq(UserAccountEntity::getTokenVersion, oldVersion)
                .set(UserAccountEntity::getPasswordHash, passwordEncoder.encode(resetPasswordDTO.newPassword()))
                .set(UserAccountEntity::getTokenVersion, oldVersion + 1)
                .set(UserAccountEntity::getUpdatedAt, LocalDateTime.now(clock)));
        if (updated != 1) {
            throw conflict("账号状态已变化，请重新登录后再试");
        }

        UserAccountEntity refreshed = findById(entity.getId());
        return LoginVO.from(jwtUtils.createToken(refreshed), refreshed);
    }

    private UserAccountEntity findById(long id) {
        UserAccountEntity entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "账号不存在", HttpStatus.UNAUTHORIZED);
        }
        return entity;
    }

    private UserAccountEntity findByAccount(String account) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getAccount, account)
                .last("LIMIT 1"));
    }

    private void rejectKnownConflict(String account, String email, String mobile) {
        if (exists(UserAccountEntity::getAccount, account)) {
            throw conflict("账号已存在");
        }
        if (exists(UserAccountEntity::getEmail, email)) {
            throw conflict("邮箱已存在");
        }
        if (mobile != null && exists(UserAccountEntity::getMobile, mobile)) {
            throw conflict("手机号已存在");
        }
    }

    private boolean exists(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserAccountEntity, ?> column,
            Object value) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(column, value)) > 0;
    }

    private void ensureCanLogin(UserAccountEntity entity) {
        if (entity.isBanned()) {
            throw new BusinessException("ACCOUNT_BANNED", "账号已被禁用", HttpStatus.FORBIDDEN);
        }
    }

    private BusinessException conflict(String message) {
        return new BusinessException("ACCOUNT_CONFLICT", message, HttpStatus.CONFLICT);
    }

    private String normalizeAccount(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
