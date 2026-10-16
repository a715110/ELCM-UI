package com.dodaso.ecosystem.elcm.ui.bean;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.elcm.ui.constant.PipelineMetricsControllerAPIEnum;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.StagedDocumentRow;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Backing bean for the metric cards on dashboard.xhtml.
 *
 * <p>REVISED 2026-10-16: five cards that follow the real flow (Staged documents, In assembly,
 * Extracting, Ready for review, Awaiting approval) replace the old Uploading/Validating/Valid/
 * Submitted cards. The first card counts documents, the others count packages. Failed extractions
 * and sent-back packages feed the red badge in the page header.
 *
 * <p>The page binds flat getters, and the small lines under each number are built here so the
 * page needs no logic. The response is read as a generic Map (no DTO binding). The JSON shape is
 * the service's PipelineOverview record, flat. Fails open: on any error the cards stay at 0 and
 * the dashboard still renders.
 *
 * <p>ADDED 2026-10-16: "Mine only" (documents the user uploaded, packages assigned to the user) and
 * the Assembly filter of the Contract Packages table. The card numbers come from the service with
 * the user's login ids; the two tables are filtered here by visibleDocuments and visiblePackages.
 * The red badge always counts everyone.
 */
@Named
@ViewScoped
@Getter
@Setter
@Slf4j
public class PipelineMetricsBean extends BaseBean {

    private int stagedDocuments;
    private int uploadedToday;
    private int inAssembly;
    private int sentBack;
    private int extracting;
    private int queued;
    private int reading;
    private int readyForReview;
    private int fieldsNeedReview;
    private int awaitingApproval;
    private int oldestWaitingDays;
    private int failedExtractions;
    private int alertSentBack;

    /** ADDED 2026-10-16: true when only the user's own work is shown. */
    private boolean mineOnly;

    /** ADDED 2026-10-16: empty for all packages, or ASSEMBLY to show only drafts. */
    private String packageStage = "";

    @Autowired
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private StageDocumentsBean stageDocumentsBean;

    @PostConstruct
    void init() {
        refresh();
    }

