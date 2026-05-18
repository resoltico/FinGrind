package dev.erst.fingrind.contract.runtime;

import java.util.Objects;

/** Machine-readable migration posture for the current protected-book format line. */
public record BookMigrationPolicy(
    BookMigrationPolicyMode mode,
    boolean inPlaceUpgradeSupported,
    boolean olderFormatsAccepted,
    boolean newerFormatsAccepted,
    int supportedBookFormatVersion) {
  /** Validates one book migration policy snapshot. */
  public BookMigrationPolicy {
    Objects.requireNonNull(mode, "mode");
    if (supportedBookFormatVersion < 1) {
      throw new IllegalArgumentException("supportedBookFormatVersion must be at least 1.");
    }
  }

  /** Returns the canonical migration posture for the active public book-format line. */
  public static BookMigrationPolicy current(int supportedBookFormatVersion) {
    return new BookMigrationPolicy(
        BookMigrationPolicyMode.HARD_BREAK_REJECT_OLDER_FORMATS,
        false,
        false,
        false,
        supportedBookFormatVersion);
  }
}
