package com.dodaso.ecosystem.elcm.ui.config;

import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;
import jakarta.servlet.annotation.WebListener;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WebListener
public class RequestInterceptPhaseListener implements PhaseListener {

  private static final long serialVersionUID = 1L;

  @Override
  public void afterPhase(PhaseEvent event) {
  }

  @Override
  public void beforePhase(PhaseEvent event) {
    FacesContext facesContext = FacesContext.getCurrentInstance();
    Map<String, String> paramValues = facesContext.getExternalContext().getRequestParameterMap();
    // ViewHandler viewHandler = facesContext.getApplication().getViewHandler();
    // String viewId = facesContext.getViewRoot().getViewId();
    // String url = viewHandler.getActionURL(facesContext, viewId);
    if (!paramValues.containsKey("jakarta.faces.ViewState")) {
      paramValues.entrySet().stream().forEach(entry -> {
        if (!entry.getKey().equals("jfwid")) {
          // TODO: implement decode parameter value
          //log.debug("Request Parameter:{} = {}", entry.getKey(), entry.getValue());
        }
      });
    }
  }

  @Override
  public PhaseId getPhaseId() {
    return PhaseId.RESTORE_VIEW;
  }
}