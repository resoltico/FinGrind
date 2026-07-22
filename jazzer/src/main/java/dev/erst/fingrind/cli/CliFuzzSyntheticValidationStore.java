package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
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
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Minimal validation-store shim for deterministic Jazzer translation helpers. */
final class CliFuzzSyntheticValidationStore {
  private CliFuzzSyntheticValidationStore() {}

  static PostingValidationStore validationStore(BookkeepingEntry entry, Instant declaredAt) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(declaredAt, "declaredAt");
    return new PostingValidationStore() {
      @Override
      public BookLifecycleInspection inspectBook() {
        return new BookLifecycleInspection.Initialized(
            1001, 1, 1, declaredAt, CliFuzzWorkflowFixtures.bookIdentity());
      }

      @Override
      public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
        return Optional.empty();
      }

      @Override
      public java.util.Map<AccountCode, RegisteredAccount> findAccounts(
          Set<AccountCode> accountCodes) {
        return java.util.Map.of();
      }

      @Override
      public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
          dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
        return CliFuzzSyntheticTaxRegistrations.lookupStore(entry, declaredAt)
            .findTaxRegistration(taxRegistrationId);
      }

      @Override
      public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
        return Optional.empty();
      }

      @Override
      public Optional<CommittedPosting> findPosting(PostingId postingId) {
        if (entry instanceof BookkeepingEntry.Reversal reversal
            && reversal.reversal().reference().priorPostingId().equals(postingId)) {
          return Optional.of(syntheticPriorPosting(reversal, declaredAt));
        }
        return Optional.empty();
      }

      @Override
      public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
        return Optional.empty();
      }

      @Override
      public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
        return List.of();
      }

      @Override
      public Optional<LocalDate> earliestPostingEffectiveDate() {
        return Optional.empty();
      }

      @Override
      public Optional<LocalDate> transferredThroughEffectiveDate() {
        return Optional.empty();
      }
    };
  }

  private static CommittedPosting syntheticPriorPosting(
      BookkeepingEntry.Reversal reversal, Instant declaredAt) {
    PostingId priorPostingId = reversal.reversal().reference().priorPostingId();
    JournalEntry priorJournalEntry =
        new JournalEntry(
            reversal.effectiveDate().minusDays(1),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "1.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "1.00"))));
    return new CommittedPosting(
        priorPostingId,
        priorJournalEntry,
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.DIRECT_JOURNAL,
        accountingEvidence(
            "synthetic-" + priorPostingId.value(), priorJournalEntry.effectiveDate()),
        new CommittedProvenance(
            requestProvenance(priorPostingId.value()), declaredAt, SourceChannel.CLI));
  }

  private static AccountingEvidence accountingEvidence(String token, LocalDate documentDate) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-" + token),
                new SourceDocumentType("operator-note"),
                documentDate)),
        List.of());
  }

  private static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new CommandId(token),
        new IdempotencyKey("idem-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("corr-" + token)));
  }
}
