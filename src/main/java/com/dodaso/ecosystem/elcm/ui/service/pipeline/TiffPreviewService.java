package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;

/**
 * Turns the pages of a TIFF into PNG images a browser can show (ADDED 2026-10-14). Browsers cannot
 * render TIFF, so the review pane shows one PNG per page. Uses the TIFF reader built into Java, so
 * no new library is needed. The bytes of the last few files are kept in memory, so a page request
 * does not download the whole file again from common-service.
 */
@Service
public class TiffPreviewService {

  /** More pages than this are not shown; the pane tells the reader to download the file. */
  public static final int MAX_PAGES = 100;

  private static final int CACHED_FILES = 3;

  private final Map<Long, byte[]> cache =
      new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Long, byte[]> eldest) {
          return size() > CACHED_FILES;
        }
      };

  /** Bytes of a file, loaded once through the given loader and then kept for a short while. */
  public synchronized byte[] bytes(final Long fileUploadId, final BytesLoader loader)
      throws Exception {
    byte[] bytes = cache.get(fileUploadId);
    if (bytes == null) {
      bytes = loader.load();
      cache.put(fileUploadId, bytes);
    }
    return bytes;
  }

  /** Number of pages, 0 when the bytes are not a readable TIFF. */
  public int pageCount(final byte[] tiff) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(tiff))) {
      final ImageReader reader = readerFor(in);
      if (reader == null) {
        return 0;
      }
      try {
        reader.setInput(in);
        return reader.getNumImages(true);
      } finally {
        reader.dispose();
      }
    }
  }

  /** One page (1 based) as PNG bytes, null when the page does not exist. */
  public byte[] pagePng(final byte[] tiff, final int page) throws IOException {
    if (page < 1 || page > MAX_PAGES) {
      return null;
    }
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(tiff))) {
      final ImageReader reader = readerFor(in);
      if (reader == null) {
        return null;
      }
      try {
        reader.setInput(in);
        if (page > reader.getNumImages(true)) {
          return null;
        }
        final BufferedImage image = reader.read(page - 1);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    }
  }

  private static ImageReader readerFor(final ImageInputStream in) {
    if (in == null) {
      return null;
    }
    final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
    return readers.hasNext() ? readers.next() : null;
  }

  /** Loads the bytes of a file; used when they are not cached yet. */
  @FunctionalInterface
  public interface BytesLoader {
    byte[] load() throws Exception;
  }
}
