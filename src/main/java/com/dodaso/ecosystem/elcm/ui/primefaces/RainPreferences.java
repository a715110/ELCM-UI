package com.dodaso.ecosystem.elcm.ui.primefaces;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Named
@SessionScoped
@Data
@Getter
@Setter
public class RainPreferences implements Serializable {

  //private String menuMode = "layout-static layout-static-active";
  private String menuMode = "layout-static";

  private String darkMode = "light";

  // Changed from "cyan" (the template's shipped default, and what ECWS UI
  // uses) to "chateau-green" so ELCM is visually distinct at a glance --
  // both the layout theme (topbar/menu) and component theme (buttons,
  // inputs, tags, tables) need to match or they'll clash, so both are
  // changed together. Confirmed rain-layout/css/layout-chateau-green-*.css
  // and primefaces-rain-chateau-green-*/theme.css both exist in
  // resources.zip for all three modes (light/dim/dark).
  //
  // Other available palettes, all confirmed present the same way: amber,
  // blue, chambray, cyan (taken by ECWS), orange, paradiso, pink,
  // tapestry, victoria, wisteria -- swap both values below to try another.
  private String layoutPrimaryColor = "chateau-green";

  private String componentTheme = "chateau-green";

  // Changed from "colored" (solid green-filled topbar) to "light" -- the
  // warm-neutral-plus-sparing-accent direction needs a neutral topbar
  // structure, and "light" already provides that natively (see
  // layout-chateau-green-light.css's .layout-topbar-light rule) rather
  // than needing to fight/override the colored variant's green fill.
  // The actual warm-toned retint (vs. its default cool white/gray) lives
  // in the new theme-override.css, not here.
  private String topbarTheme = "light";

  // Changed from "dim" (dark navy #2B394F sidebar, unrelated to the
  // warm-neutral palette) to "light" -- same reasoning as topbarTheme's
  // change: gives a neutral sidebar structure natively instead of fighting
  // a dark-navy base with overrides. Warm retint + accent lives in
  // theme-override.css.
  private String menuTheme = "light";

  private String profileMode = "popup";

  private String inputStyle = "outlined";

  private boolean lightLogo = true;

  private List<ComponentTheme> componentThemes = new ArrayList<ComponentTheme>();

  private List<LayoutPrimaryColor> layoutPrimaryColors = new ArrayList<LayoutPrimaryColor>();

  @PostConstruct
  public void init() {
    componentThemes.add(new ComponentTheme("Blue", "blue", "#2c84d8"));
    componentThemes.add(new ComponentTheme("Wisteria", "wisteria", "#A864AE"));
    componentThemes.add(new ComponentTheme("Cyan", "cyan", "#25A4D4"));
    componentThemes.add(new ComponentTheme("Amber", "amber", "#DB8519"));
    componentThemes.add(new ComponentTheme("Pink", "pink", "#F5487F"));
    componentThemes.add(new ComponentTheme("Orange", "orange", "#CB623A"));
    componentThemes.add(new ComponentTheme("Victoria", "victoria", "#594791"));
    componentThemes.add(new ComponentTheme("Chateau Green", "chateau-green", "#3C9462"));
    componentThemes.add(new ComponentTheme("Paradiso", "paradiso", "#3B9195"));
    componentThemes.add(new ComponentTheme("Chambray", "chambray", "#3161BA"));
    componentThemes.add(new ComponentTheme("Tapestry", "tapestry", "#A2527F"));

    layoutPrimaryColors.add(new LayoutPrimaryColor("Blue", "blue", "#2c84d8"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Wisteria", "wisteria", "#A053A7"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Cyan", "cyan", "#25A4D4"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Amber", "amber", "#DB8519"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Pink", "pink", "#F5487F"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Orange", "orange", "#CB623A"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Victoria", "victoria", "#705BB1"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Chateau Green", "chateau-green", "#3C9462"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Paradiso", "paradiso", "#3B9195"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Chambray", "chambray", "#3161BA"));
    layoutPrimaryColors.add(new LayoutPrimaryColor("Tapestry", "tapestry", "#924470"));
  }

  public void setDarkMode(String darkMode) {
    this.darkMode = darkMode;
    this.menuTheme = darkMode;
    this.topbarTheme = darkMode;
    this.lightLogo = !this.topbarTheme.equals("light");
  }

  public String getLayout() {
    return "layout-" + this.layoutPrimaryColor + '-' + this.darkMode;
  }

  public String getTheme() {
    return this.componentTheme + '-' + this.darkMode;
  }

  public void setLayoutPrimaryColor(String layoutPrimaryColor) {
    this.layoutPrimaryColor = layoutPrimaryColor;
    this.componentTheme = layoutPrimaryColor;
  }


  public void setTopbarTheme(String topbarTheme) {
    this.topbarTheme = topbarTheme;
    this.lightLogo = !this.topbarTheme.equals("light");
  }

  public String getInputStyleClass() {
    return this.inputStyle.equals("filled") ? "ui-input-filled" : "";
  }

  @Getter
  public static class ComponentTheme {

    String name;
    String file;
    String color;

    public ComponentTheme(String name, String file, String color) {
      this.name = name;
      this.file = file;
      this.color = color;
    }

  }

  @Getter
  public static class LayoutPrimaryColor {

    String name;
    String file;
    String color;

    public LayoutPrimaryColor(String name, String file, String color) {
      this.name = name;
      this.file = file;
      this.color = color;
    }

  }
}