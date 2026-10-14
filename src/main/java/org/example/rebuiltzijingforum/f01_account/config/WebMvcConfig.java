package org.example.rebuiltzijingforum.f01_account.config;

import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import java.nio.file.Paths;

/**
 * 本文件要完成：
 * 注册 F01 鉴权拦截器并指定受保护接口。
 * 已理解
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;

    /**
     * 获取验证消息
     * @param authenticationInterceptor
     */
    public WebMvcConfig(AuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    /**
     * 模板                                              案例
     * registry.addInterceptor(拦截器)                   registry.addInterceptor(loginInterceptor)
     * .addPathPatterns("受保护路径")                    .addPathPatterns("/api/orders/me")
     * .excludePathPatterns("公开路径")                  .excludePathPatterns("/api/orders/public")
     * 要验证身份的都要加在这
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns(
                        "/api/accounts/me",
                        "/api/accounts/me/contact",
                        "/api/accounts/reset-password",
                        "/api/profiles/me",
                        "/api/profiles/me/avatar",
                        "/api/posts/**",
                        "/api/comments/**",
                        "/api/interactions/**",
                        "/api/follows/**",
                        "/api/notifications/**",
                        "/api/messages/**",
                        "/api/search/ai/**",
                        "/api/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get("uploads").toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
