package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

  @Autowired
  private SimpleCORSFilter simpleCORSFileter;

  @Value("${spring.profiles.active}")
  private String activeProfile;

  @Bean
  public OAuth2DebugFilter oauth2DebugFilter() {
    return new OAuth2DebugFilter();
  }

  @Bean
  public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
    return new HttpSessionOAuth2AuthorizationRequestRepository();
  }

  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
    DefaultAuthorizationCodeTokenResponseClient accessTokenResponseClient =
        new DefaultAuthorizationCodeTokenResponseClient();

    accessTokenResponseClient.setRequestEntityConverter(authorizationCodeGrantRequest -> {
      ClientRegistration clientRegistration = authorizationCodeGrantRequest.getClientRegistration();
      OAuth2AuthorizationExchange authorizationExchange = authorizationCodeGrantRequest.getAuthorizationExchange();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();
      formParameters.add("grant_type", "authorization_code");
      formParameters.add("code", authorizationExchange.getAuthorizationResponse().getCode());
      formParameters.add("redirect_uri",
          authorizationExchange.getAuthorizationRequest().getRedirectUri());

      // Force basic authentication
      String credentials =
          clientRegistration.getClientId() + ":" + clientRegistration.getClientSecret();
      String encodedCredentials = Base64.getEncoder()
          .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      headers.add(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);

      // Add debugging
      log.trace("=== CUSTOM TOKEN REQUEST ===");
      log.trace("Client ID: {}", clientRegistration.getClientId());
      log.trace("Authorization header being set: Basic {}", encodedCredentials);
      log.trace("Headers: {}", headers);
      log.trace("Form parameters: {}", formParameters);

      URI uri = UriComponentsBuilder.fromUriString(
          clientRegistration.getProviderDetails().getTokenUri()).build().toUri();

      RequestEntity<?> requestEntity = new RequestEntity<>(formParameters, headers, HttpMethod.POST,
          uri);
      log.info("Final RequestEntity headers: {}", requestEntity.getHeaders());

      return requestEntity;
    });
    return accessTokenResponseClient;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .addFilterBefore(oauth2DebugFilter(), OAuth2LoginAuthenticationFilter.class)
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
            .sessionFixation().migrateSession() // Better security than .none()
            .maximumSessions(1) // Limit to one session per user
            .maxSessionsPreventsLogin(false) // Allow new login to invalidate old session
            .sessionRegistry(sessionRegistry())
            .and()
            .invalidSessionUrl("/login") // Redirect to login on invalid session
        )
        .csrf(csrf -> csrf.disable()) // Temporarily disable for testing
        .authorizeHttpRequests(authz -> authz
            // Static resources - allow all
            .requestMatchers("/javax.faces.resource/**", "/resources/**", "/css/**", "/js/**",
                "/images/**").permitAll()
            // RootRedirect and error pages - allow all
            .requestMatchers("/", "/login/**", "/error").permitAll()
            // Session management API endpoints - require authentication but allow access
            .requestMatchers("/api/extend-session", "/api/session-status").authenticated()
            // Debug and test endpoints - require authentication
            .requestMatchers("/api/session-debug", "/api/session-test-extend",
                "/api/session-force-expire", "/api/session-simulate-activity",
                "/api/session-health").authenticated()
            // All other requests require authentication
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .authorizationEndpoint(authorization -> authorization
                .authorizationRequestRepository(authorizationRequestRepository())
            )
            .tokenEndpoint(token -> token
                .accessTokenResponseClient(accessTokenResponseClient())
            )
            .loginPage("/oauth2/authorization/sso?reason=timeout")
            .defaultSuccessUrl("/dashboard", true)
            .failureHandler(new AuthenticationFailureHandler() {
              @Override
              public void onAuthenticationFailure(HttpServletRequest request,
                  HttpServletResponse response,
                  AuthenticationException exception) throws IOException {
                log.warn("OAuth2 login failure: {}", exception.getMessage());

                // Check if it's an authorization_request_not_found error
                if (exception.getMessage().contains("authorization_request_not_found") ||
                    exception.getCause() != null &&
                        exception.getCause().getMessage()
                            .contains("authorization_request_not_found")) {

                  log.info("Redirecting to fresh OAuth2 flow due to expired authorization request");
                  // Redirect to fresh OAuth2 flow
                  response.sendRedirect("/oauth2/authorization/sso?autosubmit=true&refresh="
                      + System.currentTimeMillis());
                } else {
                  // Generic error - could redirect to error page or retry
                  log.error("OAuth2 authentication failed: {}", exception.getMessage());
                  response.sendRedirect("/oauth2/authorization/sso");
                }
              }
            })
            // Add token refresh configuration
            .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService())
            )
        )
        .logout(logout -> logout
            .logoutRequestMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/api/logout", "POST"),
                new AntPathRequestMatcher("/logout", "GET"),
                new AntPathRequestMatcher("/logout", "POST")
            ))
            .logoutSuccessUrl(
                "/oauth2/authorization/sso") // or "https://localhost:8081/login?logout"
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("ECWSSESSIONID")           // <-- use your web cookie name
        );

    return http.build();
  }

  // 3. Custom OAuth2 User Service for token refresh handling
  @Bean
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService() {
    DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    return new OAuth2UserService<OAuth2UserRequest, OAuth2User>() {
      @Override
      public OAuth2User loadUser(OAuth2UserRequest userRequest)
          throws OAuth2AuthenticationException {
        //OAuth2User user = delegate.loadUser(userRequest);
        // Store token information in session for refresh logic
        return delegate.loadUser(userRequest);
      }
    };
  }

  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(
        List.of("https://localhost:*")); // Adjust with your frontend URL
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Authorization"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }
}