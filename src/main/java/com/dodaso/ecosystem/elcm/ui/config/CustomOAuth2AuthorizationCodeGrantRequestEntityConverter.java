package com.dodaso.ecosystem.elcm.ui.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CustomOAuth2AuthorizationCodeGrantRequestEntityConverter
    implements Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> {

  @Override
  public RequestEntity<?> convert(OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest) {
    ClientRegistration clientRegistration = authorizationCodeGrantRequest.getClientRegistration();
    OAuth2AuthorizationExchange authorizationExchange = authorizationCodeGrantRequest.getAuthorizationExchange();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();
    formParameters.add("grant_type", "authorization_code");
    formParameters.add("code", authorizationExchange.getAuthorizationResponse().getCode());
    formParameters.add("redirect_uri", authorizationExchange.getAuthorizationRequest().getRedirectUri());

    // Handle different authentication methods
    if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(clientRegistration.getClientAuthenticationMethod())) {
      String credentials = clientRegistration.getClientId() + ":" + clientRegistration.getClientSecret();
      String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      headers.add(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
    } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(clientRegistration.getClientAuthenticationMethod())) {
      formParameters.add("client_id", clientRegistration.getClientId());
      formParameters.add("client_secret", clientRegistration.getClientSecret());
    } else if (ClientAuthenticationMethod.CLIENT_SECRET_JWT.equals(clientRegistration.getClientAuthenticationMethod())) {
      // For JWT authentication, you would need to create a JWT here
      // This is complex and requires JWT library
      formParameters.add("client_id", clientRegistration.getClientId());
      formParameters.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
      // formParameters.add("client_assertion", createJWT(clientRegistration));
      throw new UnsupportedOperationException("JWT client authentication not implemented in this example");
    } else if (ClientAuthenticationMethod.NONE.equals(clientRegistration.getClientAuthenticationMethod())) {
      formParameters.add("client_id", clientRegistration.getClientId());
    }

    URI uri = UriComponentsBuilder.fromUriString(clientRegistration.getProviderDetails().getTokenUri()).build().toUri();

    return new RequestEntity<>(formParameters, headers, HttpMethod.POST, uri);
  }
}