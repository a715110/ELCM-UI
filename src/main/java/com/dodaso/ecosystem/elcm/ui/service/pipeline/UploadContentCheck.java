package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.util.Locale;

/**
 * Checks a file that was just added in the Upload dialog (ADDED 2026-10-14): it must not be empty,
 * and its first bytes must match what its extension says. p:validateFile already limits the
 * extension, size and number of files; this catches an empty file and a file whose content does not
 * fit its name (a text file or a broken download called lease.pdf), which would otherwise only fail
 * minutes later in extraction. Extensions without a rule here pass.
 */
public final class UploadContentCheck {

  private static final int PDF_SCAN_BYTES = 1024;

  private UploadContentCheck() {}

  /** A reason for the user when the file is not acceptable; null when it is. */
  public static String problem(final String fileName, final byte[] content) {
    if (content == null || content.length == 0) {
      return "The file is empty.";
    }
    final String ext = extensionOf(fileName);
    final boolean ok =
        switch (ext) {
          case "pdf" -> pdfHeader(content);
          case "docx" -> startsWith(content, 0x50, 0x4B, 0x03, 0x04);
          case "doc" ->
              startsWith(content, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
                  || startsWith(content, 0x50, 0x4B, 0x03, 0x04)
                  || startsWith(content, '{', '\\', 'r', 't', 'f');
          case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
          case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
          case "tif", "tiff" -> tiffHeader(content);
          default -> true;
        };
    return ok
        ? null
        : "The content is not a valid ." + ext + " file. It may be damaged or renamed.";
  }

  static String extensionOf(final String fileName) {
    if (fileName == null) {
      return "";
    }
    final int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /** PDF readers accept up to 1024 bytes of junk before the %PDF- marker. */
  private static boolean pdfHeader(final byte[] c) {
    final int last = Math.min(c.length, PDF_SCAN_BYTES + 5) - 5;
    for (int i = 0; i <= last; i++) {
      if (c[i] == '%' && c[i + 1] == 'P' && c[i + 2] == 'D' && c[i + 3] == 'F' && c[i + 4] == '-') {
        return true;
      }
    }
    return false;
  }

  /** Classic and big TIFF, both byte orders. */
  private static boolean tiffHeader(final byte[] c) {
    return startsWith(c, 'I', 'I', 42, 0)
        || startsWith(c, 'M', 'M', 0, 42)
        || startsWith(c, 'I', 'I', 43, 0)
        || startsWith(c, 'M', 'M', 0, 43);
  }

  private static boolean startsWith(final byte[] c, final int... expected) {
    if (c.length < expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if ((c[i] & 0xFF) != expected[i]) {
        return false;
      }
    }
    return true;
  }
}
