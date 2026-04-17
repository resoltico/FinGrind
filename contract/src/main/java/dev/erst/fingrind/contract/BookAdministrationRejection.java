package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic book-administration refusals. */
public sealed interface BookAdministrationRejection
    permits BookAdministrationRejection.BookAlreadyInitialized,
        BookAdministrationRejection.BookNotInitialized,
        BookAdministrationRejection.BookContainsSchema,
        BookAdministrationRejection.NormalBalanceConflict {

  /** Returns the stable wire code for one book-administration rejection instance. */
  static String wireCode(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> "book-already-initialized";
      case BookAdministrationRejection.BookNotInitialized _ -> "book-not-initialized";
      case BookAdministrationRejection.BookContainsSchema _ -> "book-contains-schema";
      case BookAdministrationRejection.NormalBalanceConflict _ -> "account-normal-balance-conflict";
    };
  }

  /** Returns the canonical machine descriptors for every permitted administration rejection. */
  static List<MachineContract.RejectionDescriptor> descriptors() {
    return List.of(
        new MachineContract.RejectionDescriptor(
            "book-already-initialized",
            "Book initialization refused because the selected book is already initialized."),
        new MachineContract.RejectionDescriptor(
            "book-not-initialized",
            "Command refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + "."),
        new MachineContract.RejectionDescriptor(
            "book-contains-schema",
            "Book initialization refused because the selected SQLite file already contains schema objects."),
        new MachineContract.RejectionDescriptor(
            "account-normal-balance-conflict",
            "Account declaration refused because the requested normalBalance conflicts with the existing immutable value."));
  }

  /** Rejection for an explicit open-book request against an already initialized book. */
  record BookAlreadyInitialized() implements BookAdministrationRejection {}

  /** Rejection for commands that require an initialized book but found none. */
  record BookNotInitialized() implements BookAdministrationRejection {}

  /** Rejection for open-book against a pre-existing SQLite file that is not empty. */
  record BookContainsSchema() implements BookAdministrationRejection {}

  /** Rejection for redeclaring an account with a different immutable normal balance. */
  record NormalBalanceConflict(
      AccountCode accountCode,
      NormalBalance existingNormalBalance,
      NormalBalance requestedNormalBalance)
      implements BookAdministrationRejection {
    /** Validates the conflicting account definition. */
    public NormalBalanceConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingNormalBalance, "existingNormalBalance");
      Objects.requireNonNull(requestedNormalBalance, "requestedNormalBalance");
    }
  }
}
