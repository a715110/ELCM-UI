package com.dodaso.ecosystem.elcm.ui.helper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ADDED 2026-10-20. Builds a CSV file (RFC 4180) as UTF-8 bytes for the exports on the screens.
 * Rows end with CRLF, a field is quoted when it holds a comma, a quote or a line break, and the
 * file starts with a byte order mark so Excel reads accented text correctly.
 *
 * <p>Spreadsheet formula guard: a text that starts with = + - @ (or a tab or carriage return)
 * would be run as a formula by Excel and similar programs, and the values here come from uploaded
 * documents, so such a text gets a single quote in front. A plain number such as -1200.50 is left
 * alone.
 */
public final class CsvBuilder {

  private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
  private static final char[] FORMULA_STARTS = {'=', '+', '-', '@', '\t', '\r'};

  private final StringBuilder out = new StringBuilder();

  public CsvBuilder row(final List<String> cells) {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(field(cells.get(i)));
    }
    out.append("\r\n");
    return this;
  }

  public CsvBuilder row(final String... cells) {
    return row(List.of(cells));
  }

  /** The finished file: byte order mark plus the rows, in UTF-8. */
  public byte[] toBytes() {
    final byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
    final byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    final byte[] all = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, all, 0, bom.length);
    System.arraycopy(body, 0, all, bom.length, body.length);
    return all;
  }

  static String field(final String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String text = value;
    if (startsLikeFormula(text) && !NUMBER.matcher(text).matches()) {
      text = "'" + text;
    }
    if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
        || text.indexOf('\r') >= 0) {
      return '"' + text.replace("\"", "\"\"") + '"';
    }
    return text;
  }

  private static boolean startsLikeFormula(final String text) {
    final char first = text.charAt(0);
    for (final char c : FORMULA_STARTS) {
      if (first == c) {
        return true;
      }
    }
    return false;
  }
}
