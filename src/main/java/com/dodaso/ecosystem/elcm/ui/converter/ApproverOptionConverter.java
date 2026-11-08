package com.dodaso.ecosystem.elcm.ui.converter;

import com.dodaso.ecosystem.elcm.ui.bean.ExtractionBean;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.AssignableUserOptionRow;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import org.springframework.stereotype.Component;

/**
 * ADDED 2026-11-07 -- lets the approver pickers on the Package Review page (Complete review and
 * Change approver) work with whole rows instead of login id text. The picker then shows the row's
 * label (name, login id and team) for a preselected person, keeps the selection while the user
 * types, and offers the suggestions with autocomplete.
 *
 * <p>The text sent by the browser is the login id; it is looked up in the people the page loaded
 * (ExtractionBean.approverCandidates). Text that matches nobody gives null, so nothing is picked.
 */
@Component("approverOptionConverter")
public class ApproverOptionConverter implements Converter<AssignableUserOptionRow> {

  @Override
  public AssignableUserOptionRow getAsObject(
      final FacesContext context, final UIComponent component, final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    final ExtractionBean bean = context.getApplication()
        .evaluateExpressionGet(context, "#{extractionBean}", ExtractionBean.class);
    if (bean == null || bean.getApproverCandidates() == null) {
      return null;
    }
    return bean.getApproverCandidates().stream()
        .filter(u -> u.getLoginId() != null && u.getLoginId().equalsIgnoreCase(value.trim()))
        .findFirst().orElse(null);
  }

  @Override
  public String getAsString(
      final FacesContext context, final UIComponent component, final AssignableUserOptionRow value) {
    return value == null || value.getLoginId() == null ? "" : value.getLoginId();
  }
}
