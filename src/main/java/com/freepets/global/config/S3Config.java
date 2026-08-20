package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.freepets.infra.s3.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.accessKey(),
                s3Properties.secretKey()
        );

        return S3Client.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
