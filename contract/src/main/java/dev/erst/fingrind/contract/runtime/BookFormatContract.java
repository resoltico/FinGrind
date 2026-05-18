package dev.erst.fingrind.contract.runtime;

/** Canonical public book-format facts shared across inspections, fixtures, and storage adapters. */
public final class BookFormatContract {
  /** Stable SQLite application_id stored in every FinGrind-managed book file. */
  public static final int APPLICATION_ID = 1_179_079_236; // "FGRD"

  /** Stable on-disk FinGrind book-format version supported by the current public line. */
  public static final int FORMAT_VERSION = 8;

  private BookFormatContract() {}
}
