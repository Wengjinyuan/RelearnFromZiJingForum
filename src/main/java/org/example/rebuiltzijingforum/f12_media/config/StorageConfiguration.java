package org.example.rebuiltzijingforum.f12_media.config;

import org.example.rebuiltzijingforum.f12_media.service.ImageStorage;
import org.example.rebuiltzijingforum.f12_media.service.ImageValidator;
import org.example.rebuiltzijingforum.f12_media.service.LocalImageStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "forum.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public ImageStorage localImageStorage(ImageValidator validator, StorageProperties properties, Clock clock) {
        return new LocalImageStorage(validator, properties, clock);
    }
}
