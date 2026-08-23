package com.dodaso.ecosystem.elcm.ui.bean;

import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageRow;
import com.dodaso.ecosystem.elcm.ui.service.pipeline.ContractPackageService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Backing bean for the Contract Packages section of dashboard.xhtml. Split
 * out of the old DashboardBean per the bean-per-section refactor.
 *
 * Deliberately reloads from ContractPackageService on every init rather
 * than being told about changes by StageDocumentsBean directly -- per the
 * cross-bean-coordination discussion, this bean doesn't know
 * StageDocumentsBean exists at all. Once "Review & Group" is real and
 * triggers an AJAX update targeting this section, this bean just needs to
 * reload (e.g. via a PrimeFaces update targeting its table, or a
 * preRenderView reload) -- no direct coupling to add later.
 */
@Named
@ViewScoped
@Getter
@RequiredArgsConstructor
public class ContractPackagesBean implements Serializable {

    private final ContractPackageService contractPackageService;

    private List<ContractPackageRow> contractPackages;

    @PostConstruct
    void init() {
        contractPackages = contractPackageService.findPackages();
    }
}