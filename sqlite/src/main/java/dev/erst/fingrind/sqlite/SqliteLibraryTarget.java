package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Canonical SQLite runtime lookup target chosen from bundle or environment configuration. */
record SqliteLibraryTarget(String mode, String lookupTarget) {
  SqliteLibraryTarget {
    mode = requireText(mode, "mode");
    lookupTarget = requireText(lookupTarget, "lookupTarget");
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
