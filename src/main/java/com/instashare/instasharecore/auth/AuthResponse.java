package com.instashare.instasharecore.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class AuthResponse {
  @Getter private String accessToken;
}
