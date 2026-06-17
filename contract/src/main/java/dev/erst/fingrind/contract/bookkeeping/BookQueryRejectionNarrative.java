package dev.erst.fingrind.contract.bookkeeping;

/** Query-rejection narrative catalog split out from the public rejection facade. */
final class BookQueryRejectionNarrative {
  private BookQueryRejectionNarrative() {}

  static String message(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + RejectionNarrative.openBookOperation()
              + ".";
      case BookQueryRejection.UnknownAccount unknownAccount ->
          "Account '%s' is not declared in this book."
              .formatted(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          "Posting '%s' does not exist in this book."
              .formatted(postingNotFound.postingId().value());
    };
  }
}
