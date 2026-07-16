package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import java.util.Objects;
import java.util.function.Function;

/** Maps internal book-read outcomes into their published result variants. */
final class BookReadOutcomeMapper {
  private BookReadOutcomeMapper() {}

  static <V, R> R map(
      BookkeepingReadOutcome<V> outcome,
      Function<V, R> reportedMapper,
      Function<BookQueryRejection, R> rejectedMapper) {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(reportedMapper, "reportedMapper");
    Objects.requireNonNull(rejectedMapper, "rejectedMapper");
    if (outcome instanceof BookkeepingReadOutcome.Reported<V> reported) {
      return reportedMapper.apply(reported.value());
    }
    BookkeepingReadOutcome.Rejected<V> rejected = (BookkeepingReadOutcome.Rejected<V>) outcome;
    return rejectedMapper.apply(toPublishedRejection(rejected.rejection()));
  }

  static BookQueryRejection toPublishedRejection(BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingQueryRejection.BookNotInitialized _ ->
          new BookQueryRejection.BookNotInitialized();
      case BookkeepingQueryRejection.UnknownAccount unknownAccount ->
          new BookQueryRejection.UnknownAccount(unknownAccount.accountCode());
      case BookkeepingQueryRejection.PostingNotFound postingNotFound ->
          new BookQueryRejection.PostingNotFound(postingNotFound.postingId());
    };
  }
}
