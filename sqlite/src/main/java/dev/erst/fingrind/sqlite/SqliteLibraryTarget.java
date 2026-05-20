package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.util.Objects;

/** Canonical SQLite runtime lookup target chosen from bundle, source checkout, or environment. */
record SqliteLibraryTarget(String mode, SqliteRuntimeProvenance provenance, String lookupTarget) {
  SqliteLibraryTarget {
    mode = requireText(mode, "mode");
    Objects.requireNonNull(provenance, "provenance");
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
