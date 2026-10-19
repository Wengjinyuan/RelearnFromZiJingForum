package org.example.rebuiltzijingforum.f12_media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forum.storage")
public class StorageProperties {
    private String type = "local";
    private String localRoot = "./uploads";
    private final Oss oss = new Oss();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public Oss getOss() {
        return oss;
    }

    public static class Oss {
        private String endpoint = "";
        private String region = "";
        private String bucket = "";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String publicBaseUrl = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

        public String resolvedPublicBaseUrl() {
            String configured = trimTrailingSlash(publicBaseUrl);
            if (!configured.isBlank()) return configured;
            String host = endpoint == null ? "" : endpoint.trim()
                    .replaceFirst("^https?://", "");
            host = trimTrailingSlash(host);
            return "https://" + bucket.trim() + "." + host;
        }

        private String trimTrailingSlash(String value) {
            if (value == null) return "";
            return value.trim().replaceFirst("/+$", "");
        }
    }
}
