package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Field proofs for the atomic aggregate-attested SQLite ledger-plan boundary. */
class SqliteLedgerPlanExecutionInvariantTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant PLAN_TIME = Instant.parse("2026-07-22T12:00:00Z");

  @Test
  void aggregateCommit_persistsAccountTaxAndPostingChildrenBehindExactlyOneOperation() {
    Path bookPath = tempDirectory.resolve("aggregate-plan-children.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      declareTaxSettlementAccounts(store);
      int operationCountBeforePlan = attestationOperationCount(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      CommittedPosting posting =
          postingFact(
              "aggregate-plan-posting", "aggregate-plan-idem", Optional.empty(), Optional.empty());

      planSession.beginLedgerPlanTransaction("aggregate-plan-children", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Declared.class,
          planSession.declareAccountForPlan(
              accountDeclaration(
                  "3000",
                  "Plan equity",
                  AccountType.EQUITY,
                  FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
              PLAN_TIME,
              authorizer));
      planSession.enterLedgerPlanStep(1);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome.Declared.class,
          planSession.declareTaxRegistrationForPlan(
              taxRegistrationCommand(), PLAN_TIME, authorizer));
      planSession.enterLedgerPlanStep(2);
      assertInstanceOf(
          PlanPostingCommitResult.Deferred.class,
          planSession.commitForPlan(postingDraft(posting), posting::postingId, authorizer));

      AttestationCommit aggregateCommit =
          planSession.appendPlanAttestation(PLAN_TIME.plusSeconds(1), authorizer);
      planSession.commitLedgerPlanTransaction();

      assertEquals(BigInteger.valueOf(operationCountBeforePlan), aggregateCommit.operationOrder());
      assertEquals(operationCountBeforePlan + 1, attestationOperationCount(store));
      assertTrue(store.findAccount(new AccountCode("3000")).isPresent());
      assertTrue(
          planSession
              .findTaxRegistration(taxRegistrationCommand().taxRegistrationId())
              .isPresent());
      assertTrue(store.findPosting(posting.postingId()).isPresent());
    }
  }

  @Test
  void planChildren_requireTheBoundAuthorityAnEnteredStepAndOneChildPerStep() {
    Path bookPath = tempDirectory.resolve("plan-child-admission.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      int operationCountBeforePlan = attestationOperationCount(store);
      AttestationPlanOperationAuthorizer boundAuthorizer = planAuthorizer();
      AttestationPlanOperationAuthorizer wrongAuthorizer = planAuthorizer();
      AccountDeclaration firstAccount =
          accountDeclaration(
              "3000",
              "Plan equity",
              AccountType.EQUITY,
              FinancialPositionLineClassification.EQUITY_CONTRIBUTION);

      planSession.beginLedgerPlanTransaction("plan-child-admission", boundAuthorizer);
      assertThrows(
          IllegalArgumentException.class,
          () -> planSession.declareAccountForPlan(firstAccount, PLAN_TIME, wrongAuthorizer));
      assertThrows(
          IllegalStateException.class,
          () -> planSession.declareAccountForPlan(firstAccount, PLAN_TIME, boundAuthorizer));

      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Declared.class,
          planSession.declareAccountForPlan(firstAccount, PLAN_TIME, boundAuthorizer));
      assertThrows(
          IllegalStateException.class,
          () ->
              planSession.declareAccountForPlan(
                  accountDeclaration(
                      "3100",
                      "Second child in one step",
                      AccountType.EQUITY,
                      FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                  PLAN_TIME,
                  boundAuthorizer));

      planSession.rollbackLedgerPlanTransaction();

      assertFalse(store.findAccount(new AccountCode("3000")).isPresent());
      assertFalse(store.findAccount(new AccountCode("3100")).isPresent());
      assertEquals(operationCountBeforePlan, attestationOperationCount(store));
    }
  }

  @Test
  void commitWithoutAnAggregateAttestation_rollsBackEveryCompletedChild() {
    Path bookPath = tempDirectory.resolve("plan-missing-aggregate.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      int operationCountBeforePlan = attestationOperationCount(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

      planSession.beginLedgerPlanTransaction("plan-missing-aggregate", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Declared.class,
          planSession.declareAccountForPlan(
              accountDeclaration(
                  "3000",
                  "Plan equity",
                  AccountType.EQUITY,
                  FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
              PLAN_TIME,
              authorizer));

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);

      assertFalse(store.findAccount(new AccountCode("3000")).isPresent());
      assertEquals(operationCountBeforePlan, attestationOperationCount(store));
      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
    }
  }

  @Test
  void childFault_rollsBackEarlierPlanChildrenAndClosesThePlanTransaction() {
    Path bookPath = tempDirectory.resolve("plan-child-fault.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      int operationCountBeforePlan = attestationOperationCount(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      CommittedPosting posting =
          postingFact(
              "plan-child-fault", "plan-child-fault-idem", Optional.empty(), Optional.empty());

      planSession.beginLedgerPlanTransaction("plan-child-fault", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Declared.class,
          planSession.declareAccountForPlan(
              accountDeclaration(
                  "3000",
                  "Plan equity",
                  AccountType.EQUITY,
                  FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
              PLAN_TIME,
              authorizer));
      planSession.enterLedgerPlanStep(1);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  planSession.commitForPlan(
                      postingDraft(posting),
                      () -> {
                        throw new IllegalStateException("planned posting identifier failure");
                      },
                      authorizer));

      assertEquals("planned posting identifier failure", failure.getMessage());
      assertFalse(store.findAccount(new AccountCode("3000")).isPresent());
      assertFalse(store.findPosting(posting.postingId()).isPresent());
      assertEquals(operationCountBeforePlan, attestationOperationCount(store));
      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
    }
  }

  @Test
  void accountPlanChild_reportsMissingAndBlankBooksWithoutCreatingAnAggregateChild() {
    AccountDeclaration declaration =
        accountDeclaration(
            "3000",
            "Plan equity",
            AccountType.EQUITY,
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

    Path missingBookPath = tempDirectory.resolve("plan-account-missing.sqlite");
    try (SqlitePostingFactStore store =
            openStore(bookAccess(missingBookPath), SqliteStoreAccessMode.PLAN_EXECUTION);
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      planSession.beginLedgerPlanTransaction("plan-account-missing", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Rejected.class,
          planSession.declareAccountForPlan(declaration, PLAN_TIME, authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
    }

    Path blankBookPath = tempDirectory.resolve("plan-account-blank.sqlite");
    var blankBookAccess = bookAccess(blankBookPath);
    try (SqliteNativeDatabase ignored = openNativeDatabase(blankBookAccess)) {
      // Establish a valid encrypted SQLite file without FinGrind initialization metadata.
    }
    try (SqlitePostingFactStore store =
            openStore(blankBookAccess, SqliteStoreAccessMode.PLAN_EXECUTION);
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      planSession.beginLedgerPlanTransaction("plan-account-blank", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Rejected.class,
          planSession.declareAccountForPlan(declaration, PLAN_TIME, authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
    }
  }

  @Test
  void accountPlanChild_preservesUnchangedAndConflictDecisionsWithoutAnAggregateChild() {
    Path bookPath = tempDirectory.resolve("plan-account-terminal-decisions.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      int operationCountBeforePlans = attestationOperationCount(store);

      planSession.beginLedgerPlanTransaction("plan-account-unchanged", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Unchanged.class,
          planSession.declareAccountForPlan(
              new AccountDeclaration(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT)),
              PLAN_TIME,
              authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();

      planSession.beginLedgerPlanTransaction("plan-account-conflict", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Rejected.class,
          planSession.declareAccountForPlan(
              new AccountDeclaration(
                  new AccountCode("1000"),
                  new AccountName("Cash as revenue"),
                  AccountType.REVENUE,
                  accountTaxonomy(AccountType.REVENUE)),
              PLAN_TIME,
              authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();

      assertEquals(operationCountBeforePlans, attestationOperationCount(store));
    }
  }

  @Test
  void taxPlanChild_preservesNoOpDeclarationsAndAttestsOnlyTheDurableUpdate() {
    Path bookPath = tempDirectory.resolve("plan-tax-terminal-decisions.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      declareTaxSettlementAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.TaxRegistrationMutationOutcome.Declared.class,
          store.declareTaxRegistration(
              taxRegistrationCommand(), PLAN_TIME, SqliteAttestationTestSupport.authorizer()));
      int operationCountBeforePlans = attestationOperationCount(store);

      planSession.beginLedgerPlanTransaction("plan-tax-unchanged", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome.Unchanged.class,
          planSession.declareTaxRegistrationForPlan(
              taxRegistrationCommand(), PLAN_TIME, authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
      assertEquals(operationCountBeforePlans, attestationOperationCount(store));

      planSession.beginLedgerPlanTransaction("plan-tax-updated", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome.Updated.class,
          planSession.declareTaxRegistrationForPlan(
              amendedTaxRegistrationCommand(), PLAN_TIME.plusSeconds(1), authorizer));
      assertTrue(planSession.hasCompletedLedgerPlanChildren());
      planSession.appendPlanAttestation(PLAN_TIME.plusSeconds(2), authorizer);
      planSession.commitLedgerPlanTransaction();

      assertEquals(operationCountBeforePlans + 1, attestationOperationCount(store));
    }
  }

  @Test
  void taxPlanChild_rejectsMissingAndBlankBooksWithoutCreatingAnAggregateChild() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

    Path missingBookPath = tempDirectory.resolve("plan-tax-missing.sqlite");
    try (SqlitePostingFactStore store =
            openStore(bookAccess(missingBookPath), SqliteStoreAccessMode.PLAN_EXECUTION);
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      planSession.beginLedgerPlanTransaction("plan-tax-missing", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome.Rejected.class,
          planSession.declareTaxRegistrationForPlan(
              taxRegistrationCommand(), PLAN_TIME, authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
    }

    Path blankBookPath = tempDirectory.resolve("plan-tax-blank.sqlite");
    var blankBookAccess = bookAccess(blankBookPath);
    try (SqliteNativeDatabase ignored = openNativeDatabase(blankBookAccess)) {
      // Establish a valid encrypted SQLite file without FinGrind initialization metadata.
    }
    try (SqlitePostingFactStore store =
            openStore(blankBookAccess, SqliteStoreAccessMode.PLAN_EXECUTION);
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      planSession.beginLedgerPlanTransaction("plan-tax-blank", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome.Rejected.class,
          planSession.declareTaxRegistrationForPlan(
              taxRegistrationCommand(), PLAN_TIME, authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
    }
  }

  @Test
  void postingPlanChild_replaysAndRejectsWithoutInventingAChildMutation() {
    Path bookPath = tempDirectory.resolve("plan-posting-terminal-decisions.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      CommittedPosting replayedPosting =
          postingFact(
              "plan-replayed", "plan-replayed-idempotency", Optional.empty(), Optional.empty());
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.PostingCommitResult.Appended.class,
          store.commit(
              postingDraft(replayedPosting),
              replayedPosting::postingId,
              SqliteAttestationTestSupport.authorizer()));
      int operationCountBeforePlans = attestationOperationCount(store);

      planSession.beginLedgerPlanTransaction("plan-posting-replayed", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          PlanPostingCommitResult.Replayed.class,
          planSession.commitForPlan(
              postingDraft(replayedPosting),
              () -> {
                throw new AssertionError("A replay must not allocate a posting ID.");
              },
              authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();

      CommittedPosting rejectedPosting =
          postingFact(
              "plan-rejected",
              "plan-rejected-idempotency",
              LocalDate.parse("2026-07-22"),
              PLAN_TIME,
              List.of(
                  line("9999", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "10.00")));
      planSession.beginLedgerPlanTransaction("plan-posting-rejected", authorizer);
      planSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          PlanPostingCommitResult.Rejected.class,
          planSession.commitForPlan(
              postingDraft(rejectedPosting),
              () -> {
                throw new AssertionError("A rejected posting must not allocate a posting ID.");
              },
              authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();

      assertEquals(operationCountBeforePlans, attestationOperationCount(store));
    }
  }

  @Test
  void postingPlanChild_rejectsMissingBooksAndGeneratedCloseDraftsBeforeIDAllocation() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
    CommittedPosting posting =
        postingFact("plan-missing", "plan-missing-idempotency", Optional.empty(), Optional.empty());

    Path missingBookPath = tempDirectory.resolve("plan-posting-missing.sqlite");
    try (SqlitePostingFactStore store =
            openStore(bookAccess(missingBookPath), SqliteStoreAccessMode.PLAN_EXECUTION);
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      planSession.beginLedgerPlanTransaction("plan-posting-missing", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertInstanceOf(
          PlanPostingCommitResult.Rejected.class,
          planSession.commitForPlan(
              postingDraft(posting),
              () -> {
                throw new AssertionError("A missing book must not allocate a posting ID.");
              },
              authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.commitLedgerPlanTransaction();
    }

    Path initializedBookPath = tempDirectory.resolve("plan-posting-generated.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(initializedBookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      var ordinaryDraft = postingDraft(posting);
      var generatedDraft =
          new dev.erst.fingrind.executor.spi.PostingDraft(
              ordinaryDraft.journalEntry(),
              ordinaryDraft.postingLineage(),
              dev.erst.fingrind.core.PostingKind.INTERIM_RESULT_SWEEP,
              dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
              generatedEvidence("plan-generated-close", "interim-result-sweep-plan"),
              ordinaryDraft.requestFingerprint(),
              ordinaryDraft.provenance());
      planSession.beginLedgerPlanTransaction("plan-posting-generated", authorizer);

      assertThrows(
          IllegalArgumentException.class,
          () ->
              planSession.commitForPlan(
                  generatedDraft,
                  () -> {
                    throw new AssertionError(
                        "A generated close draft must not allocate a posting ID.");
                  },
                  authorizer));
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
      planSession.rollbackLedgerPlanTransaction();
    }
  }

  @Test
  void planAccountChild_abortsTheWholePlanWhenSQLiteAdmissionFails() {
    Path bookPath = tempDirectory.resolve("plan-account-admission-failure.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      planSession.beginLedgerPlanTransaction("plan-account-admission-failure", authorizer);
      planSession.enterLedgerPlanStep(0);

      try (StoreDatabaseSwap ignored = swapStoreDatabase(store, staleDatabaseHandle())) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    planSession.declareAccountForPlan(
                        accountDeclaration(
                            "3000",
                            "Plan equity",
                            AccountType.EQUITY,
                            FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                        PLAN_TIME,
                        authorizer));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to declare SQLite ledger-plan account."));
      } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
      assertFalse(store.findAccount(new AccountCode("3000")).isPresent());
    }
  }

  @Test
  void accountPlanExecutor_abortsThePlanWhenItsPersistenceCallbackFailsAtRuntime() {
    Path bookPath = tempDirectory.resolve("plan-account-runtime-failure.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      SqliteAccountRegistryAttestedMutationExecutor executor =
          new SqliteAccountRegistryAttestedMutationExecutor(store.lifecycle);
      IllegalStateException failure =
          new IllegalStateException("forced account persistence failure");
      planSession.beginLedgerPlanTransaction("plan-account-runtime-failure", authorizer);
      planSession.enterLedgerPlanStep(0);

      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  executor.executePlanChild(
                      store.lifecycle.database(),
                      authorizer,
                      () -> Boolean.FALSE,
                      "Failed to declare SQLite ledger-plan account.",
                      (activeDatabase, observedHead) -> {
                        throw failure;
                      })));

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
      assertFalse(planSession.hasCompletedLedgerPlanChildren());
    }
  }

  @Test
  void planTaxChild_abortsTheWholePlanWhenSQLiteAdmissionFails() {
    Path bookPath = tempDirectory.resolve("plan-tax-admission-failure.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      planSession.beginLedgerPlanTransaction("plan-tax-admission-failure", authorizer);
      planSession.enterLedgerPlanStep(0);

      try (StoreDatabaseSwap ignored = swapStoreDatabase(store, staleDatabaseHandle())) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    planSession.declareTaxRegistrationForPlan(
                        taxRegistrationCommand(), PLAN_TIME, authorizer));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to declare SQLite ledger-plan tax registration."));
      } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
    }
  }

  @Test
  void planTaxChild_abortsAndRollsBackWhenThePersistedRegistrationDisappears() {
    Path bookPath = tempDirectory.resolve("plan-tax-disappeared.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      declareTaxSettlementAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      AtomicInteger taxRegistrationLookups = new AtomicInteger();

      try (SqliteStatementRedirectingDatabase disappearingRegistration =
              SqliteStatementRedirectingDatabase.borrowing(
                  requireStoreDatabase(store),
                  (realDatabase, sql) -> {
                    if (SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID.equals(sql)
                        && taxRegistrationLookups.incrementAndGet() == 2) {
                      return realDatabase.prepare("select 1 where ?1 is not null and 0");
                    }
                    return realDatabase.prepare(sql);
                  });
          StoreDatabaseSwap ignored = swapStoreDatabase(store, disappearingRegistration)) {
        planSession.beginLedgerPlanTransaction("plan-tax-disappeared", authorizer);
        planSession.enterLedgerPlanStep(0);

        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    planSession.declareTaxRegistrationForPlan(
                        taxRegistrationCommand(), PLAN_TIME, authorizer));
        assertEquals(
            "Persisted SQLite tax registration disappeared after write: vat-plan",
            failure.getMessage());
      }

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
      assertFalse(
          planSession
              .findTaxRegistration(taxRegistrationCommand().taxRegistrationId())
              .isPresent());
    }
  }

  @Test
  void planPostingChild_abortsTheWholePlanWhenSQLiteAdmissionFails() {
    Path bookPath = tempDirectory.resolve("plan-posting-admission-failure.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      CommittedPosting posting =
          postingFact(
              "plan-admission-failure",
              "plan-admission-failure-idem",
              Optional.empty(),
              Optional.empty());
      planSession.beginLedgerPlanTransaction("plan-posting-admission-failure", authorizer);
      planSession.enterLedgerPlanStep(0);

      try (StoreDatabaseSwap ignored = swapStoreDatabase(store, staleDatabaseHandle())) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    planSession.commitForPlan(
                        postingDraft(posting), posting::postingId, authorizer));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to commit SQLite ledger-plan posting fact."));
      } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }

      assertThrows(IllegalStateException.class, planSession::commitLedgerPlanTransaction);
      assertFalse(store.findPosting(posting.postingId()).isPresent());
    }
  }

  @Test
  void directMutationFamilies_areRejectedWhileAnAggregatePlanIsActive() {
    Path bookPath = tempDirectory.resolve("plan-direct-mutation-guard.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession planSession = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      int operationCountBeforePlan = attestationOperationCount(store);
      AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
      CommittedPosting posting =
          postingFact(
              "direct-guard-posting", "direct-guard-idem", Optional.empty(), Optional.empty());

      planSession.beginLedgerPlanTransaction("plan-direct-mutation-guard", authorizer);

      assertThrows(
          IllegalStateException.class,
          () ->
              store.openAttestedBook(
                  PLAN_TIME,
                  bookIdentity(),
                  List.of(),
                  SqliteAttestationTestSupport.genesis(bookIdentity(), PLAN_TIME)));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.declareAccount(
                  accountDeclaration(
                      "3000",
                      "Direct account",
                      AccountType.EQUITY,
                      FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                  PLAN_TIME,
                  SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.amendAccount(
                  accountDeclaration(
                      "1000",
                      "Amended cash",
                      AccountType.ASSET,
                      FinancialPositionLineClassification.CURRENT_ASSET),
                  PLAN_TIME,
                  SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.retireAccount(
                  new AccountCode("1000"), PLAN_TIME, SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.declareTaxRegistration(
                  taxRegistrationCommand(), PLAN_TIME, SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.commit(
                  postingDraft(posting),
                  posting::postingId,
                  SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.interimResultSweep(
                  new ReportingPeriod(
                      bookIdentity().bookStartEffectiveDate(), LocalDate.parse("2026-07-21")),
                  bookIdentity(),
                  InterimResultSweepPlanner.forBookIdentity(bookIdentity()),
                  LocalDate.parse("2026-07-22"),
                  PLAN_TIME,
                  () -> posting.postingId(),
                  SqliteAttestationTestSupport.authorizer()));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.fiscalYearClose(
                  new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
                  bookIdentity(),
                  FiscalYearClosePlanner.forBookIdentity(bookIdentity()),
                  LocalDate.parse("2027-01-01"),
                  PLAN_TIME,
                  () -> posting.postingId(),
                  SqliteAttestationTestSupport.authorizer()));

      planSession.rollbackLedgerPlanTransaction();

      assertFalse(store.findAccount(new AccountCode("3000")).isPresent());
      assertEquals(
          new AccountName("Cash"),
          store.findAccount(new AccountCode("1000")).orElseThrow().accountName());
      assertTrue(store.findAccount(new AccountCode("1000")).orElseThrow().active());
      assertFalse(
          planSession
              .findTaxRegistration(taxRegistrationCommand().taxRegistrationId())
              .isPresent());
      assertFalse(store.findPosting(posting.postingId()).isPresent());
      assertEquals(operationCountBeforePlan, attestationOperationCount(store));
    }
  }

  private static AttestationPlanOperationAuthorizer planAuthorizer() {
    return new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
  }

  private static AccountDeclaration accountDeclaration(
      String code,
      String name,
      AccountType accountType,
      FinancialPositionLineClassification lineClassification) {
    return new AccountDeclaration(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        financialPositionTaxonomy(lineClassification));
  }

  private static DeclareTaxRegistrationCommand taxRegistrationCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-plan"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber("LV40001234567"),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }

  private static DeclareTaxRegistrationCommand amendedTaxRegistrationCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-plan"),
        new TaxRegistrationName("Latvia VAT amended"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber("LV40001234567"),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }

  private static void declareTaxSettlementAccounts(SqlitePostingFactStore store) {
    assertInstanceOf(
        dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared.class,
        store.declareAccount(
            accountDeclaration(
                "1300",
                "VAT recoverable",
                AccountType.ASSET,
                FinancialPositionLineClassification.CURRENT_ASSET),
            PLAN_TIME,
            SqliteAttestationTestSupport.authorizer()));
    assertInstanceOf(
        dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared.class,
        store.declareAccount(
            accountDeclaration(
                "2100",
                "VAT payable",
                AccountType.LIABILITY,
                FinancialPositionLineClassification.CURRENT_LIABILITY),
            PLAN_TIME,
            SqliteAttestationTestSupport.authorizer()));
  }

  private int attestationOperationCount(SqlitePostingFactStore store) {
    return queryInt(requireStoreDatabase(store), "select count(*) from attestation_operation");
  }
}
