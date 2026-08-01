package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.Objects;

/** Distinguishes a failed pre-link witness revalidation from an indeterminate link attempt. */
final class SqliteBookKeyFileFinalLinkAdmissionFailure extends IOException {
  private static final long serialVersionUID = 1L;

  SqliteBookKeyFileFinalLinkAdmissionFailure(IOException cause) {
    super(Objects.requireNonNull(cause, "cause").getMessage(), cause);
  }
}
