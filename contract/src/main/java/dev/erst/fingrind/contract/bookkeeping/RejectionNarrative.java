package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical plain-language rejection prose for public rejection contracts. */
public final class RejectionNarrative {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);

  private RejectionNarrative() {}

  static String openBookOperation() {
    return OPEN_BOOK_OPERATION;
  }

  /** Returns the canonical plain-language message for an administration rejection. */
  public static String message(BookAdministrationRejection rejection) {
    return BookAdministrationRejectionNarrative.message(
        Objects.requireNonNull(rejection, "rejection"));
  }

  /** Returns the canonical plain-language message for a maintenance rejection. */
  public static String message(BookMaintenanceRejection rejection) {
    return BookMaintenanceRejectionNarrative.message(
        Objects.requireNonNull(rejection, "rejection"));
  }

  /** Returns the canonical plain-language message for a query rejection. */
  public static String message(BookQueryRejection rejection) {
    return BookQueryRejectionNarrative.message(Objects.requireNonNull(rejection, "rejection"));
  }

  /** Returns the canonical plain-language message for a posting rejection. */
  public static String message(PostingRejection rejection) {
    return PostingRejectionNarrative.message(Objects.requireNonNull(rejection, "rejection"));
  }

  /** Returns the canonical operator repair hint for a posting rejection when one exists. */
  public static @Nullable String hint(PostingRejection rejection) {
    return PostingRejectionNarrative.hint(Objects.requireNonNull(rejection, "rejection"));
  }
}
