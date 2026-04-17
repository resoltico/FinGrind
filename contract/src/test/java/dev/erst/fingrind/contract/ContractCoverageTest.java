package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolPlanKinds;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Targeted bundle-coverage tests for contract-only records and template metadata. */
class ContractCoverageTest {
  @Test
  void resultRecordsExposePayloadsAcrossAdministrationAndQuerySurfaces() {
    DeclaredAccount declaredAccount = declaredAccount("1000");
    PostingFact postingFact = postingFact("posting-1", "idem-1");
    AccountPage accountPage = new AccountPage(List.of(declaredAccount), 50, 0, false);
    PostingPage postingPage = new PostingPage(List.of(postingFact), 50, 0, false);
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            declaredAccount,
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new CurrencyBalance(
                    money("10.00"), money("0.00"), money("10.00"), NormalBalance.DEBIT)));

    assertEquals(
        Instant.parse("2026-04-07T10:15:30Z"),
        new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")).initializedAt());
    assertEquals(
        new BookAdministrationRejection.BookAlreadyInitialized(),
        new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized())
            .rejection());
    assertEquals(declaredAccount, new DeclareAccountResult.Declared(declaredAccount).account());
    assertEquals(
        new BookAdministrationRejection.BookNotInitialized(),
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized())
            .rejection());
    assertEquals(accountPage, new ListAccountsResult.Listed(accountPage).page());
    assertEquals(
        new BookQueryRejection.BookNotInitialized(),
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()).rejection());
    assertEquals(postingFact, new GetPostingResult.Found(postingFact).postingFact());
    assertEquals(
        new BookQueryRejection.PostingNotFound(new PostingId("posting-2")),
        new GetPostingResult.Rejected(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-2")))
            .rejection());
    assertEquals(snapshot, new AccountBalanceResult.Reported(snapshot).snapshot());
    assertEquals(
        new BookQueryRejection.UnknownAccount(new AccountCode("9999")),
        new AccountBalanceResult.Rejected(
                new BookQueryRejection.UnknownAccount(new AccountCode("9999")))
            .rejection());
    assertEquals(postingPage, new ListPostingsResult.Listed(postingPage).page());
    assertEquals(
        new BookQueryRejection.BookNotInitialized(),
        new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()).rejection());
  }

  @Test
  void machineContractPlanTemplatePublishesCanonicalAgentWorkflowMetadata() {
    MachineContract.LedgerPlanTemplateDescriptor template =
        MachineContract.planTemplate(
            Clock.fixed(Instant.parse("2026-04-17T09:10:11Z"), ZoneOffset.UTC));

    assertEquals("plan-1", template.planId());
    assertEquals("NORMAL", template.executionPolicy().journalLevel());
    assertEquals("HALT_ON_FIRST_FAILURE", template.executionPolicy().failurePolicy());
    assertEquals("ATOMIC", template.executionPolicy().transactionMode());
    assertEquals(5, template.steps().size());
    assertEquals("initialize-book", template.steps().get(0).stepId());
    assertEquals(OperationId.OPEN_BOOK.wireName(), template.steps().get(0).kind());
    assertEquals("declare-cash", template.steps().get(1).stepId());
    assertEquals(OperationId.DECLARE_ACCOUNT.wireName(), template.steps().get(1).kind());
    assertEquals("1000", template.steps().get(1).declareAccount().accountCode());
    assertEquals("Cash", template.steps().get(1).declareAccount().accountName());
    assertEquals("DEBIT", template.steps().get(1).declareAccount().normalBalance());
    assertEquals("declare-revenue", template.steps().get(2).stepId());
    assertEquals("2000", template.steps().get(2).declareAccount().accountCode());
    assertEquals("CREDIT", template.steps().get(2).declareAccount().normalBalance());
    assertEquals("post-journal", template.steps().get(3).stepId());
    assertEquals(OperationId.POST_ENTRY.wireName(), template.steps().get(3).kind());
    assertEquals("2026-04-17", template.steps().get(3).posting().effectiveDate());
    assertEquals("1000", template.steps().get(3).posting().lines().get(0).accountCode());
    assertEquals("2000", template.steps().get(3).posting().lines().get(1).accountCode());
    assertEquals("operator-1", template.steps().get(3).posting().provenance().actorId());
    assertEquals("assert-cash-balance", template.steps().get(4).stepId());
    assertEquals(ProtocolPlanKinds.ASSERT_ACCOUNT_BALANCE, template.steps().get(4).kind());
    assertEquals("1000", template.steps().get(4).assertion().accountCode());
    assertEquals("EUR", template.steps().get(4).assertion().currencyCode());
    assertEquals("10.00", template.steps().get(4).assertion().netAmount());
    assertEquals("DEBIT", template.steps().get(4).assertion().balanceSide());
  }

  @Test
  @SuppressWarnings("NullOptional")
  void inspectionAssertionsAndViolationsCoverOptionalAndPositiveBranches() {
    BookInspection inspection =
        new BookInspection(
            BookInspection.Status.INITIALIZED,
            true,
            true,
            false,
            0x46475244,
            1,
            1,
            "hard-break",
            Instant.parse("2026-04-07T10:15:30Z"));
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            declaredAccount("1000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new CurrencyBalance(
                    money("10.00"), money("0.00"), money("10.00"), NormalBalance.DEBIT)));
    LedgerAssertion.AccountActive active =
        new LedgerAssertion.AccountActive(new AccountCode("1000"));
    LedgerAssertion.PostingExists postingExists =
        new LedgerAssertion.PostingExists(new PostingId("posting-1"));
    LedgerAssertion.AccountBalanceEquals assertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            null,
            Optional.of(LocalDate.parse("2026-04-30")),
            money("10.00"),
            NormalBalance.DEBIT);
    LedgerAssertion.AccountBalanceEquals lowerBoundOnlyAssertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            null,
            money("10.00"),
            NormalBalance.DEBIT);
    PostingRejection.InactiveAccount inactive =
        new PostingRejection.InactiveAccount(new AccountCode("2000"));

    assertEquals(BookInspection.Status.INITIALIZED, inspection.status());
    assertEquals(Integer.valueOf(0x46475244), inspection.applicationId());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), snapshot.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), snapshot.effectiveDateTo());
    assertEquals(new AccountCode("1000"), active.accountCode());
    assertEquals(new PostingId("posting-1"), postingExists.postingId());
    assertTrue(assertion.query().effectiveDateFrom().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), assertion.query().effectiveDateTo());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")),
        lowerBoundOnlyAssertion.query().effectiveDateFrom());
    assertTrue(lowerBoundOnlyAssertion.query().effectiveDateTo().isEmpty());
    assertEquals(new AccountCode("2000"), inactive.accountCode());
  }

  @Test
  @SuppressWarnings("NullOptional")
  void journalRecordsRejectBlankIdentifiersAndBackwardsTimes() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalEntry succeededWithoutFailure =
        new LedgerJournalEntry(
            "post",
            OperationId.POST_ENTRY,
            LedgerStepStatus.SUCCEEDED,
            startedAt,
            finishedAt,
            List.of(),
            null);

    assertEquals(Optional.empty(), succeededWithoutFailure.failure());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry(
                " ",
                OperationId.POST_ENTRY,
                LedgerStepStatus.REJECTED,
                startedAt,
                finishedAt,
                List.of(),
                Optional.of(failure)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry(
                "post",
                OperationId.POST_ENTRY,
                LedgerStepStatus.REJECTED,
                finishedAt,
                startedAt,
                List.of(),
                Optional.of(failure)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                " ", LedgerPlanStatus.REJECTED, startedAt, finishedAt, List.of()));
  }

  private static DeclaredAccount declaredAccount(String accountCode) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        Optional.empty(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty(),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("10.00")),
            new JournalLine(
                new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("10.00"))));
  }

  private static Money money(String amount) {
    return new Money(new CurrencyCode("EUR"), new BigDecimal(amount));
  }
}
