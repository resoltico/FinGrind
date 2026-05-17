package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
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
            "account-role-conflict",
            "account-taxonomy-conflict",
            "parent-account-missing",
            "parent-account-inactive",
            "parent-account-type-conflict",
            "parent-account-taxonomy-conflict",
            "account-hierarchy-cycle",
            "closing-equity-account-missing",
            "closing-equity-account-classification-mismatch",
            "closing-equity-account-inactive",
            "period-close-must-start-at",
            "period-close-future-date",
            "period-close-crosses-fiscal-year-boundary"),
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
                new BookAdministrationRejection.AccountRoleConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.AccountRole.ORDINARY,
                    dev.erst.fingrind.core.AccountRole.CONTRA)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountTaxonomyConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    new AccountTaxonomy(
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                        Optional.empty()),
                    new AccountTaxonomy(
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                        Optional.empty()))),
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
                new BookAdministrationRejection.ParentAccountTaxonomyConflict(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new AccountTaxonomy(
                        Optional.of(new dev.erst.fingrind.core.AccountCode("1000")),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty()),
                    new dev.erst.fingrind.core.AccountCode("5000"),
                    new AccountTaxonomy(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(
                            dev.erst.fingrind.core.ProfitAndLossLineClassification
                                .OPERATING_EXPENSE)))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountHierarchyCycle(
                    new dev.erst.fingrind.core.AccountCode("1100"),
                    new dev.erst.fingrind.core.AccountCode("1100"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ClosingEquityAccountMissing(
                    new dev.erst.fingrind.core.AccountCode("3000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ClosingEquityAccountClassificationMismatch(
                    new dev.erst.fingrind.core.AccountCode("3000"),
                    FinancialPositionLineClassification.RETAINED_EARNINGS,
                    FinancialPositionLineClassification.OTHER_EQUITY)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.ClosingEquityAccountInactive(
                    new dev.erst.fingrind.core.AccountCode("3000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.PeriodCloseMustStartAt(
                    java.time.LocalDate.parse("2026-04-01"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.PeriodCloseFutureDate(
                    java.time.LocalDate.parse("2026-04-02"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
                    java.time.LocalDate.parse("2026-12-15"),
                    java.time.LocalDate.parse("2027-01-15"),
                    FiscalYearStart.parse("01-01")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-type-conflict",
            "account-role-conflict",
            "account-taxonomy-conflict",
            "parent-account-missing",
            "parent-account-inactive",
            "parent-account-type-conflict",
            "parent-account-taxonomy-conflict",
            "account-hierarchy-cycle",
            "closing-equity-account-missing",
            "closing-equity-account-classification-mismatch",
            "closing-equity-account-inactive",
            "period-close-must-start-at",
            "period-close-future-date",
            "period-close-crosses-fiscal-year-boundary"),
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
