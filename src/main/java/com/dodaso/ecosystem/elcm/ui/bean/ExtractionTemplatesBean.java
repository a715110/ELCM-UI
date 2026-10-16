package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.PackageOutcome;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.UploadContentCheck;
import com.dodaso.ecosystem.elcm.ui.service.template.TemplateAdminUiService;
import com.dodaso.ecosystem.elcm.ui.service.template.TemplateRows;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 * Backing bean of the Extraction Templates screen (ADDED 2026-10-14), for System Admin and Super
 * Admin. The list shows every version of every template. Opening a draft lets the administrator
 * change its fields (a copy is held here until Save draft), publish it with a reason, or discard
 * it. Opening a published or retired version shows it read only, with its change log, and lets the
 * administrator copy it into a new draft or compare it with another version.
 *
 * <p>Only elcm-service decides what is allowed; this bean checks the obvious things (a key and a
 * label, a reason of 10 characters) so the administrator does not wait for a round trip.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ExtractionTemplatesBean implements Serializable {

  /** Shortest reason accepted when publishing; elcm-service checks the same number. */
  public static final int MIN_REASON = 10;

  /** Answers in a row that could not be read (every 3 seconds) before a trial run is given up. */
  private static final int TRIAL_LOST_AFTER_MISSES = 5;

  private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,99}$");

  private final TemplateAdminUiService templateAdminUiService;

  private List<TemplateRows.Summary> templates = new ArrayList<>();
  private boolean loadFailed;
  private TemplateRows.Options options = new TemplateRows.Options();

  /** The version shown in the detail card; null when none is open. */
  private TemplateRows.Detail selected;

  /** The fields shown in the detail card. For a draft this is the working copy. */
  private List<TemplateRows.Field> fields = new ArrayList<>();

  /** True when the working copy differs from what was last saved. */
  private boolean dirty;

  private List<TemplateRows.Event> events = new ArrayList<>();

  @Setter private Long compareWithId;
  private TemplateRows.Diff diff;

  /** The field in the edit dialog, and its place in the list (-1 for a new field). */
  private TemplateRows.Field editing = new TemplateRows.Field();

  private int editingIndex = -1;

  @Setter private String publishReason;
  @Setter private String discardReason;
  @Setter private String newName;
  @Setter private Long newContractTypeId;
  @Setter private String newReason;

  /** ADDED 2026-10-15 - the trial run shown in the trial dialog; null until a document is chosen. */
  private TemplateRows.Trial trial;

  /** ADDED 2026-10-15 - what deleting the shown template would remove, and what was typed to confirm. */
  private TemplateRows.DeleteImpact deleteImpact;

  @Setter private String deleteConfirmName;
  @Setter private String deleteReasonText;

  /** How many times in a row the trial run could not be read. */
  private int trialMisses;

  @PostConstruct
  void init() {
    final TemplateRows.Options loaded = templateAdminUiService.options();
    if (loaded != null) {
      options = loaded;
    }
    reload();
  }

  // ---- list ----

  public void reload() {
    final List<TemplateRows.Summary> rows = templateAdminUiService.list();
    loadFailed = rows == null;
    templates = rows == null ? new ArrayList<>() : rows;
  }

  /** Opens a version in the detail card. */
  public void open(final TemplateRows.Summary row) {
    if (blockedByUnsavedChanges(row.getId())) {
      return;
    }
    load(row.getId());
  }

  /** Opens the open draft of the template that is shown. */
  public void openDraft() {
    if (selected != null && selected.getOpenDraftId() != null) {
      if (blockedByUnsavedChanges(selected.getOpenDraftId())) {
        return;
      }
      load(selected.getOpenDraftId());
    }
  }

  public void close() {
    if (blockedByUnsavedChanges(null)) {
      return;
    }
    clearSelection();
  }

  /** Throws away the unsaved changes and shows the saved draft again. */
  public void revert() {
    if (selected != null) {
      load(selected.getSummary().getId());
    }
  }

  // ---- new template and new draft ----

  public void prepareNew() {
    newName = null;
    newContractTypeId = null;
    newReason = null;
  }

  public void confirmNew() {
    final TemplateRows.Result r =
        templateAdminUiService.createTemplate(newName, newContractTypeId, newReason);
    if (!report(r, "Template not created")) {
      callback("ok", false);
      return;
    }
    callback("ok", true);
    adopt(r.getDetail());
    reload();
    add(
        FacesMessage.SEVERITY_INFO,
        "Template created",
        "Add its fields, save the draft, then publish it.");
  }

  /** Copies the version that is shown into a new draft. */
  public void createDraft() {
    if (selected == null || blockedByUnsavedChanges(null)) {
      return;
    }
    final TemplateRows.Result r = templateAdminUiService.createDraft(selected.getSummary().getId());
    if (r.getOutcome() == PackageOutcome.CONFLICT) {
      add(
          FacesMessage.SEVERITY_WARN,
          "A draft is already open",
          "Open the existing draft, or discard it, before starting another one.");
      load(selected.getSummary().getId());
      reload();
      return;
    }
    if (!report(r, "Draft not created")) {
      return;
    }
    adopt(r.getDetail());
    reload();
    add(FacesMessage.SEVERITY_INFO, "Draft created", "Change the fields, save, then publish.");
  }

  // ---- editing a draft ----

  public void addField() {
    editing = new TemplateRows.Field();
    editing.setDataType(firstDataType());
    editingIndex = -1;
  }

  public void editField(final TemplateRows.Field row) {
    editing = row.copy();
    editingIndex = fields.indexOf(row);
  }

  /** The edit dialog's OK: checks the field and puts it in the working copy. */
  public void applyField() {
    final String key = trim(editing.getKey());
    final String label = trim(editing.getLabel());
    String problem = null;
    if (key == null || !KEY.matcher(key).matches()) {
      problem =
          "The key starts with a lower case letter and uses only lower case letters, digits and"
              + " underscores, for example base_rent_monthly.";
    } else if (label == null) {
      problem = "Enter a label, the name people see on the review screen.";
    } else if (editing.getDataType() == null) {
      problem = "Choose a data type.";
    } else {
      for (int i = 0; i < fields.size(); i++) {
        if (i != editingIndex && key.equals(fields.get(i).getKey())) {
          problem = "Another field already uses the key " + key + ".";
        }
      }
    }
    if (problem != null) {
      add(FacesMessage.SEVERITY_ERROR, "Field not accepted", problem);
      callback("ok", false);
      return;
    }
    editing.setKey(key);
    editing.setLabel(label);
    if (editingIndex >= 0 && editingIndex < fields.size()) {
      fields.set(editingIndex, editing);
    } else {
      fields.add(editing);
    }
    renumber();
    dirty = true;
    editing = new TemplateRows.Field();
    editingIndex = -1;
    callback("ok", true);
  }

  public void removeField(final TemplateRows.Field row) {
    fields.remove(row);
    renumber();
    dirty = true;
  }

  public void moveUp(final TemplateRows.Field row) {
    final int i = fields.indexOf(row);
    if (i > 0) {
      fields.remove(i);
      fields.add(i - 1, row);
      renumber();
      dirty = true;
    }
  }

  public void moveDown(final TemplateRows.Field row) {
    final int i = fields.indexOf(row);
    if (i >= 0 && i < fields.size() - 1) {
      fields.remove(i);
      fields.add(i + 1, row);
      renumber();
      dirty = true;
    }
  }

  public void saveDraft() {
    if (selected == null || !selected.getSummary().isDraft()) {
      return;
    }
    final TemplateRows.Result r =
        templateAdminUiService.save(selected.getSummary().getId(), fields);
    if (!report(r, "Draft not saved")) {
      return;
    }
    adopt(r.getDetail());
    reload();
    add(FacesMessage.SEVERITY_INFO, "Draft saved", "The change is in the change log.");
  }

  // ---- publish and discard ----

  public void preparePublish() {
    publishReason = null;
    if (selected == null || !selected.getSummary().isDraft()) {
      callback("ok", false);
      return;
    }
    if (dirty) {
      add(FacesMessage.SEVERITY_WARN, "Save the draft first", "Publish uses the saved draft.");
      callback("ok", false);
      return;
    }
    callback("ok", true);
  }

  public void confirmPublish() {
    final String reason = trim(publishReason);
    if (reason == null || reason.length() < MIN_REASON) {
      add(
          FacesMessage.SEVERITY_ERROR,
          "Reason needed",
          "Give a reason of at least " + MIN_REASON + " characters.");
      callback("ok", false);
      return;
    }
    final TemplateRows.Result r =
        templateAdminUiService.publish(selected.getSummary().getId(), reason);
    if (!report(r, "Not published")) {
      callback("ok", false);
      return;
    }
    callback("ok", true);
    adopt(r.getDetail());
    reload();
    add(
        FacesMessage.SEVERITY_INFO,
        "Published",
        "Version " + selected.getSummary().getVersion() + " is now used for new extractions.");
  }

  public void prepareDiscard() {
    discardReason = null;
  }

  public void confirmDiscard() {
    final PackageOutcome outcome =
        templateAdminUiService.discard(selected.getSummary().getId(), trim(discardReason));
    if (outcome != PackageOutcome.OK) {
      report(TemplateRows.Result.of(outcome), "Draft not discarded");
      return;
    }
    clearSelection();
    reload();
    add(FacesMessage.SEVERITY_INFO, "Draft discarded", "It stays in the change log.");
  }

  // ---- delete a template for good (ADDED 2026-10-15) ----

  /** Opens the delete dialog for the template of the version that is shown. */
  public void prepareDelete() {
    deleteConfirmName = null;
    deleteReasonText = null;
    deleteImpact = null;
    if (selected == null) {
      callback("ok", false);
      return;
    }
    deleteImpact = templateAdminUiService.deleteImpact(selected.getSummary().getId());
    if (deleteImpact == null) {
      add(FacesMessage.SEVERITY_ERROR, "Not available", "Please try again.");
      callback("ok", false);
      return;
    }
    callback("ok", true);
  }

  public void confirmDelete() {
    if (selected == null || deleteImpact == null || deleteImpact.isBlocked()) {
      callback("ok", false);
      return;
    }
    final String typed = trim(deleteConfirmName);
    if (typed == null || !typed.equals(deleteImpact.getName())) {
      add(
          FacesMessage.SEVERITY_ERROR,
          "Name not confirmed",
          "Type the name of the template exactly as shown: " + deleteImpact.getName());
      callback("ok", false);
      return;
    }
    final String reason = trim(deleteReasonText);
    if (reason == null || reason.length() < MIN_REASON) {
      add(
          FacesMessage.SEVERITY_ERROR,
          "Reason needed",
          "Give a reason of at least " + MIN_REASON + " characters.");
      callback("ok", false);
      return;
    }
    final TemplateRows.Result r =
        templateAdminUiService.deleteTemplate(selected.getSummary().getId(), typed, reason);
    if (!report(r, "Template not deleted")) {
      callback("ok", false);
      return;
    }
    callback("ok", true);
    final String name = deleteImpact.getName();
    clearSelection();
    reload();
    add(FacesMessage.SEVERITY_INFO, "Template deleted", name + " and all its versions are gone.");
  }

  // ---- trial run (ADDED 2026-10-15) ----

  /** Opens the trial dialog for the version that is shown. */
  public void prepareTrial() {
    trial = null;
    trialMisses = 0;
    if (selected == null) {
      callback("ok", false);
      return;
    }
    if (dirty) {
      add(
          FacesMessage.SEVERITY_WARN,
          "Save the draft first",
          "The trial run uses the saved draft.");
      callback("ok", false);
      return;
    }
    callback("ok", true);
  }

  /** A document was chosen in the trial dialog: start reading it at once. */
  public void handleTrialUpload(final FileUploadEvent event) {
    trialMisses = 0;
    if (selected == null) {
      return;
    }
    final UploadedFile file = event.getFile();
    final byte[] content;
    try {
      content = file.getContent();
    } catch (final RuntimeException e) {
      trial = TemplateRows.Trial.refused("The file could not be read. Please try again.");
      return;
    }
    final String problem = UploadContentCheck.problem(file.getFileName(), content);
    if (problem != null) {
      trial = TemplateRows.Trial.refused(problem);
      return;
    }
    trial =
        templateAdminUiService.startTrial(
            selected.getSummary().getId(), file.getFileName(), file.getContentType(), content);
  }

  /** Asked every few seconds by the dialog while the document is being read. */
  public void pollTrial() {
    if (trial == null || !trial.isWorking() || trial.getId() == null) {
      return;
    }
    final TemplateRows.Trial now = templateAdminUiService.trialStatus(trial.getId());
    if (now == null) {
      trialMisses++;
      if (trialMisses >= TRIAL_LOST_AFTER_MISSES) {
        trial =
            TemplateRows.Trial.refused(
                "The trial run was lost (the service may have restarted). Choose the document"
                    + " again.");
      }
      return;
    }
    trialMisses = 0;
    trial = now;
  }

  /** True when the dialog should stop asking. */
  public boolean isTrialFinished() {
    return trial == null || !trial.isWorking();
  }

  /** Called when the dialog is closed or another document is wanted. */
  public void clearTrial() {
    trial = null;
    trialMisses = 0;
  }

  // ---- compare ----

  /** Versions of the shown template that it can be compared with. */
  public List<TemplateRows.Summary> getCompareChoices() {
    final List<TemplateRows.Summary> out = new ArrayList<>();
    if (selected != null) {
      for (final TemplateRows.Summary t : templates) {
        if (t.getName().equals(selected.getSummary().getName())
            && !t.getId().equals(selected.getSummary().getId())) {
          out.add(t);
        }
      }
    }
    return out;
  }

  public void runCompare() {
    diff = null;
    if (selected != null && compareWithId != null) {
      diff = templateAdminUiService.compare(compareWithId, selected.getSummary().getId());
      if (diff == null) {
        add(FacesMessage.SEVERITY_ERROR, "Not compared", "Please try again.");
      }
    }
  }

  // ---- helpers ----

  /** True when the working copy has editable fields. */
  public boolean isEditable() {
    return selected != null && selected.getSummary().isDraft();
  }

  public List<TemplateRows.Option> getDataTypes() {
    return options.getDataTypes();
  }

  public List<TemplateRows.Option> getContractTypes() {
    return options.getContractTypes();
  }

  private void load(final Long id) {
    final TemplateRows.Detail detail = templateAdminUiService.detail(id);
    if (detail == null) {
      add(FacesMessage.SEVERITY_ERROR, "Not available", "That version could not be loaded.");
      return;
    }
    adopt(detail);
  }

  private void adopt(final TemplateRows.Detail detail) {
    selected = detail;
    fields = new ArrayList<>();
    if (detail.getFields() != null) {
      detail.getFields().forEach(f -> fields.add(f.copy()));
    }
    dirty = false;
    diff = null;
    compareWithId = null;
    events = templateAdminUiService.events(detail.getSummary().getId());
  }

  private void clearSelection() {
    selected = null;
    fields = new ArrayList<>();
    events = new ArrayList<>();
    diff = null;
    dirty = false;
  }

  private boolean blockedByUnsavedChanges(final Long targetId) {
    if (dirty
        && selected != null
        && (targetId == null || !targetId.equals(selected.getSummary().getId()))) {
      add(
          FacesMessage.SEVERITY_WARN,
          "Unsaved changes",
          "Save the draft, or choose Discard changes, before leaving "
              + selected.getSummary().getTitle()
              + ".");
      return true;
    }
    return false;
  }

  /** Shows what went wrong; true when the call went through without problems. */
  private boolean report(final TemplateRows.Result r, final String title) {
    if (r.getOutcome() != PackageOutcome.OK) {
      switch (r.getOutcome()) {
        case FORBIDDEN ->
            add(
                FacesMessage.SEVERITY_WARN,
                "No permission",
                "Managing extraction templates needs the System Admin or Super Admin role.");
        case NOT_FOUND ->
            add(
                FacesMessage.SEVERITY_WARN,
                title,
                "That version no longer exists. Reload the list.");
        case CONFLICT ->
            add(
                FacesMessage.SEVERITY_WARN,
                title,
                "The version changed in the meantime (it may already be published). Reload and"
                    + " try again.");
        default -> add(FacesMessage.SEVERITY_ERROR, title, "Please try again.");
      }
      return false;
    }
    if (r.getProblems() != null && !r.getProblems().isEmpty()) {
      int shown = 0;
      for (final String p : r.getProblems()) {
        if (shown++ == 8) {
          add(FacesMessage.SEVERITY_ERROR, title, "And " + (r.getProblems().size() - 8) + " more.");
          break;
        }
        add(FacesMessage.SEVERITY_ERROR, title, p);
      }
      return false;
    }
    return true;
  }

  private void renumber() {
    for (int i = 0; i < fields.size(); i++) {
      fields.get(i).setOrder(i + 1);
    }
  }

  private String firstDataType() {
    final List<TemplateRows.Option> types = options.getDataTypes();
    return types == null || types.isEmpty() ? null : types.get(0).getCode();
  }

  private static void callback(final String name, final Object value) {
    PrimeFaces.current().ajax().addCallbackParam(name, value);
  }

  private static String trim(final String s) {
    if (s == null) {
      return null;
    }
    final String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static void add(
      final FacesMessage.Severity severity, final String summary, final String detail) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
  }
}
