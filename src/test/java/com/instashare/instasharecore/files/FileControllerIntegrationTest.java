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
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

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

  @Autowired FileRepository fileRepository;

  @Value("classpath:sample.txt")
  @Autowired
  Resource sampleFile;

  @LocalServerPort private int serverPort;
  private User validUser;

  private String validAccessToken;

  private String existingFileKey;

  private String existingFileName;

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
    existingFileName = filename;
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
  @DisplayName("User can download COMPRESSED files")
  void downloadCompressedShouldWork() throws IOException {
    // Update file status manually
    val file = fileRepository.findByIdAndOwner(existingFileKey, validUser.email()).block();
    file.setFileStatus(FileStatus.COMPRESSED);
    fileRepository.save(file).block();

    // Download
    val result =
        webClient
            .get()
            .uri(format("http://localhost:%d/v1/files/%s", serverPort, existingFileKey))
            .header("Authorization", "Bearer " + validAccessToken)
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(Duration.ofSeconds(10));
    val tempFile = Files.createTempFile("download-", ".tmp");
    Files.write(tempFile, Objects.requireNonNull(result.share().block()));
    assertThat("File size must be greater than 0", Files.size(tempFile), Matchers.greaterThan(0L));
    // Clean-up
    val deleteResult = tempFile.toFile().delete();
    assertThat("File should be deleted after download", deleteResult, Matchers.is(true));
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
  @DisplayName("User cannot upload file with existing filename")
  void cannotUploadFileExistingFilename() throws IOException {
    val response =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/files?filename=%s", serverPort, existingFileName))
            .header("Authorization", "Bearer " + validAccessToken)
            .contentType(MediaType.TEXT_PLAIN)
            .contentLength(sampleFile.contentLength())
            .body(BodyInserters.fromResource(sampleFile))
            .exchangeToMono(clientResponse -> Mono.just(clientResponse.statusCode()))
            .timeout(Duration.ofSeconds(10));
    val statusCode = response.block();
    assertThat("Cannot upload file with existing filename", statusCode, is(HttpStatus.BAD_REQUEST));
  }

  @Test
  @DisplayName(
      "An error should be thrown when attempting to upload a file (simple) without content-length header")
  void uploadWithoutContentLengthSimple() {
    val response =
        webClient
            .post()
            .uri(
                format(
                    "http://localhost:%d/v1/files?filename=%s",
                    serverPort, sampleFile.getFilename()))
            .header("Authorization", "Bearer " + validAccessToken)
            .contentType(MediaType.TEXT_PLAIN)
            .exchangeToMono(clientResponse -> Mono.just(clientResponse.statusCode()))
            .timeout(Duration.ofSeconds(10));
    val statusCode = response.block();
    assertThat("Bad request must be returned", statusCode, is(HttpStatus.BAD_REQUEST));
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

  @Test
  @DisplayName(
      "When counting the number of files of a user owning just one file, then one must be retrieved")
  void countShouldBeEqualToOne() {
    val response =
        webClient
            .get()
            .uri("http://localhost:%d/v1/files/count".formatted(serverPort))
            .header("Authorization", "Bearer " + validAccessToken)
            .retrieve()
            .bodyToMono(Long.class)
            .timeout(Duration.ofSeconds(2));
    assertThat("Count should be one", response.block(), Matchers.is(1L));
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
