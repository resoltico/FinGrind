package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical query-operation registry for the public FinGrind protocol catalog. */
final class ProtocolQueryOperations {
  private ProtocolQueryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationSupport.operation(
            OperationId.INSPECT_BOOK,
            OperationCategory.QUERY,
            "Inspect Book",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "Inspect one selected book for lifecycle state, format version, and compatibility.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                    .formatted(
                        OperationId.INSPECT_BOOK.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE))),
        ProtocolOperationSupport.operation(
            OperationId.LIST_ACCOUNTS,
            OperationCategory.QUERY,
            "List Accounts",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.optionalLimitSyntax(),
                ProtocolOptions.optionalOffsetSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "List one stable page of declared accounts in the selected book.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                    .formatted(
                        OperationId.LIST_ACCOUNTS.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.LIMIT,
                        ProtocolLimits.DEFAULT_PAGE_LIMIT))),
        ProtocolOperationSupport.operation(
            OperationId.GET_POSTING,
            OperationCategory.QUERY,
            "Get Posting",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.POSTING_ID + " <posting-id>"),
            ExecutionMode.JSON_ENVELOPE,
            "Return one committed posting by durable posting identifier.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 018f0e6d-7f7e-7b04-b93f-bc0b69f19d5b"
                    .formatted(
                        OperationId.GET_POSTING.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.POSTING_ID))),
        ProtocolOperationSupport.operation(
            OperationId.LIST_POSTINGS,
            OperationCategory.QUERY,
            "List Postings",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                "[" + ProtocolOptions.ACCOUNT_CODE + " <account-code>]",
                "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                ProtocolOptions.optionalLimitSyntax(),
                ProtocolOptions.optionalCursorSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            "List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 25"
                    .formatted(
                        OperationId.LIST_POSTINGS.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ACCOUNT_CODE,
                        ProtocolOptions.LIMIT))),
        ProtocolOperationSupport.operation(
            OperationId.ACCOUNT_BALANCE,
            OperationCategory.QUERY,
            "Account Balance",
            List.of(),
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.ACCOUNT_CODE + " <account-code>",
                "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]"),
            ExecutionMode.JSON_ENVELOPE,
            "Compute grouped per-currency balances for one declared account.",
            List.of(
                "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000"
                    .formatted(
                        OperationId.ACCOUNT_BALANCE.wireName(),
                        ProtocolOptions.BOOK_FILE,
                        ProtocolOptions.BOOK_KEY_FILE,
                        ProtocolOptions.ACCOUNT_CODE))));
  }
}
