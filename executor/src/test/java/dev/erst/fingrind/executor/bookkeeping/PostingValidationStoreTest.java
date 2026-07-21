package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for default posting-validation-store helpers. */
class PostingValidationStoreTest {
  @Test
  void firstCommittedPosting_returnsEarliestCommittedPostingEvenWhenItIsOpeningBalance() {
    CommittedPosting openingPosting = posting("opening-1", "idem-1", PostingKind.OPENING_BALANCE);
    CommittedPosting standardPosting = posting("posting-2", "idem-2", PostingKind.STANDARD);
    PostingValidationStore store =
        new ValidationStoreDouble(List.of(openingPosting, standardPosting));

    assertEquals(Optional.of(openingPosting), store.firstCommittedPosting());
  }

  @Test
  void firstCommittedPosting_returnsEmptyWhenBookContainsNoCommittedPostings() {
    PostingValidationStore store = new ValidationStoreDouble(List.of());

    assertEquals(Optional.empty(), store.firstCommittedPosting());
  }

  private static CommittedPosting posting(
      String postingId, String idempotencyKey, PostingKind postingKind) {
    return new CommittedPosting(
        new PostingId(java.util.UUID.nameUUIDFromBytes(("fingrind-test-postingid:" + postingId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        postingKind,
        postingOriginKindFor(postingKind),
        accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static PostingOriginKind postingOriginKindFor(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> PostingOriginKind.REVERSAL;
      case OPENING_BALANCE -> PostingOriginKind.OPENING_POSITION;
      case INTERIM_RESULT_SWEEP -> PostingOriginKind.INTERIM_RESULT_SWEEP;
      case FISCAL_YEAR_CLOSE -> PostingOriginKind.FISCAL_YEAR_CLOSE;
    };
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private record ValidationStoreDouble(List<CommittedPosting> postings)
      implements PostingValidationStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(1);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<dev.erst.fingrind.executor.spi.StoredRequestPosting> findExistingPosting(
        IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return postings;
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
