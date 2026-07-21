package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.ExecutorAccountingTestSupport;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct package-level coverage for internal posting-acceptance helpers. */
class PostingAcceptancePolicyInternalTest {
  @Test
  void initializedBookIdentity_rejectsMissingAndExistingSnapshots() {
    IllegalStateException missingFailure =
        assertThrows(
            IllegalStateException.class,
            () -> PostingAcceptancePolicy.initializedBookIdentity(new MissingInspectionBook()));
    IllegalStateException existingFailure =
        assertThrows(
            IllegalStateException.class,
            () -> PostingAcceptancePolicy.initializedBookIdentity(new ExistingInspectionBook()));

    assertEquals(
        "Book identity is unavailable because the book is missing.", missingFailure.getMessage());
    assertEquals(
        "Book identity is unavailable for non-initialized book status blank-sqlite.",
        existingFailure.getMessage());
  }

  @Test
  void isInternalSystemPosting_distinguishes_system_and_non_system_postings() {
    assertTrue(
        PostingAcceptancePolicy.isInternalSystemPosting(
            new PostingCommand(
                PostingKind.INTERIM_RESULT_SWEEP,
                dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                PostingLineageModel.direct(),
                generatedEvidence("interim-result-sweep-command", "interim-result-sweep-plan"),
                new RequestProvenance(
                    new CommandId("c01435bb-ec91-3b81-90d6-13c11a761bd7"),
                    new IdempotencyKey("idem-system-command"),
                    new CausationId("cause-close"),
                    Optional.of(new CorrelationId("corr-close"))),
                SourceChannel.SYSTEM)));
    assertFalse(
        PostingAcceptancePolicy.isInternalSystemPosting(
            new PostingCommand(
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("6100", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("1000", JournalLine.EntrySide.CREDIT, "10.00"))),
                PostingLineageModel.direct(),
                generatedEvidence("operator-correction", "operator-correction"),
                new RequestProvenance(
                    new CommandId("2b8de830-e1a1-30c3-b574-e79aa3810de4"),
                    new IdempotencyKey("idem-command-cli"),
                    new CausationId("cause-correction"),
                    Optional.empty()),
                SourceChannel.CLI)));
    assertTrue(
        PostingAcceptancePolicy.isInternalSystemPosting(
            draft(SourceChannel.SYSTEM, "idem-system")));
    assertFalse(
        PostingAcceptancePolicy.isInternalSystemPosting(draft(SourceChannel.CLI, "idem-cli")));
  }

  @Test
  void rejectionFor_returnsEmptyForAcceptedAndReplay_andPresentForRejections() {
    PostingAcceptancePolicy policy = PostingAcceptancePolicy.currentKernel();
    PostingCommand acceptedCommand =
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
            PostingLineageModel.direct(),
            generatedEvidence("accepted", "operator-note"),
            new RequestProvenance(
                new CommandId("6a2902df-21a8-3aa0-934a-3c34a30e63ea"),
                new IdempotencyKey("idem-accepted"),
                new CausationId("cause-accepted"),
                Optional.empty()),
            SourceChannel.CLI);
    RequestFingerprint acceptedFingerprint = RequestFingerprintOwner.fingerprint(acceptedCommand);
    PostingDraft acceptedDraft =
        RequestFingerprintTestSupport.fingerprintedDraft(
            acceptedCommand.journalEntry(),
            acceptedCommand.postingLineage(),
            acceptedCommand.postingKind(),
            acceptedCommand.postingOriginKind(),
            acceptedCommand.evidence(),
            new CommittedProvenance(
                acceptedCommand.requestProvenance(),
                Instant.parse("2026-04-07T10:15:30Z"),
                SourceChannel.CLI));

    assertEquals(
        Optional.empty(), policy.rejectionFor(acceptedCommand, new AcceptedInspectionBook()));
    assertEquals(
        Optional.empty(), policy.rejectionFor(acceptedDraft, new AcceptedInspectionBook()));
    assertEquals(
        Optional.empty(),
        policy.rejectionFor(
            acceptedCommand,
            new ReplayInspectionBook(postingFact("posting-replay"), acceptedFingerprint)));
    assertEquals(
        Optional.of(new BookkeepingPostingRejection.BookNotInitialized()),
        policy.rejectionFor(acceptedCommand, new MissingInspectionBook()));
  }

  private static PostingDraft draft(SourceChannel sourceChannel, String idempotencyKey) {
    return new PostingDraft(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        PostingKind.INTERIM_RESULT_SWEEP,
        dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
        generatedEvidence(idempotencyKey, "interim-result-sweep-plan"),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("c01435bb-ec91-3b81-90d6-13c11a761bd7"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-close"),
                Optional.of(new CorrelationId("corr-close"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            sourceChannel));
  }

  private static CommittedPosting postingFact(String postingId) {
    return new CommittedPosting(
        new dev.erst.fingrind.core.PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        generatedEvidence("stored-" + postingId, "operator-note"),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  /** Validation-book double whose inspection reports one missing-book lifecycle snapshot. */
  private static final class MissingInspectionBook extends EmptyValidationStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(2);
    }
  }

  /** Validation-book double whose inspection reports one existing non-initialized snapshot. */
  private static final class ExistingInspectionBook extends EmptyValidationStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Existing(
          BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 2);
    }
  }

  /** Validation-book double whose inspection reports one initialized snapshot. */
  private static class AcceptedInspectionBook extends EmptyValidationStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return ExecutorAccountingTestSupport.initializedLifecycleInspection(
          1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"));
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return switch (accountCode.value()) {
        case "1000" ->
            Optional.of(
                ExecutorAccountingTestSupport.registeredAccount(
                    accountCode,
                    new dev.erst.fingrind.core.AccountName("Cash"),
                    dev.erst.fingrind.core.AccountType.ASSET,
                    dev.erst.fingrind.core.NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T10:15:30Z")));
        case "4000" ->
            Optional.of(
                ExecutorAccountingTestSupport.registeredAccount(
                    accountCode,
                    new dev.erst.fingrind.core.AccountName("Revenue"),
                    dev.erst.fingrind.core.AccountType.REVENUE,
                    dev.erst.fingrind.core.NormalBalance.CREDIT,
                    true,
                    Instant.parse("2026-04-07T10:15:30Z")));
        default -> Optional.empty();
      };
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(
        java.util.Set<AccountCode> accountCodes) {
      return accountCodes.stream()
          .map(accountCode -> Map.entry(accountCode, findAccount(accountCode)))
          .filter(entry -> entry.getValue().isPresent())
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey, entry -> entry.getValue().orElseThrow()));
    }
  }

  /** Validation-book double whose inspection reports one initialized snapshot with a replay hit. */
  private static final class ReplayInspectionBook extends AcceptedInspectionBook {
    private final StoredRequestPosting storedRequestPosting;

    private ReplayInspectionBook(
        CommittedPosting postingFact, RequestFingerprint requestFingerprint) {
      this.storedRequestPosting = new StoredRequestPosting(postingFact, requestFingerprint);
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.of(storedRequestPosting);
    }
  }
}
