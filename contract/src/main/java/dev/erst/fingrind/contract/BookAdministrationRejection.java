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
    return descriptorFor(rejection).code();
  }

  /** Returns the stable wire code for the missing-book administration rejection. */
  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  /** Returns the canonical machine descriptors for every permitted administration rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
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

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private static Descriptor descriptorFor(BookAdministrationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          Descriptor.BOOK_ALREADY_INITIALIZED;
      case BookAdministrationRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case BookAdministrationRejection.BookContainsSchema _ -> Descriptor.BOOK_CONTAINS_SCHEMA;
      case BookAdministrationRejection.NormalBalanceConflict _ ->
          Descriptor.NORMAL_BALANCE_CONFLICT;
    };
  }

  /** Canonical administration rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_ALREADY_INITIALIZED,
    BOOK_NOT_INITIALIZED,
    BOOK_CONTAINS_SCHEMA,
    NORMAL_BALANCE_CONFLICT;

    private String code() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED -> "book-already-initialized";
        case BOOK_NOT_INITIALIZED -> "administration-book-not-initialized";
        case BOOK_CONTAINS_SCHEMA -> "book-contains-schema";
        case NORMAL_BALANCE_CONFLICT -> "account-normal-balance-conflict";
      };
    }

    private String description() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED ->
            "Book initialization refused because the selected book is already initialized.";
        case BOOK_NOT_INITIALIZED ->
            "Administration command refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".";
        case BOOK_CONTAINS_SCHEMA ->
            "Book initialization refused because the selected SQLite file already contains schema objects.";
        case NORMAL_BALANCE_CONFLICT ->
            "Account declaration refused because the requested normalBalance conflicts with the existing immutable value.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case BOOK_ALREADY_INITIALIZED, BOOK_NOT_INITIALIZED, BOOK_CONTAINS_SCHEMA -> List.of();
        case NORMAL_BALANCE_CONFLICT ->
            List.of(
                detailField(
                    "accountCode", "Declared account code that already exists in the book."),
                detailField(
                    "existingNormalBalance",
                    "Immutable live normalBalance already stored for this account."),
                detailField(
                    "requestedNormalBalance",
                    "Conflicting normalBalance that the caller attempted to declare."));
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(
              BOOK_ALREADY_INITIALIZED,
              BOOK_NOT_INITIALIZED,
              BOOK_CONTAINS_SCHEMA,
              NORMAL_BALANCE_CONFLICT)
          .stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }
}
