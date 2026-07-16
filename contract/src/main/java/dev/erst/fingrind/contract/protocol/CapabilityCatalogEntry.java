package dev.erst.fingrind.contract.protocol;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One canonical public-scope capability fact and its operative boundary, where applicable. */
public record CapabilityCatalogEntry(
    String id, String scopeStatement, CapabilityStatus status, @Nullable String operativeBoundary) {
  /** Validates one published scope claim and its operative boundary, where applicable. */
  public CapabilityCatalogEntry {
    id = Objects.requireNonNull(id, "id").strip();
    scopeStatement = Objects.requireNonNull(scopeStatement, "scopeStatement").strip();
    Objects.requireNonNull(status, "status");
    if (id.isEmpty()) {
      throw new IllegalArgumentException("id must not be blank.");
    }
    if (scopeStatement.isEmpty()) {
      throw new IllegalArgumentException("scopeStatement must not be blank.");
    }
    if (status == CapabilityStatus.PARTIAL) {
      operativeBoundary = Objects.requireNonNull(operativeBoundary, "operativeBoundary").strip();
      if (operativeBoundary.isEmpty()) {
        throw new IllegalArgumentException(
            "operativeBoundary must not be blank for a partial capability.");
      }
    } else if (operativeBoundary != null) {
      throw new IllegalArgumentException("operativeBoundary is reserved for a partial capability.");
    }
  }
}
