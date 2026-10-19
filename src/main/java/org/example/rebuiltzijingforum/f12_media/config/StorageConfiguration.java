package org.example.rebuiltzijingforum.f12_media.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.example.rebuiltzijingforum.f12_media.service.AliyunOssImageStorage;
import org.example.rebuiltzijingforum.f12_media.service.ImageStorage;
import org.example.rebuiltzijingforum.f12_media.service.ImageValidator;
import org.example.rebuiltzijingforum.f12_media.service.LocalImageStorage;
import org.example.rebuiltzijingforum.f12_media.service.OssClientFactory;
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

    @Bean
    @ConditionalOnProperty(prefix = "forum.storage", name = "type", havingValue = "oss")
    public OssClientFactory ossClientFactory(StorageProperties properties) {
        StorageProperties.Oss oss = properties.getOss();
        require("forum.storage.oss.endpoint", oss.getEndpoint());
        require("forum.storage.oss.region", oss.getRegion());
        require("forum.storage.oss.bucket", oss.getBucket());
        require("forum.storage.oss.access-key-id", oss.getAccessKeyId());
        require("forum.storage.oss.access-key-secret", oss.getAccessKeySecret());

        DefaultCredentialProvider credentials =
                new DefaultCredentialProvider(oss.getAccessKeyId().trim(), oss.getAccessKeySecret().trim());
        ClientBuilderConfiguration clientConfiguration = new ClientBuilderConfiguration();
        clientConfiguration.setSignatureVersion(SignVersion.V4);
        return () -> OSSClientBuilder.create()
                .endpoint(oss.getEndpoint().trim())
                .credentialsProvider(credentials)
                .clientConfiguration(clientConfiguration)
                .region(oss.getRegion().trim())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "forum.storage", name = "type", havingValue = "oss")
    public ImageStorage aliyunOssImageStorage(ImageValidator validator, StorageProperties properties,
                                               OssClientFactory clients, Clock clock) {
        return new AliyunOssImageStorage(validator, properties, clients, clock);
    }

    private void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when forum.storage.type=oss");
        }
    }
}
