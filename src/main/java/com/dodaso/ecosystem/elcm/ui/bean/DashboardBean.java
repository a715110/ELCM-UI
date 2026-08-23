package com.dodaso.ecosystem.elcm.ui.bean;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Backing bean for dashboard.xhtml (FC-1 Pipeline dashboard).
 *
 * ALL data here is hardcoded placeholder/mock data, not from a real
 * service or repository -- there is no persistence layer in the project
 * yet (see the MINIMUM-JARS comment in pom.xml: Hibernate/Spring Data JPA
 * hasn't been added back in). Field names on StagedDocumentRow and
 * ContractPackageRow are deliberately aligned with the actual
 * staged_document / contract_package columns from
 * elcm_fc1_fc2_schema.sql, so wiring this up to a real service later is a
 * rename-free swap of the data source, not a redesign of the view model.
 *
 * Metric counts (uploading/validating/valid/submitted) similarly come from
 * hardcoded fields, not a COUNT(*) query -- replace with real aggregate
 * queries once the persistence layer exists.
 */
@Named
@ViewScoped
@Getter
@Setter
@Slf4j
public class DashboardBean implements Serializable {

    // ---- Top metrics (placeholder counts) ----
    @Getter
    private int uploadingCount = 0;
    private int validatingCount = 0;
    private int validCount = 3;
    private int submittedCount = 2;

    // ---- Stage Documents (placeholder rows) ----
    private List<StagedDocumentRow> stagedDocuments = List.of(
        new StagedDocumentRow("Retail-HQ-Lease-2026.pdf", "PDF", "Retail", "Unassigned", "L. Nguyen", "2026-06-12 09:14"),
        new StagedDocumentRow("Office-Tower-Amendment.pdf", "PDF", "Office", "Unassigned", "M. Okonkwo", "2026-06-12 09:10"),
        new StagedDocumentRow("Retail-Sublease-Notice.pdf", "PDF", "Retail", "Awaiting Assignment", "Auto-routed", "2026-06-12 09:18")
    );

    // ---- Contract Packages (placeholder rows) ----
    private List<ContractPackageRow> contractPackages = List.of(
        new ContractPackageRow("BATCH--LOCAL", "PKG-2026-002", 1, "CR-2026-0039 - Globex LLC", "Land", "Auto-routed", "1/1 roles", "Assembly")
    );

  /** Placeholder row for the Stage Documents table -- see class-level note on field alignment. */
  @Getter
  @AllArgsConstructor
  public static class StagedDocumentRow {
    String fileName;
    String type;
    String workspace;
    String record;
    String assignee;
    String uploadedAt;
  }

    /** Placeholder row for the Contract Packages table -- see class-level note on field alignment. */
    @Getter
    @AllArgsConstructor
    public static class ContractPackageRow {
      String batchId;
      String packageCode;
      int docCount;
      String targetRecord;
      String workspace;
      String assignee;
      String roles;
      String status;
    }
}