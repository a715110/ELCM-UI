package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.quality.ExtractionControlUiService;
import com.dodaso.ecosystem.elcm.ui.service.quality.ExtractionControlView;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Backing bean of the extraction control card on the Extraction Quality screen (ADDED 2026-10-18):
 * shows whether the extraction agent is paused and lets an administrator pause or resume it. The
 * state is read from elcm-service when the page opens and after each action; elcm-service refuses
 * anyone who is not an administrator.
 */
@Named
@ViewScoped
@Getter
@Setter
public class ExtractionControlBean implements Serializable {

  @Autowired
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private ExtractionControlUiService uiService;

  private ExtractionControlView state = new ExtractionControlView();
  private boolean loadFailed;

  /** The reason typed for a Pause. */
  private String pauseReason;

  @PostConstruct
  void init() {
    load();
  }

  /** Reads the state. */
  public void load() {
    final ExtractionControlView view = uiService.state();
    loadFailed = view == null;
    if (view != null) {
      state = view;
    }
  }

  /** Clears the reason box before the Pause dialog opens. */
  public void preparePause() {
    pauseReason = null;
  }

  /** Pause confirmed. Callback parameter "done" is true when the agent was paused. */
  public void pause() {
    final String reason = pauseReason == null ? "" : pauseReason.trim();
    if (reason.isEmpty()) {
      add(FacesMessage.SEVERITY_WARN, "Reason needed", "Say why the agent is paused.");
      org.primefaces.PrimeFaces.current().ajax().addCallbackParam("done", false);
      return;
    }
    final ExtractionControlUiService.Result result = uiService.pause(reason);
    boolean done = false;
    switch (result.outcome()) {
      case OK -> {
        done = true;
        state = result.view() == null ? state : result.view();
        add(
            FacesMessage.SEVERITY_INFO,
            "Extraction paused",
            "No new package will start. A package being read is put back in the queue after the"
                + " document it is on.");
      }
      case INVALID ->
          add(
              FacesMessage.SEVERITY_WARN,
              "Reason not accepted",
              "The reason is required and at most 500 characters.");
      case FORBIDDEN ->
          add(FacesMessage.SEVERITY_WARN, "No permission", "Only administrators can pause extraction.");
      default -> add(FacesMessage.SEVERITY_ERROR, "Pause failed", "Please try again.");
    }
    if (done) {
      pauseReason = null;
    }
    org.primefaces.PrimeFaces.current().ajax().addCallbackParam("done", done);
  }

  /** Resumes the agent. */
  public void resume() {
    final ExtractionControlUiService.Result result = uiService.resume();
    switch (result.outcome()) {
      case OK -> {
        state = result.view() == null ? state : result.view();
        add(FacesMessage.SEVERITY_INFO, "Extraction resumed", "The queue is being worked again.");
      }
      case FORBIDDEN ->
          add(FacesMessage.SEVERITY_WARN, "No permission", "Only administrators can resume extraction.");
      default -> add(FacesMessage.SEVERITY_ERROR, "Resume failed", "Please try again.");
    }
  }

  private static void add(
      final FacesMessage.Severity severity, final String summary, final String detail) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
  }
}
