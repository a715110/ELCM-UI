package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Business logic + data access for staged documents (FC-1 Pipeline).
 *
 * ALL data here is still hardcoded placeholder/mock data -- there is no
 * persistence layer in the project yet (see the MINIMUM-JARS comment in
 * pom.xml). This is the ONLY class that should need to change once
 * Hibernate/Spring Data JPA is added back in -- StageDocumentsBean and
 * dashboard.xhtml stay as they are.
 */
@Service
public class StageDocumentService {

    public List<StagedDocumentRow> findStaged(String workspace) {
        // workspace filtering not implemented yet -- parameter exists so the
        // bean layer and page can already call this the way they will once
        // filtering is real, without a second signature change later.
        return List.of(
            new StagedDocumentRow("Retail-HQ-Lease-2026.pdf", "PDF", "Retail", "Unassigned", "L. Nguyen", "2026-06-12 09:14"),
            new StagedDocumentRow("Office-Tower-Amendment.pdf", "PDF", "Office", "Unassigned", "M. Okonkwo", "2026-06-12 09:10"),
            new StagedDocumentRow("Retail-Sublease-Notice.pdf", "PDF", "Retail", "Awaiting Assignment", "Auto-routed", "2026-06-12 09:18")
        );
    }
}