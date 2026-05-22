package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.policy.BuiltInBookkeepingPolicyPacks;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.Objects;
import java.util.Optional;

/** Bookkeeping acceptance policy shared by preflight and durable commit paths. */
public final class PostingAcceptancePolicy {
  private static final PostingAcceptancePolicy CURRENT_KERNEL = new PostingAcceptancePolicy();
  private final PostingCurrencyAcceptancePolicy postingCurrencyAcceptancePolicy =
      new PostingCurrencyAcceptancePolicy();
  private final PostingClosedPeriodPolicy postingClosedPeriodPolicy =
      new PostingClosedPeriodPolicy();
  private final OpeningBalanceAcceptancePolicy openingBalanceAcceptancePolicy =
      new OpeningBalanceAcceptancePolicy();
  private final PostingAccountStatePolicy postingAccountStatePolicy =
      new PostingAccountStatePolicy();

  /** Returns the built-in posting-acceptance policy for the current FinGrind kernel. */
  public static PostingAcceptancePolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  /** Returns the first deterministic rejection for the supplied posting attempt, if any. */
  public Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!book.inspectBook().allowsInitializedWorkflow()) {
      return Optional.of(new BookkeepingPostingRejection.BookNotInitialized());
    }
    if (book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey()).isPresent()) {
      return Optional.of(new BookkeepingPostingRejection.DuplicateIdempotencyKey());
    }
    BookIdentity bookIdentity = initializedBookIdentity(book);
    Optional<BookkeepingPostingRejection> currencyRejection =
        postingCurrencyAcceptancePolicy.rejectionFor(postingRequest, bookIdentity);
    if (currencyRejection.isPresent()) {
      return currencyRejection;
    }
    Optional<BookkeepingPostingRejection> closedPeriodRejection =
        postingClosedPeriodPolicy.rejectionFor(postingRequest, book);
    if (closedPeriodRejection.isPresent()) {
      return closedPeriodRejection;
    }
    Optional<BookkeepingPostingRejection> openingBalanceWindowRejection =
        openingBalanceAcceptancePolicy.windowRejection(postingRequest, book);
    if (openingBalanceWindowRejection.isPresent()) {
      return openingBalanceWindowRejection;
    }
    Optional<BookkeepingPostingRejection> accountRejection =
        postingAccountStatePolicy.rejectionFor(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    Optional<BookkeepingPostingRejection> openingBalanceNominalRejection =
        openingBalanceAcceptancePolicy.nominalAccountRejection(postingRequest, book);
    if (openingBalanceNominalRejection.isPresent()) {
      return openingBalanceNominalRejection;
    }
    Optional<BookkeepingPostingRejection> closingEquityReservationRejection =
        new ClosingEquityReservationPolicy(
                BuiltInBookkeepingPolicyPacks.forBookIdentity(bookIdentity).closePolicy())
            .rejectionFor(postingRequest, bookIdentity, book);
    if (closingEquityReservationRejection.isPresent()) {
      return closingEquityReservationRejection;
    }
    return ReversalAcceptancePolicy.rejectionFor(postingRequest, book);
  }

  static BookIdentity initializedBookIdentity(PostingValidationStore book) {
    return switch (Objects.requireNonNull(book.inspectBook(), "inspectBook")) {
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized ->
          initialized.bookIdentity();
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException("Initialized posting validation requires one live book.");
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Existing _ ->
          throw new IllegalStateException("Initialized posting validation requires one live book.");
    };
  }

  static boolean isInternalSystemPosting(PostingRequestModel postingRequest) {
    return switch (postingRequest) {
      case PostingCommand command -> command.sourceChannel() == SourceChannel.SYSTEM;
      case PostingDraft draft -> draft.provenance().sourceChannel() == SourceChannel.SYSTEM;
    };
  }
}
