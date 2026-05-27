package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;

/** Canonical public book-format facts shared across inspections, fixtures, and storage adapters. */
public final class BookFormatContract {
  /** Stable SQLite application_id stored in every FinGrind-managed book file. */
  public static final int APPLICATION_ID =
      ProtocolCatalog.runtime().protectedBookFormat().applicationId();

  /** Stable on-disk FinGrind book-format version supported by the current public line. */
  public static final int FORMAT_VERSION =
      ProtocolCatalog.runtime().protectedBookFormat().formatVersion();

  private BookFormatContract() {}
}