    /**
     * Re-reads the counts. Called on page load via init() and again from the dashboard's
     * refreshStagedDocuments remote command after an action that moves documents or packages.
     */
    @Override
    public void refresh() {
        try {
            final Map<String, Object> response = restServiceClient.get(
                ServiceDiscoveryEnum.elcm_service.getServiceDiscoveryName(),
                PipelineMetricsControllerAPIEnum.getMetrics.getEndPoint() + loginQuery(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
            if (response != null) {
                this.stagedDocuments = toInt(response.get("stagedDocuments"));
                this.uploadedToday = toInt(response.get("uploadedToday"));
                this.inAssembly = toInt(response.get("inAssembly"));
                this.sentBack = toInt(response.get("sentBack"));
                this.extracting = toInt(response.get("extracting"));
                this.queued = toInt(response.get("queued"));
                this.reading = toInt(response.get("reading"));
                this.readyForReview = toInt(response.get("readyForReview"));
                this.fieldsNeedReview = toInt(response.get("fieldsNeedReview"));
                this.awaitingApproval = toInt(response.get("awaitingApproval"));
                this.oldestWaitingDays = toInt(response.get("oldestWaitingDays"));
                this.failedExtractions = toInt(response.get("failedExtractions"));
                this.alertSentBack = toInt(response.get("alertSentBack"));
            }
        } catch (Exception e) {
            log.warn("Could not load pipeline metrics, showing zeros: {}", e.getMessage());
        }
    }

    /**
     * ADDED 2026-10-16: Mine only switched. Reloads the numbers and drops the document selection,
     * so a hidden row can never stay ticked for Create Document Set.
     */
    public void onMineChanged() {
        if (stageDocumentsBean != null) {
            stageDocumentsBean.setSelectedDocuments(null);
        }
        refresh();
    }

    /** ADDED 2026-10-16: In assembly card clicked, show only drafts in Contract Packages. */
    public void showAssembly() {
        packageStage = "ASSEMBLY";
    }

    /** ADDED 2026-10-16: back to all packages. */
    public void clearPackageStage() {
        packageStage = "";
    }

    public boolean isAssemblyFilter() {
        return "ASSEMBLY".equals(packageStage);
    }

    /** ADDED 2026-10-16: the Stage Documents rows to show, all or the ones the user uploaded. */
    public List<StagedDocumentRow> visibleDocuments(final List<StagedDocumentRow> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        if (!mineOnly) {
            return rows;
        }
        final Set<String> me = myIdentities();
        final List<StagedDocumentRow> out = new ArrayList<>();
        for (final StagedDocumentRow row : rows) {
            if (isMine(me, row.getUploadedBy())) {
                out.add(row);
            }
        }
        return out;
    }

    /** ADDED 2026-10-16: the Contract Packages rows to show, with Mine only and Assembly filters. */
    public List<ContractPackageRow> visiblePackages(final List<ContractPackageRow> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        if (!mineOnly && !isAssemblyFilter()) {
            return rows;
        }
        final Set<String> me = myIdentities();
        final List<ContractPackageRow> out = new ArrayList<>();
        for (final ContractPackageRow row : rows) {
            if (mineOnly && !isMine(me, row.getAssigneeLoginId())) {
                continue;
            }
            if (isAssemblyFilter() && !"ASSEMBLY".equals(row.getStatusCode())) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    /** ADDED 2026-10-16: "n of m" for a table header when a filter hides rows, else just n. */
    public String countText(final int shown, final int total) {
        return shown == total ? String.valueOf(shown) : shown + " of " + total;
    }

    /** The login ids that identify the user: the IAMS one and the authenticated one, lower case. */
    private Set<String> myIdentities() {
        final Set<String> ids = new LinkedHashSet<>();
        addIdentity(ids, getLoginId());
        addIdentity(ids, userHelper.getCurrentLoginId());
        return ids;
    }

    private static void addIdentity(final Set<String> ids, final String id) {
        if (id != null && !id.isBlank()) {
            ids.add(id.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static boolean isMine(final Set<String> me, final String login) {
        return login != null && me.contains(login.trim().toLowerCase(Locale.ROOT));
    }

    /** The query string for the service: empty for everyone, else the user's login ids. */
    private String loginQuery() {
        if (!mineOnly) {
            return "";
        }
        final StringBuilder query = new StringBuilder();
        for (final String id : myIdentities()) {
            query.append(query.length() == 0 ? "?" : "&")
                .append("login=").append(URLEncoder.encode(id, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    public String getStagedNote() {
        return "documents, " + uploadedToday + " uploaded today";
    }

    public String getAssemblyNote() {
        return "packages, " + sentBack + " sent back by a reviewer";
    }

    public String getExtractingNote() {
        return "packages, " + queued + " queued, " + reading + " reading";
    }

    public String getReadyNote() {
        return "packages, " + fieldsNeedReview + (fieldsNeedReview == 1 ? " field needs" : " fields need")
            + " review";
    }

    public String getAwaitingNote() {
        if (awaitingApproval == 0) {
            return "packages";
        }
        return "packages, oldest waiting "
            + (oldestWaitingDays == 0 ? "less than a day" : oldestWaitingDays + (oldestWaitingDays == 1 ? " day" : " days"));
    }

    /** True when something needs attention: a failed extraction or a package sent back. */
    public boolean isAlert() {
        return failedExtractions > 0 || alertSentBack > 0;
    }

    /** Text of the red badge, for example "1 extraction failed, 2 sent back". */
    public String getAlertText() {
        final StringBuilder text = new StringBuilder();
        if (failedExtractions > 0) {
            text.append(failedExtractions).append(failedExtractions == 1 ? " extraction failed" : " extractions failed");
        }
        if (alertSentBack > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(alertSentBack).append(" sent back");
        }
        return text.toString();
    }

    private static int toInt(final Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

}
