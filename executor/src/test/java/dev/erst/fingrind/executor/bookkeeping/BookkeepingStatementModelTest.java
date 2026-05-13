package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct model-validation coverage for the accounting reporting and close-period surface. */
class BookkeepingStatementModelTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-12T13:45:00Z");

  @Test
  void registeredAccountDeclare_rejectsTypeConflictAndPreservesRoleBasedNormalBalance() {
    RegisteredAccount existing =
        new RegisteredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            true,
            FIXED_INSTANT);
    AccountDeclaration conflictDeclaration =
        new AccountDeclaration(
            existing.accountCode(),
            new AccountName("Cash"),
            AccountType.LIABILITY,
            AccountRole.ORDINARY);
    AccountDeclaration redeclaration =
        new AccountDeclaration(
            existing.accountCode(),
            new AccountName("Cash Reserve"),
            AccountType.ASSET,
            AccountRole.CONTRA);

    assertEquals(
        new AccountDeclarationOutcome.Rejected(
            new BookkeepingAdministrationRejection.AccountTypeConflict(
                existing.accountCode(), AccountType.ASSET, AccountType.LIABILITY)),
        RegisteredAccount.declare(existing, conflictDeclaration, FIXED_INSTANT));
    assertEquals(
        new AccountDeclarationOutcome.Rejected(
            new BookkeepingAdministrationRejection.AccountRoleConflict(
                existing.accountCode(), AccountRole.ORDINARY, AccountRole.CONTRA)),
        RegisteredAccount.declare(existing, redeclaration, FIXED_INSTANT));
    assertEquals(NormalBalance.DEBIT, existing.normalBalance());
    assertEquals(
        NormalBalance.CREDIT,
        new AccountDeclaration(
                new AccountCode("1090"),
                new AccountName("Accumulated Depreciation"),
                AccountType.ASSET,
                AccountRole.CONTRA)
            .normalBalance());
  }

  @Test
  void administrationRejections_requireTheirMandatoryFields() {
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountTypeConflict(
                        nullOf(AccountCode.class), AccountType.ASSET, AccountType.LIABILITY))
            .getMessage());
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.AccountRoleConflict(
                        nullOf(AccountCode.class), AccountRole.ORDINARY, AccountRole.CONTRA))
            .getMessage());
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.RetainedEarningsAccountInactive(
                        nullOf(AccountCode.class)))
            .getMessage());
    assertEquals(
        "requiredEffectiveDateFrom",
        assertThrows(
                NullPointerException.class,
                () ->
                    new BookkeepingAdministrationRejection.PeriodCloseMustStartAt(
                        nullOf(LocalDate.class)))
            .getMessage());
  }

  @Test
  void statementAndCloseModels_validateOrderingAndDefensivelyCopyCollections() {
    List<FinancialPositionSectionView> financialPositionSections =
        new ArrayList<>(
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            false,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
    List<IncomeStatementSectionView> incomeSections =
        new ArrayList<>(
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            false,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))));
    List<ChangesInEquityRowView> equityRows =
        new ArrayList<>(
            List.of(
                new ChangesInEquityRowView(
                    "current-earnings",
                    "Current Earnings",
                    true,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))));
    List<PostingDraft> closingPostings = new ArrayList<>(List.of(postingDraft()));
    List<PostingId> closingPostingIds = new ArrayList<>(List.of(new PostingId("posting-1")));

    FinancialPositionView financialPositionView =
        new FinancialPositionView(
            Optional.of(LocalDate.parse("2026-05-12")), financialPositionSections);
    IncomeStatementView incomeStatementView =
        new IncomeStatementView(
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-12"),
            incomeSections,
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    ChangesInEquityView changesInEquityView =
        new ChangesInEquityView(
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-12"),
            equityRows,
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    PeriodCloseDraft periodCloseDraft =
        new PeriodCloseDraft(
            new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
            new AccountCode("3200"),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            FIXED_INSTANT,
            closingPostings);
    ClosedPeriod closedPeriod =
        new ClosedPeriod(
            1,
            new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
            new AccountCode("3200"),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            FIXED_INSTANT,
            closingPostingIds);

    financialPositionSections.clear();
    incomeSections.clear();
    equityRows.clear();
    closingPostings.clear();
    closingPostingIds.clear();

    assertEquals(1, financialPositionView.sections().size());
    assertEquals(1, incomeStatementView.sections().size());
    assertEquals(1, changesInEquityView.rows().size());
    assertEquals(1, periodCloseDraft.closingPostings().size());
    assertEquals(1, closedPeriod.closingPostingIds().size());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementCriteria(
                LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityCriteria(
                LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementView(
                LocalDate.parse("2026-05-12"),
                LocalDate.parse("2026-05-01"),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityView(
                LocalDate.parse("2026-05-12"),
                LocalDate.parse("2026-05-01"),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ClosedPeriod(
                0,
                new ReportingPeriod(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-12")),
                new AccountCode("3200"),
                List.of(currencyBalance("0.00", "1.00", "1.00", BalanceSide.CREDIT)),
                FIXED_INSTANT,
                List.of()));
  }

  private static PostingDraft postingDraft() {
    return new PostingDraft(
        new JournalEntry(
            LocalDate.parse("2026-05-12"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "1.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "1.00")))),
        PostingLineageModel.direct(),
        PostingKind.PERIOD_CLOSE,
        new CommittedProvenance(
            new RequestProvenance(
                new dev.erst.fingrind.core.ActorId("actor-1"),
                dev.erst.fingrind.core.ActorType.SYSTEM,
                new dev.erst.fingrind.core.CommandId("command-1"),
                new dev.erst.fingrind.core.IdempotencyKey("idem-1"),
                new dev.erst.fingrind.core.CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(Money.parse("EUR", debitAmount), Money.parse("EUR", creditAmount));
    assertEquals(Money.parse("EUR", netAmount), balance.netAmount());
    assertEquals(balanceSide, balance.balanceSide());
    return balance;
  }
}
