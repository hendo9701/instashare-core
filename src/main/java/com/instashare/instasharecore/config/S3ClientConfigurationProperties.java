package com.instashare.instasharecore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import software.amazon.awssdk.regions.Region;

import java.net.URI;

@ConfigurationProperties(prefix = "aws.s3")
@Data
public class S3ClientConfigurationProperties {

  private Region region = Region.US_EAST_1;

  private URI endpoint = null;

  private String accessKeyId;

  private String secretAccessKey;

  private String bucket;

  private int multipartMinPartSize = 5 * 1024 * 1024;
}
