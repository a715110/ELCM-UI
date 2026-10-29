package com.dodaso.ecosystem.elcm.ui.config;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * REVISED 2026-10-27. The sign-in calls (token exchange, key download) go through the JVM default
 * HTTPS settings, so the local profile, where the SSO server uses a self-signed developer
 * certificate, relies on this class to accept it. Before this revision it switched certificate
 * and host name checking off for the whole JVM in every environment, the cloud included.
 *
 * <p>Now certificate checking is skipped only in the local profile, or when
 * {@code dodaso.tls.trust-all} says so explicitly (the same rule as RESTServiceClient in
 * baseline-common). In every other profile the JVM defaults are left alone.
 */
@Configuration
@Slf4j
public class OAuth2SslConfig {

  private final boolean trustAll;

  public OAuth2SslConfig(
      Environment environment,
      @Value("${dodaso.tls.trust-all:#{null}}") Boolean trustAllSetting) {
    this.trustAll = trustAllSetting != null
        ? trustAllSetting
        : environment.acceptsProfiles(Profiles.of("local"));
  }

  @Bean
  public RestTemplate oauth2RestTemplate() throws KeyManagementException, NoSuchAlgorithmException {
    if (trustAll) {
      log.warn("Certificate and host name checking is OFF for HTTPS calls in this JVM"
          + " (local profile or dodaso.tls.trust-all=true). Never use this outside development.");
      installTrustAll();
    }

    // Configure the HTTP client factory
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(70000);
    factory.setReadTimeout(70000);

    RestTemplate restTemplate = new RestTemplate(factory);

    // Add OAuth2 specific message converters and error handlers
    restTemplate.setMessageConverters(Arrays.asList(
        new OAuth2AccessTokenResponseHttpMessageConverter()
    ));
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

    return restTemplate;
  }

  private static void installTrustAll() throws KeyManagementException, NoSuchAlgorithmException {
    // Create a trust manager that accepts all certificates
    TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          public void checkClientTrusted(X509Certificate[] certs, String authType) {
          }

          public void checkServerTrusted(X509Certificate[] certs, String authType) {
          }
        }
    };

    // Install the all-trusting trust manager
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

    // Create all-trusting host name verifier
    HostnameVerifier allHostsValid = (hostname, session) -> true;
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
  }
}
