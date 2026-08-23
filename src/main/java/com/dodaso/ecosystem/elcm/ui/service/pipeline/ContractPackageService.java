package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Business logic + data access for contract packages (FC-1 Pipeline). See
 * StageDocumentService's class-level note -- same situation, same reason.
 */
@Service
public class ContractPackageService {

    public List<ContractPackageRow> findPackages() {
        return List.of(
            new ContractPackageRow("BATCH--LOCAL", "PKG-2026-002", 1, "CR-2026-0039 - Globex LLC", "Land", "Auto-routed", "1/1 roles", "Assembly")
        );
    }
}