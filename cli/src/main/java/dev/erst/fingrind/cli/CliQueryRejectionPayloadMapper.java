package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliQueryPlanRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;

/** Maps read-side query rejections into CLI rejected envelopes. */
final class CliQueryRejectionPayloadMapper {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String GET_POSTING_OPERATION =
      ProtocolCatalog.operationName(OperationId.GET_POSTING);
  private static final String LIST_ACCOUNTS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS);
  private static final String LIST_POSTINGS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_POSTINGS);

  private CliQueryRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope(BookQueryRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        rejectionHint(rejection),
        null,
        null,
        rejectionDetails(rejection),
        null,
        null,
        null,
        null);
  }

  private static String rejectionHint(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case BookQueryRejection.UnknownAccount _ ->
          "Use "
              + LIST_ACCOUNTS_OPERATION
              + " to confirm the account code, or declare the missing account before rerunning the query.";
      case BookQueryRejection.PostingNotFound _ ->
          "Use "
              + LIST_POSTINGS_OPERATION
              + " or "
              + GET_POSTING_OPERATION
              + " with a known posting id from this book before rerunning the query.";
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliQueryPlanRejectionJsonModels.UnknownAccountDetails(
              unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliQueryPlanRejectionJsonModels.PostingNotFoundDetails(
              postingNotFound.postingId().value());
    };
  }
}
