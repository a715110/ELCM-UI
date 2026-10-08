package com.dodaso.ecosystem.elcm.ui.service.pipeline;

/**
 * Result of a staged document delete call, mapped from elcm-service's status codes so the bean can
 * show a plain message without knowing HTTP: 204 DELETED, 404 NOT_FOUND (already deleted),
 * 403 FORBIDDEN (not the uploader), 409 CONFLICT (in a package or submitted), 400 TOO_LONG
 * (reason over 500 characters), anything else FAILED.
 */
public enum DeleteOutcome {
  DELETED,
  NOT_FOUND,
  FORBIDDEN,
  CONFLICT,
  TOO_LONG,
  FAILED
}
