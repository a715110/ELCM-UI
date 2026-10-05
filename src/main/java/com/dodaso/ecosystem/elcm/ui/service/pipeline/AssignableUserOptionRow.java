package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Backs uploadfilesdialog.xhtml's "Assign To" p:autoComplete
 * (UploadFilesBean.completeAssignableUsers()) -- replaces the old plain
 * List<String> of display names (UploadFilesService.findAssignableUsers()'s
 * former return type) so the picker can show each person's loginId
 * alongside their display name, per explicit request ("display the User ID
 * alongside the Name ... so that users can easily tell identical or similar
 * names apart").
 *
 * Deliberately a thin projection of IAMS's UserDirectoryDTO (loginId,
 * displayName, teamName) rather than reusing that DTO directly -- same
 * reasoning as ContractRecordOptionRow/StagedDocumentRow: this is a
 * lightweight, UI-local picker shape, not a shared cross-module type.
 * teamName is carried along so the autocomplete's itemLabel can show it as
 * a secondary disambiguator too (two "Brian Patel"s on different teams),
 * not just the loginId.
 *
 * STORED VALUE (REVISED 2026-10-04, assigneeId -> loginId): the autocomplete
 * submits this row's loginId (itemValue="#{au.loginId}"), so
 * UploadFilesBean.assignToUser -- and StagedDocument.assigneeId downstream --
 * now hold a unique identifier, not a display name. Two people who share a
 * display name are therefore distinct assignments, not just distinct picks.
 * elcm-service resolves the loginId back to a display name when it builds
 * the dashboard rows (see AssigneeDirectoryLookupService), and still
 * resolves pre-change rows that hold a display name via a fallback.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignableUserOptionRow implements Serializable {
    private String loginId;
    private String displayName;
    private String teamName;

    /**
     * ADDED 2026-10-04 -- computed here in Java, rather than as an EL
     * ternary/concat expression in uploadfilesdialog.xhtml's itemLabel,
     * mainly to avoid relying on EL 3.0's string-literal method invocation
     * (" - ".concat(...)) working on whatever Faces/EL version actually
     * ends up on the classpath -- same spirit as this row avoiding records
     * for EL-compatibility reasons elsewhere in this package. Produces
     * e.g. "Brian Patel (U1023) - Corporate Leasing Team", or just
     * "Brian Patel (U1023)" when teamName isn't on file.
     */
    public String getLabel() {
        final StringBuilder label = new StringBuilder();
        label.append(displayName).append(" (").append(loginId).append(')');
        if (teamName != null && !teamName.isBlank()) {
            label.append(" - ").append(teamName);
        }
        return label.toString();
    }
}
