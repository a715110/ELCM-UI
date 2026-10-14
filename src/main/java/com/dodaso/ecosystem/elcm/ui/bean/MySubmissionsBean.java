package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.MyCommittedDocumentRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.MySubmissionRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.MySubmissionsUiService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Backing bean of the My Submissions screen (ADDED 2026-10-14): what the signed-in user submitted,
 * filtered by Pending, In progress, Completed and Declined, and the documents of the submissions
 * that were applied (committed). Both lists are for the caller only; elcm-service decides who the
 * caller is. Filters work on the lists already loaded; Refresh reads them again.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class MySubmissionsBean implements Serializable {

  /** Filter value that shows every submission. */
  static final String ALL = "ALL";

  private static final String[][] FILTERS = {
    {ALL, "All"},
    {"PENDING", "Pending"},
    {"IN_PROGRESS", "In progress"},
    {"COMPLETED", "Completed"},
    {"DECLINED", "Declined"}
  };

  private final MySubmissionsUiService mySubmissionsUiService;

  private List<MySubmissionRow> all = new ArrayList<>();
  private List<MyCommittedDocumentRow> committed = new ArrayList<>();
  private boolean submissionsFailed;
  private boolean committedFailed;

  @Setter private String filter = ALL;

  @PostConstruct
  void init() {
    refresh();
  }

  public void refresh() {
    final List<MySubmissionRow> subs = mySubmissionsUiService.submissions();
    submissionsFailed = subs == null;
    all = subs == null ? new ArrayList<>() : subs;
    final List<MyCommittedDocumentRow> docs = mySubmissionsUiService.committed();
    committedFailed = docs == null;
    committed = docs == null ? new ArrayList<>() : docs;
  }

  /** The submissions of the current filter. */
  public List<MySubmissionRow> getRows() {
    final List<MySubmissionRow> out = new ArrayList<>();
    for (final MySubmissionRow r : all) {
      if (matches(r, filter)) {
        out.add(r);
      }
    }
    return out;
  }

  /** The filter buttons, each with the number of submissions it holds. */
  public List<SelectItem> getFilterItems() {
    final List<SelectItem> items = new ArrayList<>();
    for (final String[] f : FILTERS) {
      int n = 0;
      for (final MySubmissionRow r : all) {
        if (matches(r, f[0])) {
          n++;
        }
      }
      items.add(new SelectItem(f[0], f[1] + " (" + n + ")"));
    }
    return items;
  }

  private static boolean matches(final MySubmissionRow row, final String which) {
    return ALL.equals(which) || which != null && which.equals(row.getGroupCode());
  }
}
