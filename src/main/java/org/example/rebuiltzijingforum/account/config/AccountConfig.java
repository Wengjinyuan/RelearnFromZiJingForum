package org.example.rebuiltzijingforum.account.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 账号模块用到的 Bean 配置。 */
@Configuration
public class AccountConfig {

    /** 提供统一时钟，方便测试时间相关逻辑。 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** 使用 BCrypt 保存和验证密码。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
