package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        "Initialized posting validation requires one live book.", missingFailure.getMessage());
    assertEquals(
        "Initialized posting validation requires one live book.", existingFailure.getMessage());
  }

  @Test
  void isInternalSystemPosting_distinguishesSystemDraftsAndUnknownRequestShapes() {
    assertTrue(
        PostingAcceptancePolicy.isInternalSystemPosting(
            new PostingCommand(
                PostingKind.PERIOD_CLOSE,
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                PostingLineageModel.direct(),
                new RequestProvenance(
                    new ActorId("actor-1"),
                    ActorType.SYSTEM,
                    new CommandId("command-close"),
                    new IdempotencyKey("idem-system-command"),
                    new CausationId("cause-close"),
                    Optional.of(new CorrelationId("corr-close"))),
                SourceChannel.SYSTEM)));
    assertTrue(
        PostingAcceptancePolicy.isInternalSystemPosting(
            draft(SourceChannel.SYSTEM, "idem-system")));
    assertFalse(
        PostingAcceptancePolicy.isInternalSystemPosting(draft(SourceChannel.CLI, "idem-cli")));
    assertFalse(PostingAcceptancePolicy.isInternalSystemPosting(new UnknownPostingRequestModel()));
  }

  private static PostingDraft draft(SourceChannel sourceChannel, String idempotencyKey) {
    return new PostingDraft(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        PostingKind.PERIOD_CLOSE,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.SYSTEM,
                new CommandId("command-close"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-close"),
                Optional.of(new CorrelationId("corr-close"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            sourceChannel));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  /** Minimal request-model seam for the default internal-source-channel branch. */
  private static final class UnknownPostingRequestModel implements PostingRequestModel {
    @Override
    public JournalEntry journalEntry() {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
              line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
    }

    @Override
    public PostingLineageModel postingLineage() {
      return PostingLineageModel.direct();
    }

    @Override
    public PostingKind postingKind() {
      return PostingKind.PERIOD_CLOSE;
    }

    @Override
    public RequestProvenance requestProvenance() {
      return new RequestProvenance(
          new ActorId("actor-1"),
          ActorType.AGENT,
          new CommandId("command-unknown"),
          new IdempotencyKey("idem-unknown"),
          new CausationId("cause-unknown"),
          Optional.of(new CorrelationId("corr-unknown")));
    }
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
}
