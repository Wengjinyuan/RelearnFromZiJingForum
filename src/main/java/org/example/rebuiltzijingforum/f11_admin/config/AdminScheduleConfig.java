package org.example.rebuiltzijingforum.f11_admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启 F11 到期禁言任务；周期由 forum.admin.mute-scan-delay-ms 配置。 */
@Configuration
@EnableScheduling
public class AdminScheduleConfig {}
