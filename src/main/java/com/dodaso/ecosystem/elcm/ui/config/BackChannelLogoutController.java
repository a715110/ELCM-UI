package com.dodaso.ecosystem.elcm.ui.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenID Connect Back-Channel Logout, receiver side. The SSO server POSTs a signed logout token
 * here (form field {@code logout_token}) when a user logs out of any app; this ends that user's
 * session in THIS app, whether or not a tab is open.
 *
 * <p>The endpoint is public (the SSO server has no session here), so the token is the only
 * credential and is fully validated: signature against the SSO key set, issuer, audience (this
 * app's client id), expiry, the back-channel logout event, no nonce, a subject, and a single use
 * of each token id.
 *
 * <p>Ending a session: it is marked expired in the session registry (the next request of that
 * browser session is answered 401 or redirected to the login page by the expired-session
 * strategy) and the stored OAuth2 tokens of the user are removed.
 */
@RestController
@Slf4j
public class BackChannelLogoutController {

  private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";
  private static final String REGISTRATION_ID = "sso";
  private static final Duration MAX_TOKEN_AGE = Duration.ofMinutes(5);

  private final SessionRegistry sessionRegistry;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final ClientRegistrationRepository registrations;
  private final String authIssuerUri;

  /** token id to the time it can be forgotten; a repeated id is a replay. */
  private final Map<String, Instant> seenTokenIds = new ConcurrentHashMap<>();
  private volatile NimbusJwtDecoder decoder;

  public BackChannelLogoutController(SessionRegistry sessionRegistry,
      OAuth2AuthorizedClientService authorizedClientService,
      ClientRegistrationRepository registrations,
      @Value("${dodaso.instance.auth-issuer-uri}") String authIssuerUri) {
    this.sessionRegistry = sessionRegistry;
    this.authorizedClientService = authorizedClientService;
    this.registrations = registrations;
    this.authIssuerUri = authIssuerUri;
  }

  @PostMapping(path = "/logout/backchannel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> backChannelLogout(@RequestParam("logout_token") String logoutToken) {
    final ClientRegistration registration = registrations.findByRegistrationId(REGISTRATION_ID);
    if (registration == null) {
      return reject("no client registration '" + REGISTRATION_ID + "'");
    }

    final Jwt jwt;
    try {
      jwt = decoder(registration).decode(logoutToken);
    } catch (JwtException e) {
      return reject("token not valid: " + e.getMessage());
    }

    final List<String> audience = jwt.getAudience();
    if (audience == null || !audience.contains(registration.getClientId())) {
      return reject("audience does not include this client");
    }
    final Map<String, Object> events = jwt.getClaimAsMap("events");
    if (events == null || !events.containsKey(EVENT)) {
      return reject("not a back-channel logout token (no events claim)");
    }
    if (jwt.hasClaim("nonce")) {
      return reject("a logout token must not carry a nonce");
    }
    final String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      return reject("no subject");
    }
    final Instant issuedAt = jwt.getIssuedAt();
    if (issuedAt == null || issuedAt.isBefore(Instant.now().minus(MAX_TOKEN_AGE))) {
      return reject("token too old or without iat");
    }
    final String tokenId = jwt.getId();
    if (tokenId == null || !firstUse(tokenId)) {
      return reject("missing or repeated token id");
    }

    final int ended = endSessionsOf(subject);
    authorizedClientService.removeAuthorizedClient(REGISTRATION_ID, subject);
    log.info("Back-channel logout: {} session(s) of {} ended", ended, subject);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  private int endSessionsOf(String subject) {
    int ended = 0;
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      if (principal instanceof AuthenticatedPrincipal user && subject.equals(user.getName())) {
        for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
          session.expireNow();
          ended++;
        }
      }
    }
    return ended;
  }

  /** True the first time a token id is seen; also drops ids whose token can no longer pass. */
  private boolean firstUse(String tokenId) {
    final Instant now = Instant.now();
    seenTokenIds.values().removeIf(forgetAt -> forgetAt.isBefore(now));
    return seenTokenIds.putIfAbsent(tokenId, now.plus(MAX_TOKEN_AGE).plusSeconds(60)) == null;
  }

  private NimbusJwtDecoder decoder(ClientRegistration registration) {
    NimbusJwtDecoder result = decoder;
    if (result == null) {
      synchronized (this) {
        result = decoder;
        if (result == null) {
          result = NimbusJwtDecoder
              .withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
              .jwsAlgorithm(SignatureAlgorithm.RS256)
              // Logout tokens are typed "logout+jwt"; the default accepts only "JWT" or none.
              .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                  new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT,
                      new JOSEObjectType("logout+jwt"), null)))
              .build();
          result.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefault(), issuerValidator()));
          decoder = result;
        }
      }
    }
    return result;
  }

  /** The issuer must be the configured SSO issuer (a trailing slash does not matter). */
  private OAuth2TokenValidator<Jwt> issuerValidator() {
    final String expected = stripTrailingSlash(authIssuerUri);
    return jwt -> {
      final String actual = jwt.getClaimAsString("iss");
      return actual != null && expected.equals(stripTrailingSlash(actual))
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              new org.springframework.security.oauth2.core.OAuth2Error("invalid_token",
                  "The iss claim is not the SSO issuer", null));
    };
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private ResponseEntity<Void> reject(String reason) {
    log.warn("Back-channel logout rejected: {}", reason);
    return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }
}
