package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for ledger-plan administration rejection facts and guard branches. */
class LedgerPlanAdministrationFailureSupportTest {
  private static final MethodHandle IS_PUBLISHED_CLOSE_WINDOW_REJECTION =
      privateStaticHelper(
          "isPublishedCloseWindowRejection", MethodType.methodType(boolean.class, Object.class));
  private static final MethodHandle CLOSE_WINDOW_FACTS =
      privateStaticHelper(
          "closeWindowFacts", MethodType.methodType(List.class, BookAdministrationRejection.class));
  private static final MethodHandle ACCOUNT_STRUCTURE_FACTS =
      privateStaticHelper(
          "accountStructureFacts",
          MethodType.methodType(List.class, BookAdministrationRejection.class));

  @Test
  void toPublished_mapsLocalRejectionsIntoPublishedRejections() {
    assertEquals(
        new BookAdministrationRejection.FiscalYearCloseMustEndAt(LocalDate.parse("2026-12-31")),
        LedgerPlanAdministrationFailureSupport.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))));
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED,
            List.of(new AccountCode("3300"), new AccountCode("3310"))),
        LedgerPlanAdministrationFailureSupport.toPublished(
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RETAINED_ACCUMULATED,
                List.of(new AccountCode("3300"), new AccountCode("3310")))));
  }

  @Test
  void facts_returnsEmptyForLifecycleRejections() {
    assertEquals(
        List.of(),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.BookAlreadyInitialized()));
    assertEquals(
        List.of(),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.BookNotInitialized()));
    assertEquals(
        List.of(),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.BookContainsSchema()));
  }

  @Test
  void facts_mapsEveryPublishedCloseWindowRejection() {
    assertEquals(
        List.of(BookWorkflowFact.text("requiredEffectiveDateFrom", "2026-01-01")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertEquals(
        List.of(BookWorkflowFact.text("attemptedEffectiveDateTo", "2026-12-31")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.InterimResultSweepFutureDate(
                LocalDate.parse("2026-12-31"))));
    assertEquals(
        List.of(
            BookWorkflowFact.text("attemptedEffectiveDateFrom", "2026-01-01"),
            BookWorkflowFact.text("attemptedEffectiveDateTo", "2026-12-31"),
            BookWorkflowFact.text("fiscalYearStart", "01-01")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
                FiscalYearStart.parse("01-01"))));
    assertEquals(
        List.of(BookWorkflowFact.text("requiredEffectiveDateFrom", "2026-01-01")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertEquals(
        List.of(BookWorkflowFact.text("requiredEffectiveDateTo", "2026-12-31")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))));
    assertEquals(
        List.of(BookWorkflowFact.text("attemptedEffectiveDateTo", "2027-01-01")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))));
  }

  @Test
  void facts_mapsEveryPublishedAccountStructureRejection() {
    AccountTaxonomy requestedFinancialTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.of(new AccountCode("9000")),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    AccountTaxonomy existingFinancialTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH));
    AccountTaxonomy requestedProfitTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.of(new AccountCode("9100")),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    AccountTaxonomy parentProfitTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.HEADER,
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));

    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("existingAccountType", "ASSET"),
            BookWorkflowFact.text("requestedAccountType", "LIABILITY")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY)));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.group(
                "existingAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "POSTABLE"),
                    BookWorkflowFact.text(
                        "financialPositionLineClassification", "NONCURRENT_ASSET"))),
            BookWorkflowFact.group(
                "requestedAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "POSTABLE"),
                    BookWorkflowFact.text("parentAccountCode", "9000"),
                    BookWorkflowFact.text(
                        "financialPositionLineClassification", "CURRENT_ASSET")))),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("1000"), existingFinancialTaxonomy, requestedFinancialTaxonomy)));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("parentAccountCode", "9000")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.ParentAccountMissing(
                new AccountCode("1000"), new AccountCode("9000"))));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("parentAccountCode", "9000")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.ParentAccountInactive(
                new AccountCode("1000"), new AccountCode("9000"))));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("requestedAccountType", "EXPENSE"),
            BookWorkflowFact.text("parentAccountCode", "9000"),
            BookWorkflowFact.text("parentAccountType", "REVENUE")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.ParentAccountTypeConflict(
                new AccountCode("1000"),
                AccountType.EXPENSE,
                new AccountCode("9000"),
                AccountType.REVENUE)));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("parentAccountCode", "9000"),
            BookWorkflowFact.text("parentAccountNodeKind", "HEADER")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.ParentAccountNotHeader(
                new AccountCode("1000"), new AccountCode("9000"), AccountNodeKind.HEADER)));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("parentAccountCode", "9000"),
            BookWorkflowFact.group(
                "requestedAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "POSTABLE"),
                    BookWorkflowFact.text("parentAccountCode", "9100"),
                    BookWorkflowFact.text("profitAndLossLineClassification", "OPERATING_EXPENSE"))),
            BookWorkflowFact.group(
                "parentAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "HEADER"),
                    BookWorkflowFact.text(
                        "profitAndLossLineClassification", "OPERATING_EXPENSE")))),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.ParentAccountTaxonomyConflict(
                new AccountCode("1000"),
                requestedProfitTaxonomy,
                new AccountCode("9000"),
                parentProfitTaxonomy)));
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("parentAccountCode", "9000")),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.AccountHierarchyCycle(
                new AccountCode("1000"), new AccountCode("9000"))));
    assertEquals(
        List.of(
            BookWorkflowFact.text("requiredFinancialPositionLineClassification", "RESULT_HOLDING"),
            BookWorkflowFact.count("inactiveCandidateAccountCount", 2)),
        LedgerPlanAdministrationFailureSupport.facts(
            new BookAdministrationRejection.CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"), new AccountCode("3210")))));
    assertEquals(
        List.of(
            BookWorkflowFact.text(
                "requiredFinancialPositionLineClassification", "RETAINED_ACCUMULATED"),
            BookWorkflowFact.count("candidateAccountCount", 2)),
        LedgerPlanAdministrationFailureSupport.facts(
            new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RETAINED_ACCUMULATED,
                List.of(new AccountCode("3300"), new AccountCode("3310")))));
  }

  @Test
  void privateDispatchHelpers_coverGuardBranches() {
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
                LocalDate.parse("2026-12-31"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
                FiscalYearStart.parse("01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.InterimResultSweepFutureDate(
                LocalDate.parse("2026-12-31"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
                FiscalYearStart.parse("01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))));
    assertTrue(
        isPublishedCloseWindowRejection(
            new BookAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))));
    assertFalse(
        isPublishedCloseWindowRejection(new BookAdministrationRejection.BookNotInitialized()));

    IllegalStateException closeWindowFailure =
        assertThrows(
            IllegalStateException.class,
            () -> closeWindowFacts(new BookAdministrationRejection.BookAlreadyInitialized()));
    IllegalStateException accountStructureFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                accountStructureFacts(
                    new BookAdministrationRejection.FiscalYearCloseFutureDate(
                        LocalDate.parse("2027-01-01"))));

    assertEquals(
        "Unsupported published close-window rejection: "
            + BookAdministrationRejection.BookAlreadyInitialized.class.getName(),
        closeWindowFailure.getMessage());
    assertEquals(
        "Unsupported published account-structure rejection: "
            + BookAdministrationRejection.FiscalYearCloseFutureDate.class.getName(),
        accountStructureFailure.getMessage());
  }

  private static boolean isPublishedCloseWindowRejection(Object rejection) {
    try {
      return (boolean) IS_PUBLISHED_CLOSE_WINDOW_REJECTION.invokeExact(rejection);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke ledger-plan close-window helper.", throwable);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<BookWorkflowFact> closeWindowFacts(BookAdministrationRejection rejection) {
    try {
      return (List<BookWorkflowFact>) CLOSE_WINDOW_FACTS.invokeExact(rejection);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke close-window fact helper.", throwable);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<BookWorkflowFact> accountStructureFacts(
      BookAdministrationRejection rejection) {
    try {
      return (List<BookWorkflowFact>) ACCOUNT_STRUCTURE_FACTS.invokeExact(rejection);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke account-structure fact helper.", throwable);
    }
  }

  private static MethodHandle privateStaticHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              LedgerPlanAdministrationFailureSupport.class, MethodHandles.lookup());
      return lookup.findStatic(
          LedgerPlanAdministrationFailureSupport.class, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind ledger-plan helper: " + methodName, exception);
    }
  }
}
