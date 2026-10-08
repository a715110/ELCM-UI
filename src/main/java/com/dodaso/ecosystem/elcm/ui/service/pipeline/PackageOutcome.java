package com.dodaso.ecosystem.elcm.ui.service.pipeline;

/**
 * Result of a package write call (create, add documents, remove a document), mapped from
 * elcm-service's status codes so the beans can show a plain message without knowing HTTP:
 * OK for success, NOT_FOUND (404, a document or package no longer exists), CONFLICT (409, a
 * document is already in a package or submitted, or the package is no longer a draft),
 * WORKSPACE_MISMATCH (422, documents from more than one workspace), INVALID (400), and FAILED
 * for anything else.
 */
public enum PackageOutcome {
  OK,
  NOT_FOUND,
  CONFLICT,
  WORKSPACE_MISMATCH,
  INVALID,
  FAILED
}
