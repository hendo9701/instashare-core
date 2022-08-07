package com.instashare.instasharecore.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@NoArgsConstructor
@Getter
@Setter
public class AuthRequest {

  @Email private String email;

  @NotBlank private String password;
}
