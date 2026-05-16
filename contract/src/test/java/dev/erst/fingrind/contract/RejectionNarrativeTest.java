package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RejectionNarrative}. */
class RejectionNarrativeTest {
  @Test
  void administrationMessagesCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookAlreadyInitialized())
            .contains("already initialized"));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookContainsSchema())
            .contains("schema objects"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTypeConflict(
                    new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY))
            .contains("account type"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountRoleConflict(
                    new AccountCode("1000"), AccountRole.ORDINARY, AccountRole.CONTRA))
            .contains("1000"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.AccountTaxonomyConflict(
                    new AccountCode("1000"),
                    new AccountTaxonomy(
                        Optional.of(new AccountCode("3000")),
                        Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                        Optional.empty()),
                    new AccountTaxonomy(
                        Optional.of(new AccountCode("3010")),
                        Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                        Optional.empty())))
            .contains("immutable hierarchy or statement taxonomy"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountMissing(
                    new AccountCode("3200")))
            .contains("Closing-equity account"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountClassificationMismatch(
                    new AccountCode("3200"),
                    FinancialPositionLineClassification.RETAINED_EARNINGS,
                    FinancialPositionLineClassification.OTHER_EQUITY))
            .contains("OTHER_EQUITY"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountClassificationMismatch(
                    new AccountCode("3200"),
                    FinancialPositionLineClassification.RETAINED_EARNINGS,
                    FinancialPositionLineClassification.OTHER_EQUITY))
            .contains("RETAINED_EARNINGS"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.ClosingEquityAccountInactive(
                    new AccountCode("3900")))
            .contains("3900"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseMustStartAt(
                    LocalDate.parse("2026-01-01")))
            .contains("2026-01-01"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseFutureDate(
                    LocalDate.parse("2026-12-31")))
            .contains("2026-12-31"));
    assertTrue(
        RejectionNarrative.message(
                new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
                    LocalDate.parse("2026-12-15"),
                    LocalDate.parse("2027-01-15"),
                    dev.erst.fingrind.core.FiscalYearStart.parse("01-01")))
            .contains("01-01"));
  }

  @Test
  void queryMessagesCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(new BookQueryRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(
        RejectionNarrative.message(new BookQueryRejection.UnknownAccount(new AccountCode("9999")))
            .contains("9999"));
    assertTrue(
        RejectionNarrative.message(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-1")))
            .contains("posting-1"));
  }

  @Test
  void postingMessagesCoverEveryRejection() {
    PostingRejection.AccountStateViolations accountStateViolations =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("9999")),
                new PostingRejection.InactiveAccount(new AccountCode("1000"))));

    assertTrue(
        RejectionNarrative.message(new PostingRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(RejectionNarrative.message(accountStateViolations).contains("Reported issues: 2"));
    assertTrue(
        RejectionNarrative.message(new PostingRejection.DuplicateIdempotencyKey())
            .contains("same idempotency key"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.PostingKindReserved(
                    dev.erst.fingrind.core.PostingKind.PERIOD_CLOSE))
            .contains("PERIOD_CLOSE"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                    dev.erst.fingrind.core.CurrencyUnit.of("USD")))
            .contains("EUR"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ClosedPeriodViolation(
                    LocalDate.parse("2026-05-01"), LocalDate.parse("2026-04-30")))
            .contains("closed-through horizon"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningBalanceWindowClosed(
                    dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-05-02")))
            .contains("first blocking posting"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.OpeningBalanceTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE))
            .contains("4000"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3900")))
            .contains("3900"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))
            .contains("posting-1"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")))
            .contains("full reversal"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1")))
            .contains("does not negate"));
  }

  @Test
  void nullRejectionsAreRejected() {
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<BookAdministrationRejection>nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<BookQueryRejection>nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message(NullTestSupport.<PostingRejection>nullOf()));
  }
}
