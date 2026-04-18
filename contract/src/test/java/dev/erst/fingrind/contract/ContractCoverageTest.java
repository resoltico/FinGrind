package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
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
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
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
    PostingPage postingPage = new PostingPage(List.of(postingFact), 50, Optional.empty());
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
    ContractTemplates.LedgerPlanTemplateDescriptor template =
        MachineContract.planTemplate(
            Clock.fixed(Instant.parse("2026-04-17T09:10:11Z"), ZoneOffset.UTC));

    assertEquals("plan-1", template.planId());
    assertEquals(5, template.steps().size());
    assertEquals("initialize-book", template.steps().get(0).stepId());
    assertEquals(LedgerStepKind.OPEN_BOOK.wireValue(), template.steps().get(0).kind());
    assertEquals("declare-cash", template.steps().get(1).stepId());
    assertEquals(LedgerStepKind.DECLARE_ACCOUNT.wireValue(), template.steps().get(1).kind());
    assertEquals("1000", template.steps().get(1).declareAccount().accountCode());
    assertEquals("Cash", template.steps().get(1).declareAccount().accountName());
    assertEquals("DEBIT", template.steps().get(1).declareAccount().normalBalance());
    assertEquals("declare-revenue", template.steps().get(2).stepId());
    assertEquals("2000", template.steps().get(2).declareAccount().accountCode());
    assertEquals("CREDIT", template.steps().get(2).declareAccount().normalBalance());
    assertEquals("post-journal", template.steps().get(3).stepId());
    assertEquals(LedgerStepKind.POST_ENTRY.wireValue(), template.steps().get(3).kind());
    assertEquals("2026-04-17", template.steps().get(3).posting().effectiveDate());
    assertEquals("1000", template.steps().get(3).posting().lines().get(0).accountCode());
    assertEquals("2000", template.steps().get(3).posting().lines().get(1).accountCode());
    assertEquals("operator-1", template.steps().get(3).posting().provenance().actorId());
    assertEquals("assert-cash-balance", template.steps().get(4).stepId());
    assertEquals(LedgerStepKind.ASSERT.wireValue(), template.steps().get(4).kind());
    assertEquals(
        LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS.wireValue(),
        template.steps().get(4).assertion().kind());
    assertEquals("1000", template.steps().get(4).assertion().accountCode());
    assertEquals("EUR", template.steps().get(4).assertion().currencyCode());
    assertEquals("10.00", template.steps().get(4).assertion().netAmount());
    assertEquals("DEBIT", template.steps().get(4).assertion().balanceSide());

    ContractDiscovery.CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ContractDiscovery.ApplicationIdentity("FinGrind", "0.17.0", "test"),
            ContractFixtures.environmentDescriptor(),
            Instant.parse("2026-04-17T09:10:11Z"));
    assertEquals("atomic", capabilities.planExecution().transactionMode());
    assertEquals("halt-on-first-failure", capabilities.planExecution().failurePolicy());
    assertTrue(
        capabilities
            .requestShapes()
            .ledgerPlan()
            .assertionKinds()
            .contains(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS.wireValue()));
    assertEquals(
        LedgerStepKind.ASSERT.wireValue(),
        capabilities.requestShapes().ledgerPlan().assertStepKind());
  }

  @Test
  @SuppressWarnings("NullOptional")
  void inspectionAssertionsAndViolationsCoverOptionalAndPositiveBranches() {
    BookInspection inspection =
        new BookInspection.Initialized(
            0x46475244,
            1,
            1,
            BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
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
            Optional.empty(),
            Optional.of(LocalDate.parse("2026-04-30")),
            money("10.00"),
            NormalBalance.DEBIT);
    LedgerAssertion.AccountBalanceEquals lowerBoundOnlyAssertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.empty(),
            money("10.00"),
            NormalBalance.DEBIT);
    PostingRejection.InactiveAccount inactive =
        new PostingRejection.InactiveAccount(new AccountCode("2000"));

    assertEquals(BookInspection.Status.INITIALIZED, inspection.status());
    assertEquals(0x46475244, ((BookInspection.Initialized) inspection).applicationId());
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
  void inspectionVariantsExposeTheirStructuralStateAndRejectInvalidMetadata() {
    List<BookInspection> inspections =
        List.of(
            new BookInspection.Missing(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                0x46475244,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Initialized(
                0x46475244,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                Instant.parse("2026-04-07T10:15:30Z")),
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                0x12345678,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                0x46475244,
                2,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                0x46475244,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    List<BookInspection.Status> statuses =
        List.of(
            BookInspection.Status.MISSING,
            BookInspection.Status.BLANK_SQLITE,
            BookInspection.Status.INITIALIZED,
            BookInspection.Status.FOREIGN_SQLITE,
            BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
            BookInspection.Status.INCOMPLETE_FINGRIND);
    List<Boolean> initialized = List.of(false, false, true, false, false, false);
    List<Boolean> compatibleWithCurrentBinary = List.of(false, false, true, false, false, false);
    List<Boolean> canInitializeWithOpenBook = List.of(true, true, false, false, false, false);

    for (int index = 0; index < inspections.size(); index++) {
      assertEquals(statuses.get(index), inspections.get(index).status());
      assertEquals(initialized.get(index), inspections.get(index).initialized());
      assertEquals(
          compatibleWithCurrentBinary.get(index),
          inspections.get(index).compatibleWithCurrentBinary());
      assertEquals(
          canInitializeWithOpenBook.get(index), inspections.get(index).canInitializeWithOpenBook());
      assertEquals(1, inspections.get(index).supportedBookFormatVersion());
      assertEquals(
          BookMigrationPolicy.SEQUENTIAL_IN_PLACE, inspections.get(index).migrationPolicy());
    }

    assertEquals(
        BookInspection.Status.BLANK_SQLITE, BookInspection.Status.fromWireValue("blank-sqlite"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new BookInspection.Missing(0, BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                0x46475244,
                -1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                -1,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookInspection.Existing(
                BookInspection.Status.INITIALIZED,
                0x46475244,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    assertThrows(NullPointerException.class, () -> new BookInspection.Missing(1, null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookInspection.Initialized(
                0x46475244, 1, 1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE, null));
  }

  @Test
  void dateRangesLineagesPostingShapesAndPlanResultsExposeDerivedAccessors() {
    ReversalReference reversalReference = new ReversalReference(new PostingId("posting-1"));
    EffectiveDateRange unbounded = EffectiveDateRange.unbounded();
    EffectiveDateRange from =
        EffectiveDateRange.of(Optional.of(LocalDate.parse("2026-04-01")), Optional.empty());
    EffectiveDateRange to =
        EffectiveDateRange.of(Optional.empty(), Optional.of(LocalDate.parse("2026-04-30")));
    EffectiveDateRange bounded =
        EffectiveDateRange.of(
            Optional.of(LocalDate.parse("2026-04-01")), Optional.of(LocalDate.parse("2026-04-30")));
    PostingLineage direct = PostingLineage.direct();
    ReversalReason reversalReason = new ReversalReason("operator reversal");
    PostingLineage reversal = PostingLineage.reversal(reversalReference, reversalReason);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            Optional.empty());
    JournalEntry requestJournalEntry = journalEntry();
    PostEntryCommand command =
        new PostEntryCommand(requestJournalEntry, reversal, requestProvenance, SourceChannel.CLI);
    PostingFact postingFact =
        new PostingFact(
            new PostingId("posting-1"),
            requestJournalEntry,
            reversal,
            new CommittedProvenance(
                requestProvenance, Instant.parse("2026-04-07T10:15:30Z"), SourceChannel.CLI));
    PostingRequest postingRequest =
        new PostingRequest() {
          @Override
          public JournalEntry journalEntry() {
            return requestJournalEntry;
          }

          @Override
          public PostingLineage postingLineage() {
            return reversal;
          }

          @Override
          public RequestProvenance requestProvenance() {
            return requestProvenance;
          }
        };
    LedgerAssertion.AccountBalanceEquals assertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"), bounded, money("10.00"), NormalBalance.DEBIT);
    PostEntryResult.CommitRejected commitRejected =
        new PostEntryResult.CommitRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey());
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerPlanResult succeededResult =
        new LedgerPlanResult.Succeeded(
            planId("plan-succeeded"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.Succeeded(
                        stepId("open"),
                        LedgerStepKind.OPEN_BOOK,
                        null,
                        startedAt,
                        finishedAt,
                        List.of()))));
    LedgerPlanResult rejectedResult =
        new LedgerPlanResult.Rejected(
            planId("plan-rejected"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.Rejected(
                        stepId("post"),
                        LedgerStepKind.POST_ENTRY,
                        null,
                        startedAt,
                        finishedAt,
                        List.of(),
                        new LedgerStepFailure("rejected", "Rejected.", List.of())))));
    LedgerPlanResult assertionFailedResult =
        new LedgerPlanResult.AssertionFailed(
            planId("plan-assertion"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.AssertionFailed(
                        stepId("assert"),
                        LedgerStepKind.ASSERT,
                        LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                        startedAt,
                        finishedAt,
                        List.of(),
                        new LedgerStepFailure("assertion-failed", "Mismatch.", List.of())))));

    assertEquals(Optional.empty(), unbounded.effectiveDateFrom());
    assertTrue(unbounded.contains(LocalDate.parse("2026-04-15")));
    assertTrue(from.contains(LocalDate.parse("2026-04-15")));
    assertFalse(from.contains(LocalDate.parse("2026-03-31")));
    assertTrue(to.contains(LocalDate.parse("2026-04-15")));
    assertFalse(to.contains(LocalDate.parse("2026-05-01")));
    assertTrue(bounded.contains(LocalDate.parse("2026-04-15")));
    assertFalse(bounded.contains(LocalDate.parse("2026-05-01")));
    assertEquals(List.of("unbounded", "from", "to", "bounded"), EffectiveDateRange.variantNames());

    assertFalse(direct.isReversal());
    assertEquals(Optional.empty(), direct.reversalReference());
    assertEquals(Optional.empty(), direct.reversalReason());
    assertTrue(reversal.isReversal());
    assertEquals(Optional.of(reversalReference), reversal.reversalReference());
    assertEquals(Optional.of(reversalReason), reversal.reversalReason());

    assertEquals(Optional.of(reversalReference), command.reversalReference());
    assertEquals(Optional.of(reversalReason), command.reversalReason());
    assertEquals(Optional.of(reversalReference), ((PostingRequest) command).reversalReference());
    assertEquals(Optional.of(reversalReason), ((PostingRequest) command).reversalReason());
    assertEquals(Optional.of(reversalReference), postingRequest.reversalReference());
    assertEquals(Optional.of(reversalReason), postingRequest.reversalReason());
    assertEquals(Optional.of(reversalReference), postingFact.reversalReference());
    assertEquals(Optional.of(reversalReason), postingFact.reversalReason());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), assertion.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), assertion.effectiveDateTo());
    assertEquals(bounded, assertion.query().effectiveDateRange());
    assertEquals(new IdempotencyKey("idem-1"), commitRejected.requestIdempotencyKey());

    assertEquals(LedgerPlanStatus.SUCCEEDED, succeededResult.status());
    assertEquals(LedgerPlanStatus.REJECTED, rejectedResult.status());
    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, assertionFailedResult.status());
  }

  @Test
  void protocolVocabularyHelpersParseWireValuesAndRejectUnknownValues() {
    assertEquals(
        LedgerStepKind.POST_ENTRY,
        LedgerStepKind.fromWireValue(LedgerStepKind.POST_ENTRY.wireValue()));
    assertEquals(
        LedgerAssertionKind.POSTING_EXISTS,
        LedgerAssertionKind.fromWireValue(LedgerAssertionKind.POSTING_EXISTS.wireValue()));
    assertEquals(
        BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
        BookMigrationPolicy.fromWireValue("sequential-in-place"));
    assertEquals(List.of("sequential-in-place"), BookMigrationPolicy.wireValues());
    assertEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE.wireValue());
    assertEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE.toString());
    assertNotEquals(null, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertNotEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertNotEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, null);
    assertNotEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, "sequential-in-place");
    assertEquals(
        List.of(
            "assert-account-declared",
            "assert-account-active",
            "assert-posting-exists",
            "assert-account-balance"),
        LedgerAssertionKind.wireValues());
    assertEquals("open-book", LedgerStepKind.wireValues().getFirst());

    assertThrows(NullPointerException.class, () -> LedgerStepKind.fromWireValue(null));
    assertThrows(IllegalArgumentException.class, () -> LedgerStepKind.fromWireValue("post_entry"));
    assertThrows(NullPointerException.class, () -> LedgerAssertionKind.fromWireValue(null));
    assertThrows(
        IllegalArgumentException.class, () -> LedgerAssertionKind.fromWireValue("assert-unknown"));
    assertThrows(NullPointerException.class, () -> BookMigrationPolicy.fromWireValue(null));
    assertThrows(
        IllegalArgumentException.class, () -> BookMigrationPolicy.fromWireValue("migrate"));
  }

  @Test
  void descriptorNamespacesPublishTheirRecordInventories() {
    ContractResponse.RejectionDescriptor leafRejection =
        new ContractResponse.RejectionDescriptor("code", "description");

    assertTrue(
        ContractDiscovery.descriptorTypes().contains(ContractDiscovery.HelpDescriptor.class));
    assertTrue(
        ContractRequestShapes.descriptorTypes()
            .contains(ContractRequestShapes.LedgerPlanRequestShapeDescriptor.class));
    assertTrue(
        ContractResponse.descriptorTypes()
            .contains(ContractResponse.ResponseModelDescriptor.class));
    assertTrue(
        ContractTemplates.descriptorTypes()
            .contains(ContractTemplates.LedgerPlanTemplateDescriptor.class));
    assertEquals(List.of(), leafRejection.detailFields());
    assertEquals(List.of(), leafRejection.detailRejections());
  }

  @Test
  @SuppressWarnings("NullOptional")
  void ledgerFactsAndJournalValidationCoverTypedAndFailureBranches() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());

    assertEquals("value", LedgerFact.text("text", "value").value());
    assertTrue(LedgerFact.flag("flag", true).value());
    assertEquals(7, LedgerFact.count("count", 7).value());
    assertEquals(
        List.of(LedgerFact.text("currencyCode", "EUR")),
        LedgerFact.group("balance", List.of(LedgerFact.text("currencyCode", "EUR"))).facts());
    assertThrows(NullPointerException.class, () -> new LedgerFact.Text("null", null));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.count(" ", 7));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.group("balance", List.of()));

    LedgerJournalEntry detailed =
        new LedgerJournalEntry.Rejected(
            stepId("assert"),
            LedgerStepKind.ASSERT,
            LedgerAssertionKind.ACCOUNT_DECLARED,
            startedAt,
            finishedAt,
            List.of(LedgerFact.flag("active", false)),
            failure);
    LedgerJournalEntry nullableOptionals =
        new LedgerJournalEntry.Succeeded(
            stepId("post"), LedgerStepKind.POST_ENTRY, null, startedAt, finishedAt, List.of());
    LedgerJournalEntry assertionFailed =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerStepKind.ASSERT,
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
            startedAt,
            finishedAt,
            List.of(LedgerFact.text("currencyCode", "EUR")),
            failure);

    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, detailed.detailKind());
    assertEquals(null, nullableOptionals.detailKind());
    assertEquals(null, new LedgerStep.OpenBook(stepId("open")).detailKind());
    assertEquals(Optional.of(failure), detailed.optionalFailure());
    assertEquals(Optional.empty(), nullableOptionals.optionalFailure());
    assertEquals(Optional.of(failure), assertionFailed.optionalFailure());
    assertEquals(LedgerStepStatus.REJECTED, detailed.status());
    assertEquals(LedgerStepStatus.SUCCEEDED, nullableOptionals.status());
    assertEquals(LedgerStepStatus.ASSERTION_FAILED, assertionFailed.status());
    assertThrows(IllegalStateException.class, nullableOptionals::requiredFailure);
    assertEquals(failure, ((LedgerJournalEntry.Failed) detailed).requiredFailure());
    assertEquals(failure, assertionFailed.requiredFailure());
    assertThrows(
        NullPointerException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("assert"),
                null,
                LedgerAssertionKind.ACCOUNT_DECLARED,
                startedAt,
                finishedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.AssertionFailed(
                stepId("assert"),
                LedgerStepKind.ASSERT,
                null,
                startedAt,
                finishedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Succeeded(
                stepId("post"),
                LedgerStepKind.POST_ENTRY,
                LedgerAssertionKind.ACCOUNT_DECLARED,
                startedAt,
                finishedAt,
                List.of()));
  }

  @Test
  @SuppressWarnings("NullOptional")
  void journalRecordsRejectBlankIdentifiersAndBackwardsTimes() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalEntry succeededWithoutFailure =
        new LedgerJournalEntry.Succeeded(
            stepId("post"), LedgerStepKind.POST_ENTRY, null, startedAt, finishedAt, List.of());
    LedgerExecutionJournal rejectedJournal =
        new LedgerExecutionJournal(
            startedAt,
            finishedAt,
            List.of(
                new LedgerJournalEntry.Rejected(
                    stepId("post"),
                    LedgerStepKind.POST_ENTRY,
                    null,
                    startedAt,
                    finishedAt,
                    List.of(),
                    failure)));

    assertTrue(succeededWithoutFailure instanceof LedgerJournalEntry.Succeeded);
    assertEquals(stepId("post"), rejectedJournal.terminalStep().stepId());
    assertEquals(stepId("post"), rejectedJournal.requiredFailedStep().stepId());
    assertThrows(
        IllegalStateException.class,
        () ->
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(succeededWithoutFailure))
                .requiredFailedStep());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId(" "),
                LedgerStepKind.POST_ENTRY,
                null,
                startedAt,
                finishedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("post"),
                LedgerStepKind.POST_ENTRY,
                null,
                finishedAt,
                startedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerExecutionJournal(startedAt, finishedAt, List.of()));
  }

  private static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  private static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
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
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
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
