package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One document inside a package, as listed in the package's Open dialog. Mirrors elcm-service. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageDocumentRow implements Serializable {
    private Long stagedDocumentId;
    private String fileName;
    private String roleCode;
    private String roleLabel;
    private String targetRecord;
}
