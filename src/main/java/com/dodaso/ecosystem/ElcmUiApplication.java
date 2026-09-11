package com.dodaso.ecosystem;

import com.dodaso.ecosystem.baseline.common.security.JwtAuthTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Entry point for the ELCM UI module.
 *
 * Packaged as a WAR (see pom.xml -- maven-war-plugin "war-exploded" +
 * spring-boot-maven-plugin repackage skipped) for deployment to an external
 * servlet container, matching the ECWS deployment pattern. Extending
 * SpringBootServletInitializer keeps that working while `mvn spring-boot:run`
 * still works for local development via the embedded container.
 */
@SpringBootApplication(scanBasePackages = {"com.dodaso.ecosystem"}, exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ComponentScan(excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthTokenFilter.class})})
public class ElcmUiApplication extends SpringBootServletInitializer {
    @Value("${spring.profiles.active}")
    private String activeProfile;

    public static void main(String[] args) {
        SpringApplication.run(ElcmUiApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ElcmUiApplication.class);
    }

    @Bean
    ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            servletContext.setInitParameter("com.sun.faces.forceLoadConfiguration",
                Boolean.TRUE.toString());
            servletContext.setInitParameter("jakarta.faces.ENABLE_CDI_RESOLVER_CHAIN",
                Boolean.TRUE.toString());
            servletContext.setInitParameter("primefaces.UPLOADER", "auto");
            servletContext.setInitParameter("primefaces.FILE_UPLOAD_MODE", "auto");

            servletContext.setInitParameter("jakarta.faces.STATE_SAVING_METHOD", "client");  //TODO: need to keep an eye on it after the change from client to server.
            if (activeProfile.equals("local")) {
                servletContext.setInitParameter("jakarta.faces.PROJECT_STAGE", "development");
            } else {
                servletContext.setInitParameter("jakarta.faces.PROJECT_STAGE", "production");
            }
            servletContext.setInitParameter("jakarta.faces.FACELETS_REFRESH_PERIOD", "0");

            servletContext.setInitParameter("jakarta.faces.FACELETS_SKIP_COMMENTS",
                Boolean.TRUE.toString());
            servletContext.setInitParameter("jakarta.faces.PARTIAL_STATE_SAVING",
                Boolean.TRUE.toString());

            servletContext.setInitParameter("jakarta.faces.VALIDATE_EMPTY_FIELDS",
                Boolean.TRUE.toString());
            servletContext.setInitParameter("jakarta.faces.validator.ENABLE_VALIDATE_WHOLE_BEAN",
                Boolean.TRUE.toString());
            servletContext.setInitParameter("jakarta.faces.CLIENT_WINDOW_MODE", "none");  //TODO: need to keep an eye on it after the change from url to none as part of sso testing.

            servletContext.setInitParameter("primefaces.FONT_AWESOME", Boolean.TRUE.toString());
            servletContext.setInitParameter("primefaces.MOVE_SCRIPTS_TO_BOTTOM", Boolean.TRUE.toString());
            servletContext.setInitParameter("primefaces.SUBMIT", "partial");
            servletContext.setInitParameter("primefaces.WEBSOCKET_ENABLED", Boolean.TRUE.toString());

            servletContext.setInitParameter("primefaces.CLIENT_SIDE_VALIDATION", Boolean.TRUE.toString());

            servletContext.setInitParameter("com.sun.faces.numberOfViewsInSession", "15");
            servletContext.setInitParameter("com.sun.faces.compressViewState", Boolean.TRUE.toString());

            servletContext.setInitParameter("jakarta.faces.UPLOAD_MAX_FILE_SIZE", "16777216"); // 16MB
            servletContext.setInitParameter("jakarta.faces.UPLOAD_MAX_REQUEST_SIZE", "20971520"); // 20MB

            // servletContext.setInitParameter("primefaces.THEME",
            // "rain-#{rainPreferences.theme}");
            servletContext.setInitParameter("primefaces.THEME", "saga");
            servletContext.setInitParameter("jakarta.faces.FACELETS_LIBRARIES",
                "./WEB-INF/primefaces-rain.taglib.xml");
            servletContext.setInitParameter("org.ocpsoft.rewrite.annotation.SCAN_LIB_DIRECTORY",
                Boolean.TRUE.toString());
        };
    }
}