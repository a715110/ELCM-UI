package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One document role for the role dropdown. Mirrors elcm-service. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRoleOptionRow implements Serializable {
    private String code;
    private String label;
}
