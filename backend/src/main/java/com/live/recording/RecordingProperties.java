package com.live.recording;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recording")
public record RecordingProperties(boolean enabled, S3 s3) {
    public record S3(String accessKey, String secret, String region, String endpoint, String bucket, String publicBaseUrl) {}

    /** 저장된 파일 경로를 재생 가능한 공개 URL 로 바꾼다. */
    public String publicUrl(String filePath) {
        if (s3 == null || s3.publicBaseUrl() == null || s3.publicBaseUrl().isBlank() || filePath == null) {
            return null;
        }
        String base = s3.publicBaseUrl().endsWith("/") ? s3.publicBaseUrl() : s3.publicBaseUrl() + "/";
        return base + filePath;
    }
}
