package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.servlet.ServletContext;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.servlet.config.HttpConfigurationProvider;
import org.ocpsoft.rewrite.servlet.config.rule.Join;

public class RewriteConfiguration extends HttpConfigurationProvider {

  @Override
  public Configuration getConfiguration(ServletContext context) {
    // return
    // ConfigurationBuilder.begin().addRule().when(Direction.isInbound().and(Path.matches("/{param}")))
    // .perform(Forward.to("/{param}.xhtml")); // .where("page").matches(".*");

    // return
    // ConfigurationBuilder.begin().addRule().when(Direction.isInbound().and(Path.matches("/{path}")))
    // .perform(Log.message(Level.INFO, "Client requested path:
    // {path}")).where("path").matches(".*");
    // return ConfigurationBuilder.begin();
    return ConfigurationBuilder.begin().addRule(Join.path("/{path}").to("/{path}.xhtml"))
        .addRule(Join.path("/{path}?{param}").to("/{path}.xhtml?{param}"));
  }

  @Override
  public int priority() {
    return 10;
  }
}