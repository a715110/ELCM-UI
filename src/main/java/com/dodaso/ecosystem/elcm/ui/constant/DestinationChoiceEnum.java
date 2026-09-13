package com.dodaso.ecosystem.elcm.ui.constant;

import lombok.Getter;

/**
 * The three destination cards in uploadfilesdialog.xhtml ("Where should
 * these documents go?"). Backs UploadFilesBean.destinationChoice.
 */
@Getter
public enum DestinationChoiceEnum {
    NEW_RECORD("new", "New Record"),
    EXISTING_RECORD("existing", "Existing Record"),
    NOT_SURE("unsure", "Not sure - leave instructions");

    /** Matches the data-dest attribute on each .ufd-dest-card in the xhtml,
     * so the client-side card-click JS and the server-side enum agree on
     * the same three values without duplicating strings in two places. */
    private final String cardDataAttr;
    private final String label;

    DestinationChoiceEnum(final String cardDataAttr, final String label) {
        this.cardDataAttr = cardDataAttr;
        this.label = label;
    }
}
