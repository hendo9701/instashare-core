package com.instashare.instasharecore.users;

import reactor.core.publisher.Mono;

public interface UserService {

  Mono<User> findByEmail(String username);

  Mono<User> save(String email, String password);
}
