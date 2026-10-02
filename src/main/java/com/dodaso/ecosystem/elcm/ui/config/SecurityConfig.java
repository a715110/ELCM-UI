package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

  // FIX: default added. Without it the app cannot start when no profile is set
  // explicitly (the profile is no longer baked into the build since Step 1).
  @Value("${spring.profiles.active:default}")
  private String activeProfile;

  /** The UI's own OAuth2 login entry point (registration id "sso"), relative to the context path. */
  private static final String OAUTH2_LOGIN_PATH = "/oauth2/authorization/sso";

  /** Session attribute / query parameter marking the single automatic retry. */
  private static final String RETRY_PARAM = "oauth2retry";

  /** A retry within this window counts as "already retried" (no second retry). */
  private static final long RETRY_WINDOW_MS = 60_000L;

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
        // ADDED 2026-10-02 -- without this, Spring Security's own default
        // X-Frame-Options header-writer runs AFTER SimpleCORSFilter (which
        // tries to set X-Frame-Options: SAMEORIGIN) and overrides it with
        // DENY, blocking the Stage Documents dashboard's file-preview
        // feature's <iframe> entirely (both the plain PDF preview and the
        // Gotenberg-converted-PDF preview -- this was likely always latent
        // for PDFs, just never exercised, since <img>-based image preview
        // isn't affected by X-Frame-Options at all). sameOrigin() here is
        // explicit and wins over whatever default Spring Security would
        // otherwise apply, matching SimpleCORSFilter's intent.
        .headers(headers -> headers
            .frameOptions(frameOptions -> frameOptions.sameOrigin())
        )
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
            // FIX: the previous handler used response.sendRedirect("/oauth2/authorization/sso"),
            // which is relative to the SERVER root, not to this app: behind nginx it became
            // https://localhost/oauth2/authorization/sso (no /ecws or /elcm) and returned 404.
            // It also retried on EVERY failure, so a persistent error (e.g. an untrusted
            // certificate on the token call) looped between the UI and the SSO server until
            // the browser gave up. Now: one automatic retry for an expired authorization
            // request, otherwise a plain error response with the cause logged.
            .failureHandler(oauth2LoginFailureHandler())
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
            // Context-relative: Spring's redirect strategy adds the context path itself.
            .logoutSuccessUrl(OAUTH2_LOGIN_PATH)
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("ELCMSESSIONID")   // FIX: was ECWSSESSIONID (copied from ECWS); ELCM's cookie is ELCMSESSIONID
        );

    return http.build();
  }

  /**
   * OAuth2 login failure handling.
   *
   * <ul>
   *   <li>{@code authorization_request_not_found} (the saved authorization request
   *       expired, e.g. the login page sat open too long): restart the login ONCE.</li>
   *   <li>Anything else, or a failure on the retry itself: 401 with the reason logged.
   *       Never an automatic restart, which is what used to loop.</li>
   * </ul>
   * Every redirect is built with {@code request.getContextPath()}, so it works behind
   * nginx, on the app's own port and in the cloud.
   */
  private AuthenticationFailureHandler oauth2LoginFailureHandler() {
    return (HttpServletRequest request, HttpServletResponse response,
        AuthenticationException exception) -> {
      final boolean expiredRequest = mentionsAuthorizationRequestNotFound(exception);
      // The failure arrives on the callback request, which cannot carry our own
      // parameter, so the retry is remembered in the session with a timestamp.
      // Older than RETRY_WINDOW_MS counts as a fresh attempt, so a stale flag never
      // blocks the retry for a later, unrelated expiry.
      final Object lastRetry = request.getSession().getAttribute(RETRY_PARAM);
      final boolean alreadyRetried = lastRetry instanceof Long ts
          && System.currentTimeMillis() - ts < RETRY_WINDOW_MS;

      if (expiredRequest && !alreadyRetried) {
        log.info("OAuth2 authorization request expired - restarting login once");
        request.getSession().setAttribute(RETRY_PARAM, System.currentTimeMillis());
        response.sendRedirect(request.getContextPath() + OAUTH2_LOGIN_PATH
            + "?" + RETRY_PARAM + "=1");
        return;
      }

      request.getSession().removeAttribute(RETRY_PARAM);
      log.error("OAuth2 authentication failed (no automatic retry): {}",
          exception.getMessage(), exception);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          "Login failed. Please close this tab and sign in again.");
    };
  }

  private static boolean mentionsAuthorizationRequestNotFound(AuthenticationException exception) {
    final String marker = "authorization_request_not_found";
    return (exception.getMessage() != null && exception.getMessage().contains(marker))
        || (exception.getCause() != null && exception.getCause().getMessage() != null
        && exception.getCause().getMessage().contains(marker));
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