package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleResult;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleRows;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleRows.AdminView;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleRows.FieldOption;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleRows.RuleRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.CriticalDateRuleUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Backing bean of the Critical Date Rules screen (ADDED 2026-11-21): the rules that decide which
 * contract dates become ECWS tasks, with add, change, switch on or off, delete and check now. Every
 * change goes to elcm-service, which checks it and the caller's permission; a refusal is shown as a
 * message and nothing changes on screen.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class CriticalDateRulesBean implements Serializable {

  private final CriticalDateRuleUiService service;

  private AdminView view = new AdminView();
  private boolean loadFailed;

  /** Fields of the rule dialog; editId is null when adding a rule. */
  private Long editId;
  private int editOpenTasks;
  @Setter
  private String code;
  @Setter
  private String label;
  @Setter
  private String fieldKey;
  @Setter
  private String noticeFieldKey;
  @Setter
  private Integer leadDays = 90;
  @Setter
  private String priority = "High";
  @Setter
  private boolean ruleEnabled = true;

  @PostConstruct
  void init() {
    reload();
  }

  public void reload() {
    final AdminView loaded = service.view();
    loadFailed = loaded == null;
    if (loaded != null) {
      view = loaded;
    }
  }

  public List<RuleRow> getRules() {
    return view.getRules();
  }

  /** True when the feature will create tasks: both switches are on. */
  public boolean isRunning() {
    return view.isEnabled() && view.isEcwsEnabled();
  }

  public boolean isEditing() {
    return editId != null;
  }

  /** The dialog title. */
  public String getDialogTitle() {
    return isEditing() ? "Change rule " + code : "New rule";
  }

  /** Date fields to pick from; keeps the rule's current field even if no template has it now. */
  public List<FieldOption> getDateOptions() {
    return withCurrent(view.getDateFields(), fieldKey);
  }

  /** Number fields to pick from, for the notice period. */
  public List<FieldOption> getNumberOptions() {
    return withCurrent(view.getNumberFields(), noticeFieldKey);
  }

  /** What a change does to the tasks already made, shown while a rule with open tasks is edited. */
  public String getImpactText() {
    if (editOpenTasks <= 0) {
      return "";
    }
    return editOpenTasks + " task(s) are open for this rule. Pointing the rule at another contract"
        + " field or notice field closes them in ECWS at the next check, and new ones are made"
        + " for the new date. A new lead time, priority or name applies only to tasks made"
        + " from now on.";
  }

  // ------------------------------------------------------------------ actions

  public void prepareNew() {
    editId = null;
    editOpenTasks = 0;
    code = null;
    label = null;
    fieldKey = null;
    noticeFieldKey = null;
    leadDays = 90;
    priority = "High";
    ruleEnabled = true;
  }

  public void prepareEdit(final Long id) {
    for (final RuleRow row : view.getRules()) {
      if (row.getId().equals(id)) {
        editId = row.getId();
        editOpenTasks = row.getOpenTasks();
        code = row.getCode();
        label = row.getLabel();
        fieldKey = row.getFieldKey();
        noticeFieldKey = row.getNoticeFieldKey() == null ? "" : row.getNoticeFieldKey();
        leadDays = row.getLeadDays();
        priority = row.getPriority();
        ruleEnabled = row.isEnabled();
        return;
      }
    }
    warn("Not opened", "The rule could not be found. Refresh the page.");
  }

  public void save() {
    final CriticalDateRuleResult result = editId == null
        ? service.create(code, label, fieldKey, noticeFieldKey, leadDays, priority, ruleEnabled)
        : service.update(editId, label, fieldKey, noticeFieldKey, leadDays, priority, ruleEnabled);
    if (accept(result)) {
      done(editId == null ? "Rule added." : "Rule saved.");
      org.primefaces.PrimeFaces.current().executeScript("PF('ruleDlg').hide()");
    }
  }

  public void toggle(final Long id, final boolean enable) {
    final CriticalDateRuleResult result = service.setEnabled(id, enable);
    if (accept(result)) {
      done(enable ? "Rule switched on." : "Rule switched off. Its open tasks close at the next check.");
    }
  }

  public void delete(final Long id) {
    final CriticalDateRuleResult result = service.delete(id);
    if (accept(result)) {
      done("Rule deleted.");
    }
  }

  public void checkNow() {
    final CriticalDateRuleResult result = service.checkNow();
    if (!result.isOk()) {
      warn("Not checked", result.getMessage());
      return;
    }
    final CriticalDateRuleRows.CheckResult check = result.getCheck();
    if (check != null && check.isActive()) {
      done(check.getMessage());
    } else {
      warn("Nothing was checked", check == null ? "No answer from the service." : check.getMessage());
    }
    reload();
  }

  // ------------------------------------------------------------------ helpers

  private static List<FieldOption> withCurrent(final List<FieldOption> options,
      final String current) {
    final List<FieldOption> list = new ArrayList<>(options);
    if (current != null && !current.isBlank()
        && list.stream().noneMatch(o -> current.equals(o.getKey()))) {
      final FieldOption extra = new FieldOption();
      extra.setKey(current);
      extra.setLabel("No longer in a published template");
      list.add(extra);
    }
    return list;
  }

  private boolean accept(final CriticalDateRuleResult result) {
    if (result.isOk()) {
      if (result.getView() != null) {
        view = result.getView();
        loadFailed = false;
      }
      return true;
    }
    warn("Not saved", result.getMessage());
    return false;
  }

  private static void done(final String text) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_INFO, "Done", text));
  }

  private static void warn(final String title, final String text) {
    FacesContext.getCurrentInstance().addMessage(null,
        new FacesMessage(FacesMessage.SEVERITY_WARN, title, text));
  }
}
