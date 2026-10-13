package org.example.rebuiltzijingforum.f01_account.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.dto.LoginDTO;
import org.example.rebuiltzijingforum.f01_account.dto.RegisterDTO;
import org.example.rebuiltzijingforum.f01_account.dto.ResetPasswordDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.f01_account.service.AccountService;
import org.example.rebuiltzijingforum.f01_account.utils.JwtUtils;
import org.example.rebuiltzijingforum.f01_account.vo.AccountVO;
import org.example.rebuiltzijingforum.f01_account.vo.LoginVO;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestAttribute;

/**
 * 本文件要完成：
 * 实现注册、登录、查当前用户和安全修改密码。
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final Clock clock;
    private final ObjectProvider<SearchSyncService> searchSync;

    public AccountServiceImpl(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            Clock clock,
            ObjectProvider<SearchSyncService> searchSync
    )
    {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.clock = clock;
        this.searchSync = searchSync;
    }

    /**
     * 模板                                              案例
     * 标准化输入 → 主动查重                             " Alice " → "alice"
     * entity.set密码哈希(encoder.encode(明文))          entity.setPasswordHash(encoder.encode("abc12345"))
     * mapper.insert(entity)                             userMapper.insert(user)
     * 唯一约束异常 → 业务冲突异常                        重复邮箱 → 409
     */
    @Override
    @Transactional//要么全部成功要么全部回滚
    public AccountVO register(RegisterDTO registerDTO) {
        //规范account email mobile
        String account = normalizeAccount(registerDTO.account());
        String email = registerDTO.email().trim().toLowerCase(Locale.ROOT);
        String mobile = normalizeOptional(registerDTO.mobile());
        //查看数据库内是否已经有了 有了就报错 不允许重复注册
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
            // UNIQUE 约束负责拦截两个并发注册请求。这是数据库干的活哈
            throw conflict("账号、邮箱或手机号已存在");
        }
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.userChanged(entity.getId()); // 提交后再建公开用户索引。
        return AccountVO.from(entity);
    }

    /**
     * 模板                                              案例
     * 按登录标识查询实体                                 findByAccount("alice")
     * encoder.matches(明文, 哈希)                        encoder.matches("abc12345", hash)
     * 校验账号状态 → 生成 Token                          NORMAL → jwtUtils.createToken(user)
     * 凭证错误统一返回未认证                             账号不存在或密码错 → 401
     */
    @Override
    @Transactional(readOnly = true)
    public LoginVO login(LoginDTO loginDTO) {
        String login = normalizeAccount(loginDTO.account());
        UserAccountEntity entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .and(q -> q.eq(UserAccountEntity::getAccount, login)
                        .or().eq(UserAccountEntity::getEmail, login)
                        .or().eq(UserAccountEntity::getMobile, login))
                .last("LIMIT 1"));
        if (    entity == null ||
                !passwordEncoder.matches(loginDTO.password(), entity.getPasswordHash())
        ) {
            // 不区分账号不存在还是密码错误，避免泄露已注册账号。
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
        }
        ensureCanLogin(entity);
        return LoginVO.from(jwtUtils.createToken(entity), entity);
    }

    /**
     * 模板                                              案例
     * id = 可信用户DTO.id()                              id = currentUser.userId()
     * entity = mapper.selectById(id)                    user = userMapper.selectById(1001)
     * 校验状态 → Entity 转 VO                            NORMAL → UserVO.from(user)
     */
    @Override
    @Transactional(readOnly = true)
    public AccountVO getCurrentUser(CurrentUserDTO currentUserDTO) {
        UserAccountEntity entity = findById(currentUserDTO.userId());
        ensureCanLogin(entity);
        return AccountVO.from(entity);
    }

    /**
     * 模板                                              案例
     * 校验当前密码与新密码规则                           old 正确且 new 不同
     * WHERE id=? AND version=旧版本                      WHERE id=1001 AND version=2
     * SET 密码哈希=?, version=旧版本+1                   SET hash=?, version=3
     * 更新成功 → 查询新数据并签发新 Token                affected=1 → newToken
     */
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
                .eq(UserAccountEntity::getId, entity.getId())//左边的作用是告知在执行这段代码的时候要获取当前数据库的ID与entity的ID进行比较
                .eq(UserAccountEntity::getTokenVersion, oldVersion)
                .set(UserAccountEntity::getPasswordHash, passwordEncoder.encode(resetPasswordDTO.newPassword()))
                .set(UserAccountEntity::getTokenVersion, oldVersion + 1)
                .set(UserAccountEntity::getUpdatedAt, LocalDateTime.now(clock)));
        if (updated != 1) //看数据库里面改了几行
            {
            throw conflict("账号状态已变化，请重新登录后再试");
        }

        UserAccountEntity refreshed = findById(entity.getId());
        return LoginVO.from(jwtUtils.createToken(refreshed), refreshed);
    }

    /**
     * 模板                                              案例
     * entity = mapper.selectById(id)                    user = userMapper.selectById(1001)
     * entity == null → 抛业务异常                        null → ACCOUNT_NOT_FOUND
     */
    private UserAccountEntity findById(long id) {
        UserAccountEntity entity = userAccountMapper.selectById(id);//因为ID唯一所以不用构架lambda表达式
        if (entity == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "账号不存在", HttpStatus.UNAUTHORIZED);
        }
        return entity;
    }

    /**
     * 模板                                              案例
     * new LambdaQueryWrapper<Entity>()                  new LambdaQueryWrapper<User>()
     * .eq(Entity::get字段, 值).last("LIMIT 1")          .eq(User::getName, "alice").last("LIMIT 1")
     * mapper.selectOne(查询条件)                         userMapper.selectOne(wrapper)
     */
    private UserAccountEntity findByAccount(String account) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()//lambda是用来代替复杂SQL的
                .eq(UserAccountEntity::getAccount, account)
                .last("LIMIT 1"));
    }

    /**
     * 模板                                              案例
     * exists(字段引用, 值) → 抛冲突异常                  exists(User::getEmail, "a@b.com")
     * 可选字段非空时才查重                               mobile != null → 检查手机号
     */
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

    /**
     * 模板                                              案例
     * mapper.selectCount(wrapper.eq(字段, 值))           userMapper.selectCount(wrapper.eq(User::getName, "alice"))
     * count > 0                                         1 > 0 → true
     */
    private boolean exists(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserAccountEntity, ?> column,
            Object value)///?是通配符 选择对应Entity的一个字段 ? column value类型一致
        {
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
