package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Unit tests for aggregate-attested ledger-plan state in {@link InMemoryBookSession}. */
class InMemoryBookSessionLedgerPlanTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void ledgerPlanTransactions_guardLifecycleAndRestoreSnapshotState() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      declareDefaultAccounts(bookSession);
      CommittedPosting baselinePosting = postingFact("idem-baseline");
      bookSession.commit(baselinePosting);
      AttestationPlanOperationAuthorizer planAuthorizer = planAuthorizer();

      bookSession.rollbackLedgerPlanTransaction();
      IllegalStateException noActiveCommit =
          assertThrows(IllegalStateException.class, bookSession::commitLedgerPlanTransaction);
      assertTrue(
          Objects.requireNonNull(noActiveCommit.getMessage())
              .contains("No ledger plan transaction"));

      bookSession.beginLedgerPlanTransaction("plan-rollback", planAuthorizer);
      IllegalStateException nestedBegin =
          assertThrows(
              IllegalStateException.class,
              () -> bookSession.beginLedgerPlanTransaction("plan-nested", planAuthorizer));
      assertTrue(Objects.requireNonNull(nestedBegin.getMessage()).contains("already active"));

      bookSession.enterLedgerPlanStep(0);
      PlanAccountDeclarationOutcome.Declared temporaryDeclaration =
          assertInstanceOf(
              PlanAccountDeclarationOutcome.Declared.class,
              bookSession.declareAccountForPlan(
                  accountDeclaration("3000", "Temporary"), FIXED_INSTANT, planAuthorizer));
      RegisteredAccount temporaryAccount = temporaryDeclaration.account();

      assertEquals(Optional.of(temporaryAccount), bookSession.findAccount(new AccountCode("3000")));
      assertTrue(bookSession.hasCompletedLedgerPlanChildren());
      bookSession.rollbackLedgerPlanTransaction();

      assertEquals(Optional.empty(), bookSession.findAccount(new AccountCode("3000")));
      assertEquals(
          Optional.of(baselinePosting), bookSession.findPosting(baselinePosting.postingId()));

      bookSession.beginLedgerPlanTransaction("plan-commit", planAuthorizer);
      bookSession.enterLedgerPlanStep(0);
      PlanAccountDeclarationOutcome.Declared committedDeclaration =
          assertInstanceOf(
              PlanAccountDeclarationOutcome.Declared.class,
              bookSession.declareAccountForPlan(
                  accountDeclaration("4000", "Committed"), FIXED_INSTANT, planAuthorizer));
      bookSession.appendPlanAttestation(FIXED_INSTANT, planAuthorizer);
      bookSession.commitLedgerPlanTransaction();

      assertEquals(
          Optional.of(committedDeclaration.account()),
          bookSession.findAccount(new AccountCode("4000")));
    }
  }

  @Test
  void activeAggregatePlan_rejectsEveryDirectMutationFamily() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      declareDefaultAccounts(bookSession);
      AttestationPlanOperationAuthorizer planAuthorizer = planAuthorizer();
      bookSession.beginLedgerPlanTransaction("plan-direct-escape", planAuthorizer);

      assertDirectPlanEscapeRejected(
          () -> bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of()));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.declareAccount(
                  accountDeclaration("3000", "Blocked"),
                  FIXED_INSTANT,
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.amendAccount(
                  accountDeclaration("1000", "Blocked rename"),
                  FIXED_INSTANT,
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.retireAccount(
                  new AccountCode("1000"),
                  FIXED_INSTANT,
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      assertDirectPlanEscapeRejected(() -> bookSession.deactivateAccount(new AccountCode("1000")));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.declareTaxRegistration(
                  directTaxRegistration(),
                  FIXED_INSTANT,
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      assertDirectPlanEscapeRejected(() -> bookSession.commit(postingFact("idem-blocked")));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.interimResultSweep(
                  directInterimResultSweepDraft(),
                  () -> {
                    throw new AssertionError(
                        "Direct interim-result sweep must not allocate a posting id.");
                  },
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      ReportingPeriod fiscalYear =
          new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
      assertDirectPlanEscapeRejected(
          () ->
              bookSession.fiscalYearClose(
                  fiscalYear,
                  bookIdentity(),
                  FiscalYearClosePlanner.forBookIdentity(bookIdentity()),
                  LocalDate.parse("2026-12-31"),
                  FIXED_INSTANT,
                  () -> {
                    throw new AssertionError(
                        "Direct fiscal-year close must not allocate a posting id.");
                  },
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      bookSession.rollbackLedgerPlanTransaction();
      assertEquals(
          Optional.of(new AccountName("Cash")),
          bookSession.findAccount(new AccountCode("1000")).map(RegisteredAccount::accountName));
      assertEquals(
          Optional.empty(), bookSession.findPosting(postingFact("idem-blocked").postingId()));
    }
  }

  @Test
  void planCommitWithoutAggregateAttestation_rollsBackEveryCompletedChild() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      AttestationPlanOperationAuthorizer planAuthorizer = planAuthorizer();
      bookSession.beginLedgerPlanTransaction("missing-aggregate", planAuthorizer);
      bookSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          PlanAccountDeclarationOutcome.Declared.class,
          bookSession.declareAccountForPlan(
              accountDeclaration("3000", "Must Roll Back"), FIXED_INSTANT, planAuthorizer));

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, bookSession::commitLedgerPlanTransaction);

      assertEquals(
          "A ledger plan with completed child mutations must append its aggregate attestation before commit.",
          failure.getMessage());
      assertTrue(bookSession.findAccount(new AccountCode("3000")).isEmpty());
      assertFalse(bookSession.hasCompletedLedgerPlanChildren());
      assertThrows(IllegalStateException.class, bookSession::commitLedgerPlanTransaction);
    }
  }

  @Test
  void planChildMutation_requiresTheBoundAuthorizerAndAnEnteredSourceStep() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      AttestationPlanOperationAuthorizer boundAuthorizer = planAuthorizer();
      AttestationPlanOperationAuthorizer otherAuthorizer = planAuthorizer();
      AccountDeclaration declaration = accountDeclaration("3000", "Scoped");

      assertThrows(
          IllegalStateException.class,
          () -> bookSession.declareAccountForPlan(declaration, FIXED_INSTANT, boundAuthorizer));

      bookSession.beginLedgerPlanTransaction("authorizer-scope", boundAuthorizer);
      assertThrows(
          IllegalArgumentException.class,
          () -> bookSession.declareAccountForPlan(declaration, FIXED_INSTANT, otherAuthorizer));
      assertThrows(
          IllegalStateException.class,
          () -> bookSession.declareAccountForPlan(declaration, FIXED_INSTANT, boundAuthorizer));

      bookSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          PlanAccountDeclarationOutcome.Declared.class,
          bookSession.declareAccountForPlan(declaration, FIXED_INSTANT, boundAuthorizer));
      bookSession.appendPlanAttestation(FIXED_INSTANT, boundAuthorizer);
      bookSession.commitLedgerPlanTransaction();
      assertTrue(bookSession.findAccount(new AccountCode("3000")).isPresent());
    }
  }

  @Test
  void childRuntimeFailure_rollsBackEarlierChildrenAndClearsThePlanTransaction() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      declareDefaultAccounts(bookSession);
      AttestationPlanOperationAuthorizer planAuthorizer = planAuthorizer();
      bookSession.beginLedgerPlanTransaction("child-runtime-failure", planAuthorizer);
      bookSession.enterLedgerPlanStep(0);
      assertInstanceOf(
          PlanAccountDeclarationOutcome.Declared.class,
          bookSession.declareAccountForPlan(
              accountDeclaration("3000", "Earlier Child"), FIXED_INSTANT, planAuthorizer));
      bookSession.enterLedgerPlanStep(1);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  bookSession.commitForPlan(
                      postingDraft(postingFact("child-runtime-failure")),
                      () -> {
                        throw new IllegalStateException("posting-id boom");
                      },
                      planAuthorizer));

      assertEquals("posting-id boom", failure.getMessage());
      assertTrue(bookSession.findAccount(new AccountCode("3000")).isEmpty());
      assertFalse(bookSession.hasCompletedLedgerPlanChildren());
      assertThrows(IllegalStateException.class, bookSession::commitLedgerPlanTransaction);
    }
  }

  private static AttestationPlanOperationAuthorizer planAuthorizer() {
    return new AttestationPlanOperationAuthorizer(ExecutorAccountingTestSupport.TEST_AUTHORIZER);
  }

  private static AccountDeclaration accountDeclaration(String accountCode, String accountName) {
    return new AccountDeclaration(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT));
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
        FIXED_INSTANT);
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE, NormalBalance.CREDIT),
        FIXED_INSTANT);
  }

  private static void assertDirectPlanEscapeRejected(Executable action) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, action);
    assertEquals(
        "Direct attested mutations cannot run inside an aggregate-attested ledger plan.",
        exception.getMessage());
  }

  private static DeclareTaxRegistrationCommand directTaxRegistration() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("direct-plan-tax"),
        new TaxRegistrationName("Direct Plan VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("1000"),
        new AccountCode("2000"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("direct-plan-standard"),
                new TaxCodeName("Direct Plan Standard"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }

  private static InterimResultSweepDraft directInterimResultSweepDraft() {
    return new InterimResultSweepDraft(
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")),
        new AccountCode("2000"),
        List.of(),
        FIXED_INSTANT,
        List.of());
  }

  private static PostingDraft postingDraft(CommittedPosting postingFact) {
    return RequestFingerprintTestSupport.fingerprintedDraft(
        postingFact.journalEntry(),
        postingFact.postingLineage(),
        postingFact.postingKind(),
        postingFact.postingOriginKind(),
        postingFact.evidence(),
        postingFact.provenance());
  }

  private static CommittedPosting postingFact(String idempotencyKey) {
    return new CommittedPosting(
        ScenarioPostingIdentifiers.fromLabel("posting-" + idempotencyKey),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.REVERSAL,
        accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                ScenarioCommandIdentifiers.fromLabel("command-" + idempotencyKey),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }
}
