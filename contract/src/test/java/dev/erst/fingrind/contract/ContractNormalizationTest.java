package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers shared collection validation paths across public contract records. */
class ContractNormalizationTest {
  private static final DeclaredAccount CASH_ACCOUNT =
      ContractFixtures.declaredAccount(
          "1000", "Cash", AccountType.ASSET, true, Instant.parse("2026-04-07T10:15:30Z"));

  @Test
  void collectionBearingRecords_rejectNullListsWithFieldContext() {
    assertEquals(
        "openingBalances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        ContractFixtures.bookIdentity(),
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        PostingCoverage.ALL_POSTING_KINDS,
                        new AccountLedgerPagination(
                            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                            Optional.empty(),
                            Optional.empty()),
                        nullOf(),
                        List.of(),
                        List.of()))
            .getMessage());
    assertEquals(
        "entries must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        ContractFixtures.bookIdentity(),
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        PostingCoverage.ALL_POSTING_KINDS,
                        new AccountLedgerPagination(
                            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                            Optional.empty(),
                            Optional.empty()),
                        List.of(),
                        nullOf(),
                        List.of()))
            .getMessage());
    assertEquals(
        "closingBalances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        ContractFixtures.bookIdentity(),
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        PostingCoverage.ALL_POSTING_KINDS,
                        new AccountLedgerPagination(
                            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                            Optional.empty(),
                            Optional.empty()),
                        List.of(),
                        List.of(),
                        nullOf()))
            .getMessage());
    assertEquals(
        "currencyTotals must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new PeriodSummaryReport(
                        ContractFixtures.bookIdentity(),
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-04-30"),
                        PostingCoverage.ALL_POSTING_KINDS,
                        1,
                        2,
                        1,
                        nullOf(),
                        List.of()))
            .getMessage());
    assertEquals(
        "accountActivity must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new PeriodSummaryReport(
                        ContractFixtures.bookIdentity(),
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-04-30"),
                        PostingCoverage.ALL_POSTING_KINDS,
                        1,
                        2,
                        1,
                        List.of(),
                        nullOf()))
            .getMessage());
    assertEquals(
        "balances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountBalanceSnapshot(
                        ContractFixtures.bookIdentity(),
                        CASH_ACCOUNT,
                        Optional.empty(),
                        Optional.empty(),
                        PostingCoverage.ALL_POSTING_KINDS,
                        nullOf()))
            .getMessage());
    assertEquals(
        "accounts must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountPage(
                        ContractFixtures.bookIdentity(), nullOf(), 50, Optional.empty()))
            .getMessage());
    assertEquals(
        "postings must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new PostingPage(
                        ContractFixtures.bookIdentity(),
                        Optional.empty(),
                        EffectiveDateRange.unbounded(),
                        nullOf(),
                        10,
                        Optional.empty(),
                        Map.of()))
            .getMessage());
    assertEquals(
        "facts must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerStepFailure("rejected", "Rejected.", nullOf()))
            .getMessage());
  }

  @Test
  void requiredNonEmptyCollections_rejectNullDirectly() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    assertEquals(
        "steps",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerPlan(new LedgerPlanId("plan-1"), nullOf()))
            .getMessage());
    assertEquals(
        "steps",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerExecutionJournal(startedAt, finishedAt, nullOf()))
            .getMessage());
    assertEquals(
        "facts must not be null.",
        assertThrows(NullPointerException.class, () -> LedgerFact.group("group", nullOf()))
            .getMessage());
    assertEquals(
        "violations must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new PostingRejection.AccountStateViolations(nullOf()))
            .getMessage());
  }

  @Test
  void exitCodeDescriptors_rejectNegativeCodes() {
    ExitCodeDescriptor exitCode = new ExitCodeDescriptor(0, "success");
    assertEquals(0, exitCode.code());
    assertEquals("success", exitCode.meaning());
    assertThrows(IllegalArgumentException.class, () -> new ExitCodeDescriptor(-1, "invalid"));
  }

  @Test
  void reportingFixtures_provideConcreteBalances() {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(Money.parse("EUR", "15.00"), Money.parse("EUR", "0.00"));
    assertEquals("EUR", balance.netAmount().currencyUnit().code());
  }

  @Test
  void currencyBalance_rejectsMixedCurrencies() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CurrencyBalance.ofTotals(Money.parse("EUR", "15.00"), Money.parse("USD", "0.00")));
  }
}
