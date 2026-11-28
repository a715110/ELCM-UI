package com.dodaso.ecosystem.elcm.ui.service;

import com.dodaso.ecosystem.baseline.common.helper.TimezoneContextHelper;
import jakarta.faces.context.FacesContext;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * ADDED 2026-11-27. The one way ELCM screens show a time. The services send times in UTC (ISO text
 * or a LocalDateTime); this shows them in the viewer's own time zone with the zone abbreviation,
 * for example "2026-11-27 03:41 PM EST", so people in different states can compare. The zone is the
 * one the browser reported (cookie dodaso_tz); UTC is the last resort and then shows as "UTC".
 * Dates without a time (due dates, critical dates) are calendar days and do not use this class.
 */
public final class ShownTime {

  private static final String COOKIE = "dodaso_tz";
  private static final DateTimeFormatter COMPACT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a z", Locale.US);
  private static final DateTimeFormatter UTC_TEXT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.US);

  private ShownTime() {}

  /** An ISO time (UTC when it has no offset) in the viewer's zone, or "" when it is empty. */
  public static String text(final String iso) {
    return text(iso, "");
  }

  /** As {@link #text(String)}, with the given text for an empty value. */
  public static String text(final String iso, final String whenEmpty) {
    if (iso == null || iso.isBlank()) {
      return whenEmpty;
    }
    final Instant instant = parse(iso);
    if (instant == null) {
      final String t = iso.replace('T', ' ');
      return (t.length() > 16 ? t.substring(0, 16) : t) + " UTC";
    }
    return COMPACT.format(instant.atZone(zone()));
  }

  /** A UTC LocalDateTime in the viewer's zone, or the given text when it is null. */
  public static String textOf(final LocalDateTime utc, final String whenEmpty) {
    return utc == null ? whenEmpty : COMPACT.format(utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone()));
  }

  /** "2026-11-27 20:41 UTC" for exports, so a file means the same wherever it is opened. */
  public static String utc(final String iso) {
    if (iso == null || iso.isBlank()) {
      return "";
    }
    final Instant instant = parse(iso);
    return instant == null ? iso : UTC_TEXT.format(instant.atZone(ZoneOffset.UTC));
  }

  static Instant parse(final String iso) {
    final String s = iso.trim();
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (DateTimeException e) {
      // no offset: the services send UTC
    }
    try {
      return LocalDateTime.parse(s.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
    } catch (DateTimeException e) {
      return null;
    }
  }

  /** The zone to show times in for the current request. */
  static ZoneId zone() {
    final ZoneId fromCookie = parseZone(cookie());
    if (fromCookie != null) {
      return fromCookie;
    }
    try {
      final ZoneId fromContext = parseZone(TimezoneContextHelper.getUserTimezone());
      if (fromContext != null) {
        return fromContext;
      }
    } catch (RuntimeException e) {
      // fall through to UTC
    }
    return ZoneOffset.UTC;
  }

  private static ZoneId parseZone(final String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    try {
      return ZoneId.of(id.trim());
    } catch (DateTimeException e) {
      return null;
    }
  }

  private static String cookie() {
    try {
      final FacesContext faces = FacesContext.getCurrentInstance();
      if (faces == null) {
        return null;
      }
      final Map<String, Object> cookies = faces.getExternalContext().getRequestCookieMap();
      if (cookies.get(COOKIE) instanceof jakarta.servlet.http.Cookie c) {
        return URLDecoder.decode(c.getValue(), StandardCharsets.UTF_8);
      }
    } catch (RuntimeException e) {
      // no request context
    }
    return null;
  }
}
