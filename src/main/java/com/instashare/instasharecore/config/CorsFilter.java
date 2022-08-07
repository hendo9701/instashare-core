package com.instashare.instasharecore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@EnableWebFlux
public class CorsFilter implements WebFluxConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // Replace '*' with actual origin
    registry
        .addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*");
  }
}
