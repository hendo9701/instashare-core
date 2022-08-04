package com.instashare.instasharecore.auth;

import com.instashare.instasharecore.config.JwtUtil;
import com.instashare.instasharecore.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  private final PasswordEncoder passwordEncoder;

  private final JwtUtil jwtUtil;

  @PostMapping("/signin")
  public Mono<ResponseEntity<AuthResponse>> signin(@Valid @RequestBody AuthRequest authRequest) {
    return userService
        .findByEmail(authRequest.getEmail())
        .filter(user -> passwordEncoder.matches(authRequest.getPassword(), user.getPassword()))
        .map(userDetails -> ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(userDetails))))
        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
  }

  @PostMapping("/signup")
  public Mono<ResponseEntity<String>> signup(@Valid @RequestBody AuthRequest authRequest) {
    return userService
        .findByEmail(authRequest.getEmail())
        .map(user -> ResponseEntity.status(HttpStatus.CONFLICT).body("Email in use."))
        .switchIfEmpty(
            userService
                .save(authRequest.getEmail(), authRequest.getPassword())
                .map(user -> ResponseEntity.ok().body("Registered")));
  }
}
