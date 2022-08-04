package com.instashare.instasharecore.config;

import com.instashare.instasharecore.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;

@Component
public class JwtUtil {

  @Value("${instashare.app.jwtSecret}")
  private String secret;

  @Value("${instashare.app.jwtExpirationMs}")
  private String expirationTime;

  private Key key;

  @PostConstruct
  public void init() {
    this.key = Keys.hmacShaKeyFor(secret.getBytes());
  }

  public Claims getAllClaimsFromToken(String token) {
    return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
  }

  public String getUsernameFromToken(String token) {
    return getAllClaimsFromToken(token).getSubject();
  }

  public Date getExpirationDateFromToken(String token) {
    return getAllClaimsFromToken(token).getExpiration();
  }

  private Boolean isTokenExpired(String token) {
    val expiration = getExpirationDateFromToken(token);
    return expiration.before(new Date());
  }

  public String generateToken(User user) {
    val claims = new HashMap<String, Object>();
    claims.put("role", user.getRoles());
    return doGenerateToken(claims, user.getUsername());
  }

  private String doGenerateToken(HashMap<String, Object> claims, String email) {
    val expirationTimeLong = Long.parseLong(expirationTime);
    val createdDate = new Date();
    val expirationDate = new Date(createdDate.getTime() + expirationTimeLong * 1000);
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(email)
        .setIssuedAt(createdDate)
        .setExpiration(expirationDate)
        .signWith(key)
        .compact();
  }

  public Boolean validateToken(String token) {

    return !isTokenExpired(token);
  }
}
