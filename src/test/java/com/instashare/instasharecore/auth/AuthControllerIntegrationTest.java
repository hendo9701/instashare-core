package com.instashare.instasharecore.auth;

import com.instashare.instasharecore.users.UserService;
import com.instashare.instasharecore.util.*;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Authentication tests")
class AuthControllerIntegrationTest {

  @Container static MyMongoDbContainer mongo = MyMongoDbContainer.getInstance();

  @Container static MyRabbitContainer rabbit = MyRabbitContainer.getInstance();
  private static User validUser;
  private final WebClient webClient = WebClient.builder().build();
  @Autowired UserService userService;
  @LocalServerPort private int serverPort;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", () -> mongo.getConnectionString() + "/instashared-dev");
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
  }

  @BeforeEach
  void setup() {
    validUser = new User(randomAlphabetic(6) + "@gmail.com", "1234");
    userService.save(validUser.email(), validUser.password()).block();
  }

  @Test
  @DisplayName("Non-existing user cannot sign-in")
  void signinNonExisting() {
    val randomEmail = randomAlphabetic(6).toLowerCase(Locale.ROOT) + "@gmail.com";
    val randomPassword = RandomStringUtils.randomAlphanumeric(10);
    val body = new SignInRequest(randomEmail, randomPassword);
    val responseStatusCode =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/signin", serverPort))
            .bodyValue(body)
            .exchangeToMono(response -> Mono.just(response.statusCode()))
            .timeout(Duration.ofSeconds(5));
    assertThat("Should be 401", responseStatusCode.block(), is(HttpStatus.UNAUTHORIZED));
  }

  @Test
  @DisplayName("Existing user can sign-in")
  void signin() {
    val body = new SignInRequest(validUser.email(), validUser.password());
    val responseStatusCode =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/signin", serverPort))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(SignInResponse.class)
            .timeout(Duration.ofSeconds(2));
    val accessToken = Objects.requireNonNull(responseStatusCode.block()).accessToken();
    assertThat("Access token must be retrieved", accessToken, is(notNullValue()));
  }

  @Test
  @DisplayName("User can sign-up")
  void signup() {
    val randomEmail = randomAlphabetic(6) + "@gmail.com";
    val randomPassword = RandomStringUtils.randomAlphanumeric(10);
    val body = new SignInRequest(randomEmail, randomPassword);
    val responseStatusCode =
        webClient
            .post()
            .uri(format("http://localhost:%d/v1/signup", serverPort))
            .bodyValue(body)
            .exchangeToMono(response -> Mono.just(response.statusCode()))
            .timeout(Duration.ofSeconds(2));
    assertThat("Should be 200", responseStatusCode.block(), is(HttpStatus.OK));
  }
}
