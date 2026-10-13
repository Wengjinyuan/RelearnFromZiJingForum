package org.example.rebuiltzijingforum.f01_account.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 本文件要完成：
 * 提供 F01 密码加密器和可测试时钟。
 */
@Configuration
public class AccountConfig {

    /**
     * 模板                                              案例
     * @Bean                                             @Bean
     * public 类型 方法名()                              public Clock testClock()
     * return 创建好的依赖对象                           return Clock.systemUTC()
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * 加密的
     * 模板                                              案例
     * @Bean                                             @Bean
     * public 接口类型 方法名()                          public PasswordEncoder encoder()
     * return new 实现类(配置)                           return new BCryptPasswordEncoder(12)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
