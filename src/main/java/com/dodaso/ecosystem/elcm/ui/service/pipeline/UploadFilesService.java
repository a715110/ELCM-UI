package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import com.dodaso.ecosystem.elcm.ui.constant.DestinationChoiceEnum;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Business logic + data access for the Upload Files dialog (FC-1 Pipeline).
 * See StageDocumentService's class-level note -- same situation, same
 * reason: ALL data here is hardcoded placeholder data, no persistence
 * layer wired up yet.
 *
 * findAssignedWorkspace() and findAssignableUsers() back the two dropdowns
 * in the dialog. submitToPipeline(...) is the eventual "Add to Pipeline"
 * action -- deliberately not touching StageDocumentService yet (see
 * UploadFilesBean's class Javadoc for why: this doesn't take real files
 * as input yet, so there's nothing real to hand off to that service).
 */
@Service
public class UploadFilesService {

    public WorkspaceOptionRow findAssignedWorkspace(final String loginId) {
        // TODO: this should come from the user's onboarding-assigned
        //  workspace via IAMS/UserHelper, not be hardcoded to Retail for
        //  everyone -- placeholder until that lookup exists.
        return new WorkspaceOptionRow("RETAIL", "Retail", "Retail Portfolio");
    }

    public List<String> findAssignableUsers() {
        // TODO: real preparer/reviewer list, likely from IAMS. Placeholder
        //  names only, matching the ones already used elsewhere in the
        //  Stage Documents mock data (StageDocumentService).
        return List.of("L. Nguyen", "M. Okonkwo");
    }

    /**
     * Placeholder for the real "Add to Pipeline" action. Does not persist
     * or move anything yet -- see UploadFilesBean.addToPipeline() for why
     * this can't be finished until the file-upload transport question is
     * resolved (raw multipart vs p:fileUpload UploadedFile).
     */
    public void submitToPipeline(final DestinationChoiceEnum destinationChoice,
                                  final String workspaceCode,
                                  final String comments,
                                  final String assignToUser) {
        // Intentionally empty -- see class Javadoc.
    }
}
