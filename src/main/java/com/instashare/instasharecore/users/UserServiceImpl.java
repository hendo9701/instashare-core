package com.instashare.instasharecore.users;

import com.instashare.instasharecore.auth.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;

  public Mono<User> findByEmail(@NonNull String email) {
    return userRepository.findByEmail(email);
  }

  public Mono<User> save(String email, String password) {
    return userRepository.save(new User(email, passwordEncoder.encode(password), Role.ROLE_USER));
  }
}
