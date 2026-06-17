package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.Objects;
import java.util.Optional;

/** Bookkeeping acceptance policy shared by preflight and durable commit paths. */
public final class PostingAcceptancePolicy {
  private static final PostingAcceptancePolicy CURRENT_KERNEL = new PostingAcceptancePolicy();
  private final PostingCurrencyAcceptancePolicy postingCurrencyAcceptancePolicy =
      new PostingCurrencyAcceptancePolicy();
  private final PostingTransferredPeriodResultPolicy postingTransferredPeriodResultPolicy =
      new PostingTransferredPeriodResultPolicy();
  private final OpenAccountingPositionAcceptancePolicy openAccountingPositionAcceptancePolicy =
      new OpenAccountingPositionAcceptancePolicy();
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
    if (!book.allowsInitializedWorkflow()) {
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
    Optional<BookkeepingPostingRejection> transferredPeriodResultRejection =
        postingTransferredPeriodResultPolicy.rejectionFor(postingRequest, book);
    if (transferredPeriodResultRejection.isPresent()) {
      return transferredPeriodResultRejection;
    }
    Optional<BookkeepingPostingRejection> openAccountingPositionWindowRejection =
        openAccountingPositionAcceptancePolicy.windowRejection(postingRequest, book);
    if (openAccountingPositionWindowRejection.isPresent()) {
      return openAccountingPositionWindowRejection;
    }
    Optional<BookkeepingPostingRejection> accountRejection =
        postingAccountStatePolicy.rejectionFor(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    Optional<BookkeepingPostingRejection> openAccountingPositionNominalRejection =
        openAccountingPositionAcceptancePolicy.nominalAccountRejection(postingRequest, book);
    if (openAccountingPositionNominalRejection.isPresent()) {
      return openAccountingPositionNominalRejection;
    }
    Optional<BookkeepingPostingRejection> resultHoldingReservationRejection =
        new ResultHoldingReservationPolicy(
                KernelAccountingRulesResolver.forBookIdentity(bookIdentity).resultTransferPolicy())
            .rejectionFor(postingRequest, bookIdentity, book);
    if (resultHoldingReservationRejection.isPresent()) {
      return resultHoldingReservationRejection;
    }
    return ReversalAcceptancePolicy.rejectionFor(postingRequest, book);
  }

  static BookIdentity initializedBookIdentity(PostingValidationStore book) {
    return Objects.requireNonNull(book, "book").requireInitializedBookIdentity();
  }

  static boolean isInternalSystemPosting(PostingRequestModel postingRequest) {
    return switch (postingRequest) {
      case PostingCommand command -> command.sourceChannel() == SourceChannel.SYSTEM;
      case PostingDraft draft -> draft.provenance().sourceChannel() == SourceChannel.SYSTEM;
    };
  }
}
