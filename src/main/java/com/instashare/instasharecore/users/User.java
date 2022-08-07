package com.instashare.instasharecore.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.instashare.instasharecore.auth.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@Document(value = "users")
public class User implements UserDetails {

  private static final long serialVersionUID = 1L;

  @Id private String id;

  @Getter
  @Indexed(unique = true)
  private String email;

  private String password;
  @Getter private Boolean enabled;
  @Getter private List<Role> roles;

  public User(String email, String password, Role role) {
    this.email = email;
    this.password = password;
    roles = List.of(role);
  }

  @JsonIgnore
  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return false;
  }

  @Override
  public boolean isAccountNonLocked() {
    return false;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return this.enabled;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return this.roles.stream()
        .map(authority -> new SimpleGrantedAuthority(authority.name()))
        .collect(Collectors.toList());
  }
}
