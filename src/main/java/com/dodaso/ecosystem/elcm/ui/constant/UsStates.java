package com.dodaso.ecosystem.elcm.ui.constant;

import java.io.Serializable;
import java.util.List;

/**
 * ADDED 2026-10-25 -- the State drop down of the New Record address (Upload Files dialog): the 50
 * states, the District of Columbia and the five US territories, each with its two letter USPS code.
 * The code is what is stored. Military mail codes (AA, AE, AP) are not listed.
 */
public final class UsStates {

  /** One entry of the drop down: code is stored, label is shown. */
  public static final class UsState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String label;

    public UsState(final String code, final String name) {
      this.code = code;
      this.label = name + " (" + code + ")";
    }

    public String getCode() {
      return code;
    }

    public String getLabel() {
      return label;
    }
  }

  public static final List<UsState> ALL = List.of(
        new UsState("AL", "Alabama"),
        new UsState("AK", "Alaska"),
        new UsState("AZ", "Arizona"),
        new UsState("AR", "Arkansas"),
        new UsState("CA", "California"),
        new UsState("CO", "Colorado"),
        new UsState("CT", "Connecticut"),
        new UsState("DE", "Delaware"),
        new UsState("DC", "District of Columbia"),
        new UsState("FL", "Florida"),
        new UsState("GA", "Georgia"),
        new UsState("HI", "Hawaii"),
        new UsState("ID", "Idaho"),
        new UsState("IL", "Illinois"),
        new UsState("IN", "Indiana"),
        new UsState("IA", "Iowa"),
        new UsState("KS", "Kansas"),
        new UsState("KY", "Kentucky"),
        new UsState("LA", "Louisiana"),
        new UsState("ME", "Maine"),
        new UsState("MD", "Maryland"),
        new UsState("MA", "Massachusetts"),
        new UsState("MI", "Michigan"),
        new UsState("MN", "Minnesota"),
        new UsState("MS", "Mississippi"),
        new UsState("MO", "Missouri"),
        new UsState("MT", "Montana"),
        new UsState("NE", "Nebraska"),
        new UsState("NV", "Nevada"),
        new UsState("NH", "New Hampshire"),
        new UsState("NJ", "New Jersey"),
        new UsState("NM", "New Mexico"),
        new UsState("NY", "New York"),
        new UsState("NC", "North Carolina"),
        new UsState("ND", "North Dakota"),
        new UsState("OH", "Ohio"),
        new UsState("OK", "Oklahoma"),
        new UsState("OR", "Oregon"),
        new UsState("PA", "Pennsylvania"),
        new UsState("RI", "Rhode Island"),
        new UsState("SC", "South Carolina"),
        new UsState("SD", "South Dakota"),
        new UsState("TN", "Tennessee"),
        new UsState("TX", "Texas"),
        new UsState("UT", "Utah"),
        new UsState("VT", "Vermont"),
        new UsState("VA", "Virginia"),
        new UsState("WA", "Washington"),
        new UsState("WV", "West Virginia"),
        new UsState("WI", "Wisconsin"),
        new UsState("WY", "Wyoming"),
        new UsState("AS", "American Samoa"),
        new UsState("GU", "Guam"),
        new UsState("MP", "Northern Mariana Islands"),
        new UsState("PR", "Puerto Rico"),
        new UsState("VI", "U.S. Virgin Islands"));

  private UsStates() {
  }
}
