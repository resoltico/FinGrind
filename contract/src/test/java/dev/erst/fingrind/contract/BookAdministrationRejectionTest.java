package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationRejection}. */
class BookAdministrationRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-type-conflict",
            "account-taxonomy-conflict",
            "contra-account-invalid",
            "account-not-found",
            "account-has-dependents",
            "account-balance-not-zero",
            "parent-account-missing",
            "parent-account-inactive",
            "parent-account-type-conflict",
            "parent-account-not-header",
            "parent-account-taxonomy-conflict",
            "account-hierarchy-cycle",
            "close-target-account-candidate-missing",
            "close-target-account-candidate-ambiguous",
            "interim-result-sweep-must-start-at",
            "interim-result-sweep-future-date",
            "interim-result-sweep-crosses-fiscal-year-boundary",
            "fiscal-year-close-must-start-at",
            "fiscal-year-close-must-end-at",
            "fiscal-year-close-precedes-transferred-through-horizon",
            "fiscal-year-close-future-date",
            "fiscal-year-close-requires-generated-postings"),
        List.of(
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookAlreadyInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookNotInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookContainsSchema()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountTypeConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.AccountType.ASSET,
                    dev.erst.fingrind.core.AccountType.EXPENSE)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountTaxonomyConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                        Optional.empty(),
                        Optional.empty()),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                        Optional.empty(),
                        Optional.empty()))),
            BookAdministrationRejection.wireCode(
                new dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid(
                    new dev.erst.fingrind.core.AccountCode("1001"),
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.ContraAccountRelationshipViolation.TARGET_MISSING)),
            BookAdministrationRejection.wireCode(
                new AccountRegistryLifecycleRejection.AccountNotFound(
                    new dev.erst.fingrind.core.AccountCode("1000"))),
            BookAdministrationRejection.wireCode(
                new AccountRegistryLifecycleRejection.AccountHasDependents(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    List.of(dev.erst.fingrind.core.AccountRegistryDependency.POSTINGS))),
            BookAdministrationRejection.wireCode(
                new AccountRegistryLifecycleRejection.AccountBalanceNotZero(
                    new dev.erst.fingrind.core.AccountCode("1000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ParentAccountMissing(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new dev.erst.fingrind.core.AccountCode("1000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ParentAccountInactive(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new dev.erst.fingrind.core.AccountCode("1000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ParentAccountTypeConflict(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    dev.erst.fingrind.core.AccountType.ASSET,
                    new dev.erst.fingrind.core.AccountCode("4000"),
                    dev.erst.fingrind.core.AccountType.REVENUE)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ParentAccountNotHeader(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ParentAccountTaxonomyConflict(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.of(new dev.erst.fingrind.core.AccountCode("1000")),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty(),
                        Optional.empty()),
                    new dev.erst.fingrind.core.AccountCode("5000"),
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(
                            dev.erst.fingrind.core.ProfitAndLossLineClassification
                                .OPERATING_EXPENSE),
                        Optional.empty()))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountHierarchyCycle(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new dev.erst.fingrind.core.AccountCode("1100"))),
            BookAdministrationRejection.wireCode(
                new CloseTargetAccountCandidateMissing(
                    FinancialPositionLineClassification.RESULT_HOLDING,
                    List.of(new dev.erst.fingrind.core.AccountCode("3000")))),
            BookAdministrationRejection.wireCode(
                new CloseTargetAccountCandidateAmbiguous(
                    FinancialPositionLineClassification.OTHER_EQUITY,
                    List.of(
                        new dev.erst.fingrind.core.AccountCode("3000"),
                        new dev.erst.fingrind.core.AccountCode("3010")))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.InterimResultSweepMustStartAt(
                    java.time.LocalDate.parse("2026-04-01"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.InterimResultSweepFutureDate(
                    java.time.LocalDate.parse("2026-04-02"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                    java.time.LocalDate.parse("2026-12-15"),
                    java.time.LocalDate.parse("2027-01-15"),
                    FiscalYearStart.parse("01-01"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                    java.time.LocalDate.parse("2026-01-01"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                    java.time.LocalDate.parse("2026-12-31"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
                    java.time.LocalDate.parse("2025-12-31"),
                    java.time.LocalDate.parse("2026-03-31"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.FiscalYearCloseFutureDate(
                    java.time.LocalDate.parse("2027-01-01"))),
            BookAdministrationRejection.wireCode(new FiscalYearCloseRequiresGeneratedPostings())));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-type-conflict",
            "account-taxonomy-conflict",
            "contra-account-invalid",
            "account-not-found",
            "account-has-dependents",
            "account-balance-not-zero",
            "parent-account-missing",
            "parent-account-inactive",
            "parent-account-type-conflict",
            "parent-account-not-header",
            "parent-account-taxonomy-conflict",
            "account-hierarchy-cycle",
            "close-target-account-candidate-missing",
            "close-target-account-candidate-ambiguous",
            "interim-result-sweep-must-start-at",
            "interim-result-sweep-future-date",
            "interim-result-sweep-crosses-fiscal-year-boundary",
            "fiscal-year-close-must-start-at",
            "fiscal-year-close-must-end-at",
            "fiscal-year-close-precedes-transferred-through-horizon",
            "fiscal-year-close-future-date",
            "fiscal-year-close-requires-generated-postings"),
        BookAdministrationRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        BookAdministrationRejection.wireCode(new BookAdministrationRejection.BookNotInitialized()),
        BookAdministrationRejection.bookNotInitializedCode());
  }
}
