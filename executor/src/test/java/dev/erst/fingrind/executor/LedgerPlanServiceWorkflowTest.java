package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.groupFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.inspectBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.postEntryCommand;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
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
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Unit tests covering commit, rejection, and rollback workflows in {@link LedgerPlanService}. */
class LedgerPlanServiceWorkflowTest {
  @Test
  void execute_commitsAllSupportedStepFamiliesAndRecordsJournal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          inspectBookStep("open"),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              stepId("revenue"),
                              account(
                                  "2000", "Revenue", AccountType.REVENUE, NormalBalance.CREDIT)),
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")),
                          new LedgerStep.InspectBook(stepId("inspect")),
                          new LedgerStep.ListAccounts(
                              stepId("accounts"),
                              new dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery(
                                  50, Optional.empty())),
                          new LedgerStep.GetPosting(
                              stepId("get"), new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                          new LedgerStep.ListPostings(
                              stepId("postings"),
                              new dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery(
                                  Optional.empty(), null, null, 50, Optional.empty())),
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              AccountBalanceQuery.unbounded(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-declared"),
                              new LedgerAssertion.AccountDeclared(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-active"),
                              new LedgerAssertion.AccountActive(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(
                                  new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  null,
                                  null,
                                  Money.parse("EUR", "10.00"),
                                  BalanceSide.DEBIT)))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertEquals(14, result.journal().steps().size());
      assertTrue(
          result.journal().steps().stream()
              .allMatch(step -> step.status() == LedgerStepStatus.SUCCEEDED));
      assertEquals(LedgerStepKind.INSPECT_BOOK, result.journal().steps().getFirst().kind());
      assertEquals(LedgerStepKind.ASSERT, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
          result.journal().steps().getLast().detailKind());
      assertTrue(
          bookSession
              .findPosting(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))
              .isPresent());
    }
  }

  @Test
  void execute_commitsTaxSetupAtomicallyAndRecordsTheDeclaredRegistration() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("tax-setup"),
                      List.of(
                          inspectBookStep("inspect-book"),
                          new LedgerStep.DeclareAccount(
                              stepId("declare-tax-payable"),
                              new DeclareAccountCommand(
                                  new AccountCode("tax-payable-vat"),
                                  new AccountName("VAT Payable"),
                                  AccountType.LIABILITY,
                                  financialPositionTaxonomy(
                                      FinancialPositionLineClassification.CURRENT_LIABILITY))),
                          new LedgerStep.DeclareAccount(
                              stepId("declare-tax-recoverable"),
                              new DeclareAccountCommand(
                                  new AccountCode("tax-recoverable-vat"),
                                  new AccountName("VAT Recoverable"),
                                  AccountType.ASSET,
                                  financialPositionTaxonomy(
                                      FinancialPositionLineClassification.CURRENT_ASSET))),
                          new LedgerStep.DeclareTaxRegistration(
                              stepId("declare-tax-registration"), taxRegistrationCommand()))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertEquals(
          List.of(
              LedgerStepKind.INSPECT_BOOK,
              LedgerStepKind.DECLARE_ACCOUNT,
              LedgerStepKind.DECLARE_ACCOUNT,
              LedgerStepKind.DECLARE_TAX_REGISTRATION),
          result.journal().steps().stream().map(step -> step.kind()).toList());
      assertTrue(
          result.journal().steps().get(3).facts().stream()
              .anyMatch(fact -> textFact(fact, "taxRegistrationId", "vat-lv")));
      assertTrue(
          result.journal().steps().get(3).facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact,
                          "taxCode",
                          "taxCode",
                          "vat-standard-sale",
                          "applicationKind",
                          "OUTPUT_SALE")));
      assertTrue(bookSession.findTaxRegistration(new TaxRegistrationId("vat-lv")).isPresent());
    }
  }

  @Test
  void execute_rollsBackTaxSetupWhenTheRegistrationCannotUseTheDeclaredAccounts() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      DeclareTaxRegistrationCommand invalidRegistration =
          new DeclareTaxRegistrationCommand(
              new TaxRegistrationId("vat-lv"),
              new TaxRegistrationName("Latvia VAT"),
              new TaxJurisdiction("LV"),
              null,
              new AccountCode("tax-payable-vat"),
              new AccountCode("missing-recoverable-account"),
              TaxObligationFrequency.MONTHLY,
              20,
              List.of(
                  new TaxCodeDefinition(
                      new TaxCode("vat-standard-sale"),
                      new TaxCodeName("VAT Standard Sale"),
                      new TaxRate(210_000),
                      TaxInclusionMode.EXCLUSIVE,
                      TaxApplicationKind.OUTPUT_SALE)));
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("tax-setup-rejected"),
                      List.of(
                          inspectBookStep("inspect-book"),
                          new LedgerStep.DeclareAccount(
                              stepId("declare-tax-payable"),
                              new DeclareAccountCommand(
                                  new AccountCode("tax-payable-vat"),
                                  new AccountName("VAT Payable"),
                                  AccountType.LIABILITY,
                                  financialPositionTaxonomy(
                                      FinancialPositionLineClassification.CURRENT_LIABILITY))),
                          new LedgerStep.DeclareTaxRegistration(
                              stepId("declare-tax-registration"), invalidRegistration))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "tax-definition-violations", result.journal().steps().getLast().requiredFailure().code());
      assertTrue(bookSession.inspectBook().initialized());
      assertTrue(bookSession.allAccounts().isEmpty());
      assertTrue(bookSession.findTaxRegistration(new TaxRegistrationId("vat-lv")).isEmpty());
    }
  }

  @Test
  void execute_recordsUpdatedAndUnchangedTaxRegistrationOutcomes() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult.Succeeded initial =
          assertInstanceOf(
              LedgerPlanResult.Succeeded.class,
              service(bookSession)
                  .execute(
                      new LedgerPlan(
                          planId("tax-registration-declared"),
                          taxSetupSteps(taxRegistrationCommand())),
                      ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      DeclareTaxRegistrationCommand updatedCommand =
          taxRegistrationCommand(new TaxRegistrationNumber("LV40001234567"));
      LedgerPlanResult.Succeeded updated =
          assertInstanceOf(
              LedgerPlanResult.Succeeded.class,
              service(bookSession)
                  .execute(
                      new LedgerPlan(
                          planId("tax-registration-updated"),
                          List.of(
                              new LedgerStep.DeclareTaxRegistration(
                                  stepId("update-tax-registration"), updatedCommand))),
                      ExecutorAccountingTestSupport.TEST_AUTHORIZER));
      LedgerPlanResult.Succeeded unchanged =
          assertInstanceOf(
              LedgerPlanResult.Succeeded.class,
              service(bookSession)
                  .execute(
                      new LedgerPlan(
                          planId("tax-registration-unchanged"),
                          List.of(
                              new LedgerStep.DeclareTaxRegistration(
                                  stepId("replay-tax-registration"), updatedCommand))),
                      ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertEquals(LedgerPlanStatus.SUCCEEDED, initial.status());
      assertEquals(LedgerPlanStatus.SUCCEEDED, updated.status());
      assertEquals(LedgerPlanStatus.SUCCEEDED, unchanged.status());
      assertEquals(LedgerPlanAttestationDisposition.APPENDED, initial.attestationDisposition());
      assertEquals(LedgerPlanAttestationDisposition.APPENDED, updated.attestationDisposition());
      assertNotNull(initial.attestationCommit());
      assertNotNull(updated.attestationCommit());
      assertEquals(
          LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
          unchanged.attestationDisposition());
      assertNull(unchanged.attestationCommit());
      assertTrue(
          updated.journal().steps().getFirst().facts().stream()
              .anyMatch(fact -> textFact(fact, "outcome", "updated")));
      assertTrue(
          updated.journal().steps().getFirst().facts().stream()
              .anyMatch(fact -> textFact(fact, "registrationNumber", "LV40001234567")));
      assertTrue(
          unchanged.journal().steps().getFirst().facts().stream()
              .anyMatch(fact -> textFact(fact, "outcome", "unchanged")));
    }
  }

  @Test
  void execute_rejectsUninitializedPlanThatDoesNotBeginWithOpenBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "administration-book-not-initialized",
          result.journal().steps().getFirst().requiredFailure().code());
      assertFalse(bookSession.inspectBook().initialized());
    }
  }

  private static DeclareTaxRegistrationCommand taxRegistrationCommand() {
    return taxRegistrationCommand(null);
  }

  private static DeclareTaxRegistrationCommand taxRegistrationCommand(
      @Nullable TaxRegistrationNumber registrationNumber) {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        registrationNumber,
        new AccountCode("tax-payable-vat"),
        new AccountCode("tax-recoverable-vat"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE)));
  }

  private static List<LedgerStep> taxSetupSteps(DeclareTaxRegistrationCommand taxRegistration) {
    return List.of(
        inspectBookStep("inspect-book"),
        new LedgerStep.DeclareAccount(
            stepId("declare-tax-payable"),
            new DeclareAccountCommand(
                new AccountCode("tax-payable-vat"),
                new AccountName("VAT Payable"),
                AccountType.LIABILITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY))),
        new LedgerStep.DeclareAccount(
            stepId("declare-tax-recoverable"),
            new DeclareAccountCommand(
                new AccountCode("tax-recoverable-vat"),
                new AccountName("VAT Recoverable"),
                AccountType.ASSET,
                financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET))),
        new LedgerStep.DeclareTaxRegistration(stepId("declare-tax-registration"), taxRegistration));
  }

  @Test
  void execute_rejectsUninitializedPlansWithFamilySpecificCodes() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var preflightResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-preflight"),
                      List.of(
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          "posting-book-not-initialized",
          preflightResult.journal().steps().getFirst().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var queryResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              AccountBalanceQuery.unbounded(new AccountCode("1000"))))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, queryResult.status());
      assertEquals(
          "query-book-not-initialized",
          queryResult.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_rejectsUninitializedAssertionPlansWithQueryFamilyCode() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(
                                  new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          result.journal().steps().getFirst().requiredFailure().code());
      assertEquals(
          LedgerAssertionKind.POSTING_EXISTS, result.journal().steps().getFirst().detailKind());
    }
  }

  @Test
  void execute_rollsBackOnPostingRejection() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          inspectBookStep("open"),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      LedgerStepFailure failure = result.journal().steps().getLast().requiredFailure();
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          failure.code());
      assertEquals("Posting rejected with 1 account-state issue.", failure.message());
      assertTrue(
          failure.facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact, "violation", "code", "unknown-account", "accountCode", "2000")));
      assertTrue(bookSession.inspectBook().initialized());
    }
  }

  @Test
  void execute_rejectsConflictingRedeclarationAndRejectsConflictingRedeclaration() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var openBookResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(planId("plan-open"), List.of(inspectBookStep("open"))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.SUCCEEDED, openBookResult.status());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());

      var redeclareResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-declare"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              new DeclareAccountCommand(
                                  new AccountCode("1000"),
                                  new AccountName("Cash"),
                                  AccountType.ASSET,
                                  financialPositionTaxonomy(
                                      FinancialPositionLineClassification.NONCURRENT_ASSET))))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, redeclareResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.AccountTaxonomyConflict(
                  new AccountCode("1000"),
                  accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
                  financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET))),
          redeclareResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_records_reactivated_renamed_and_unchanged_account_outcomes() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());
      InMemoryBookFixtureMutations.deactivateAccount(bookSession, new AccountCode("1000"));

      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-declare-outcomes"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("reactivate"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              stepId("rename"),
                              account(
                                  "1000", "Cash Reserve", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              stepId("unchanged"),
                              account(
                                  "1000",
                                  "Cash Reserve",
                                  AccountType.ASSET,
                                  NormalBalance.DEBIT)))),
                  ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status(), result::toString);
      assertTrue(
          result.journal().steps().get(0).facts().stream()
              .anyMatch(fact -> textFact(fact, "outcome", "reactivated")));
      assertTrue(
          result.journal().steps().get(1).facts().stream()
              .anyMatch(fact -> textFact(fact, "outcome", "renamed")));
      assertTrue(
          result.journal().steps().get(1).facts().stream()
              .anyMatch(fact -> textFact(fact, "accountName", "Cash Reserve")));
      assertTrue(
          result.journal().steps().get(2).facts().stream()
              .anyMatch(fact -> textFact(fact, "outcome", "unchanged")));
    }
  }
}
