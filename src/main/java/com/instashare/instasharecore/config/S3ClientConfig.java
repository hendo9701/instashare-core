package com.instashare.instasharecore.config;

import lombok.val;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.time.Duration;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(S3ClientConfigurationProperties.class)
public class S3ClientConfig {

  @Bean
  public S3AsyncClient s3Client(
      S3ClientConfigurationProperties s3Properties, AwsCredentialsProvider credentialsProvider) {
    val httpClient =
        NettyNioAsyncHttpClient.builder().writeTimeout(Duration.ZERO).maxConcurrency(64).build();
    val serviceConfiguration =
        S3Configuration.builder()
            .checksumValidationEnabled(false)
            .chunkedEncodingEnabled(true)
            .build();
    val clientBuilder =
        S3AsyncClient.builder()
            .httpClient(httpClient)
            .region(s3Properties.getRegion())
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(serviceConfiguration);

    Optional.ofNullable(s3Properties.getEndpoint()).map(clientBuilder::endpointOverride);

    return clientBuilder.build();
  }

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider(
      S3ClientConfigurationProperties s3Properties) {
    if (s3Properties.getAccessKeyId().isBlank()) return DefaultCredentialsProvider.create();
    return () ->
        AwsBasicCredentials.create(
            s3Properties.getAccessKeyId(), s3Properties.getSecretAccessKey());
  }
}
