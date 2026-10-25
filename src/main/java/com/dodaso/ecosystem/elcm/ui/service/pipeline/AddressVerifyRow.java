package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADDED 2026-10-25 -- the answer of the address check. status is VERIFIED, SUGGESTED, NOT_FOUND or
 * UNAVAILABLE; line1 to zip hold the suggested standard form when status is SUGGESTED.
 */
@Getter
@Setter
@NoArgsConstructor
public class AddressVerifyRow implements Serializable {

  private String status;
  private String message;
  private String line1;
  private String line2;
  private String city;
  private String state;
  private String zip;
  private String zipPlus4;
  private String provider;

  public static AddressVerifyRow of(final String status, final String message) {
    final AddressVerifyRow row = new AddressVerifyRow();
    row.setStatus(status);
    row.setMessage(message);
    return row;
  }

  public boolean isSuggested() {
    return "SUGGESTED".equals(status);
  }

  /** "STATUS color" of the message line: green when found, amber otherwise. */
  public String getColor() {
    return "VERIFIED".equals(status) ? "#1b7f3b" : isSuggested() ? "#8a5a00" : "#b00020";
  }

  /** The suggested address on one line for display, such as "123 MAIN ST APT 4, NEW YORK, NY 10001". */
  public String getFormatted() {
    final StringBuilder sb = new StringBuilder();
    append(sb, line1);
    append(sb, line2);
    append(sb, city);
    final String stateZip = ((state == null ? "" : state) + " " + (zip == null ? "" : zip)).trim();
    append(sb, stateZip);
    return sb.toString();
  }

  private static void append(final StringBuilder sb, final String part) {
    if (part != null && !part.isBlank()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(part.trim());
    }
  }
}
