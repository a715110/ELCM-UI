/*
   Copyright 2009-2021 PrimeTek.

   Licensed under PrimeFaces Commercial License, Version 1.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   Licensed under PrimeFaces Commercial License, Version 1.0 (the "License");

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.dodaso.ecosystem.elcm.ui.primefaces;

import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UINamingContainer;
import jakarta.faces.component.UIOutput;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AbortProcessingException;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.faces.event.ListenerFor;
import jakarta.faces.event.PostAddToViewEvent;
import org.primefaces.component.api.Widget;
import org.primefaces.component.menu.AbstractMenu;

// REVERTED (again, 2026-08-18): this project appears to have restored an
// older snapshot at some point -- this @FacesComponent annotation
// (removed several turns ago because WEB-INF/faces-config.xml already
// registers this exact component-type via XML, and having both is a
// duplicate-registration conflict) had come back. Removed again for the
// same reason as before -- see the equivalent note on this class from
// that earlier fix if it's still in version history.
@ListenerFor(sourceClass = RainMenu.class, systemEventClass = PostAddToViewEvent.class)
public class RainMenu extends AbstractMenu implements Widget {

  public static final String COMPONENT_TYPE = "org.primefaces.component.RainMenu";
  public static final String COMPONENT_FAMILY = "org.primefaces.component";
  private static final String DEFAULT_RENDERER = "org.primefaces.component.RainMenuRenderer";
  private static final String[] LEGACY_RESOURCES = new String[]{"primefaces.css",
      "jquery/jquery.js", "jquery/jquery-plugins.js", "primefaces.js"};
  private static final String[] MODERN_RESOURCES = new String[]{"components.css",
      "jquery/jquery.js", "jquery/jquery-plugins.js", "core.js"};

  public RainMenu() {
    setRendererType(DEFAULT_RENDERER);
  }

  public String getFamily() {
    return COMPONENT_FAMILY;
  }

  public String getWidgetVar() {
    return (String) getStateHelper().eval(PropertyKeys.widgetVar, null);
  }

  public void setWidgetVar(String _widgetVar) {
    getStateHelper().put(PropertyKeys.widgetVar, _widgetVar);
  }

  public org.primefaces.model.menu.MenuModel getModel() {
    return (org.primefaces.model.menu.MenuModel) getStateHelper().eval(PropertyKeys.model, null);
  }

  public void setModel(org.primefaces.model.menu.MenuModel _model) {
    getStateHelper().put(PropertyKeys.model, _model);
  }

  public String getStyle() {
    return (String) getStateHelper().eval(PropertyKeys.style, null);
  }

  public void setStyle(String _style) {
    getStateHelper().put(PropertyKeys.style, _style);
  }

  public String getStyleClass() {
    return (String) getStateHelper().eval(PropertyKeys.styleClass, null);
  }

  public void setStyleClass(String _styleClass) {
    getStateHelper().put(PropertyKeys.styleClass, _styleClass);
  }

  public String resolveWidgetVar() {
    FacesContext context = getFacesContext();
    String userWidgetVar = (String) getAttributes().get("widgetVar");

    if (userWidgetVar != null) {
      return userWidgetVar;
    } else {
      return "widget_" + getClientId(context).replaceAll(
          "-|" + UINamingContainer.getSeparatorChar(context), "_");
    }
  }

  @Override
  public void processEvent(ComponentSystemEvent event) throws AbortProcessingException {
    if (event instanceof PostAddToViewEvent) {
      FacesContext context = getFacesContext();
      UIViewRoot root = context.getViewRoot();

      boolean isPrimeConfig;
      try {
        isPrimeConfig = Class.forName("org.primefaces.config.PrimeConfiguration") != null;
      } catch (ClassNotFoundException e) {
        isPrimeConfig = false;
      }

      String[] resources = (isPrimeConfig) ? MODERN_RESOURCES : LEGACY_RESOURCES;

      for (String res : resources) {
        UIComponent component = context.getApplication().createComponent(UIOutput.COMPONENT_TYPE);
        if (res.endsWith("css")) {
          component.setRendererType("jakarta.faces.resource.Stylesheet");
        } else if (res.endsWith("js")) {
          component.setRendererType("jakarta.faces.resource.Script");
        }

        component.getAttributes().put("library", "primefaces");
        component.getAttributes().put("name", res);

        root.addComponentResource(context, component);
      }
    }
  }

  protected enum PropertyKeys {

    widgetVar, model, style, styleClass;

    String toString;

    PropertyKeys(String toString) {
      this.toString = toString;
    }

    PropertyKeys() {
    }

    public String toString() {
      return ((this.toString != null) ? this.toString : super.toString());
    }
  }
}