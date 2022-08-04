package com.instashare.instasharecore.files;

import com.instashare.instasharecore.users.UserService;
import com.instashare.instasharecore.util.*;
import lombok.val;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("File tests")
class FileControllerIntegrationTest {

  @Container static MyMongoDbContainer mongo = MyMongoDbContainer.getInstance();

  @Container static MyRabbitContainer rabbit = MyRabbitContainer.getInstance();
  private final WebClient webClient = WebClient.builder().build();
  @Autowired UserService userService;

  @Value("classpath:sample.txt")
  @Autowired
  Resource sampleFile;
  @LocalServerPort private int serverPort;
  private User validUser;

  private String validAccessToken;

  private String existingFileKey;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", () -> mongo.getConnectionString() + "/instashared-dev");
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
  }

  @BeforeEach
  void setup() throws IOException {
    validUser = new User(randomAlphabetic(6) + "@gmail.com", "1234");
    userService.save(validUser.email(), validUser.password()).block();
    val body = new SignInRequest(validUser.email(), validUser.password());
    val response =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/signin", serverPort))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(SignInResponse.class)
            .timeout(Duration.ofSeconds(2));
    validAccessToken = response.block().accessToken();

    val filename = RandomStringUtils.randomAlphabetic(10) + "-" + sampleFile.getFilename();
    val result =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/files?filename=%s", serverPort, filename))
            .header("Authorization", "Bearer " + validAccessToken)
            .contentType(MediaType.TEXT_PLAIN)
            .contentLength(sampleFile.contentLength())
            .body(BodyInserters.fromResource(sampleFile))
            .retrieve()
            .bodyToMono(UploadResultCompleted.class)
            .timeout(Duration.ofSeconds(10));
    existingFileKey = result.block().uploadResultContent.keys.get(0);
  }

  @Test
  @DisplayName("Listing the files of a user with no files must return an array")
  void list() {
    val listResult =
        webClient
            .get()
            .uri(format("http://localhost:%d/v1/files", serverPort))
            .header("Authorization", "Bearer " + validAccessToken)
            .retrieve()
            .bodyToMono(List.class)
            .timeout(Duration.ofSeconds(2));

    assertThat("List must be empty", listResult.block().size(), Matchers.greaterThanOrEqualTo(0));
  }

  @Test
  @DisplayName("User cannot download RAW files")
  void download() {
    val result =
        webClient
            .get()
            .uri(format("http://localhost:%d/v1/files/%s", serverPort, existingFileKey))
            .header("Authorization", "Bearer " + validAccessToken)
            .exchangeToMono(response -> Mono.just(response.statusCode()))
            .timeout(Duration.ofSeconds(2));
    assertThat("Response status code must be 403", result.block(), is(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("User cannot rename a non-existing file")
  void renameNonExisting() {
    val nonExistingFileKey = randomAlphanumeric(10);
    val renameFileRequest = new RenameFileRequest(randomAlphanumeric(10));
    val httpStatus =
        webClient
            .patch()
            .uri(format("http://localhost:%d/v1/files/%s", serverPort, nonExistingFileKey))
            .header("Authorization", "Bearer " + validAccessToken)
            .bodyValue(renameFileRequest)
            .exchangeToMono(response -> Mono.just(response.statusCode()))
            .timeout(Duration.ofSeconds(2));

    assertThat("Should be 404", httpStatus.block(), is(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("User cannot rename a non-existing file")
  void renameExisting() {
    val renameFileRequest = new RenameFileRequest(randomAlphanumeric(10));
    val httpStatus =
        webClient
            .patch()
            .uri(format("http://localhost:%d/v1/files/%s", serverPort, existingFileKey))
            .header("Authorization", "Bearer " + validAccessToken)
            .bodyValue(renameFileRequest)
            .exchangeToMono(response -> Mono.just(response.statusCode()))
            .timeout(Duration.ofSeconds(2));

    assertThat("Should be 200", httpStatus.block(), is(HttpStatus.OK));
  }

  @Test
  @DisplayName("User can upload files (simple)")
  void upload() throws IOException {
    val response =
        webClient
            .post()
            .uri(
                format(
                    "http://localhost:%d/v1/files?filename=%s",
                    serverPort, sampleFile.getFilename()))
            .header("Authorization", "Bearer " + validAccessToken)
            .contentType(MediaType.TEXT_PLAIN)
            .contentLength(sampleFile.contentLength())
            .body(BodyInserters.fromResource(sampleFile))
            .retrieve()
            .bodyToMono(UploadResultCompleted.class)
            .timeout(Duration.ofSeconds(10));
    val uploadResult = response.block();
    assertThat(
        "Upload status must be COMPLETED", uploadResult.uploadStatus, is(UploadStatus.COMPLETED));
  }

  @Test
  @DisplayName("User can upload files (multipart)")
  void uploadMultipart() {

    val builder = new MultipartBodyBuilder();
    builder.part("file", sampleFile);

    val response =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/files", serverPort))
            .header("Authorization", "Bearer " + validAccessToken)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(UploadResultCompleted.class)
            .timeout(Duration.ofSeconds(50));
    val uploadResult = response.block();
    assertThat(
        "Upload status must be COMPLETED", uploadResult.uploadStatus, is(UploadStatus.COMPLETED));
  }

  enum UploadStatus {
    FAILED,
    COMPLETED
  }

  record UploadResultContentCompleted(List<String> keys) {}

  record UploadResultCompleted(
      UploadStatus uploadStatus, UploadResultContentCompleted uploadResultContent) {}

  record RenameFileRequest(String newName) {}
}
