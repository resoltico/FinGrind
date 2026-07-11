package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct coverage for purchase and no-op account-semantics branches. */
class PostEntryRoleAccountSemanticsCoverageTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-05-13T11:00:00Z");

  @Test
  void validate_coversDirectJournalPurchaseAndNoopVariants() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        dev.erst.fingrind.core.Money.ofMinorUnits(
                            dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)),
                    new JournalLine(
                        new AccountCode("4000"),
                        JournalLine.EntrySide.CREDIT,
                        dev.erst.fingrind.core.Money.ofMinorUnits(
                            dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)))),
            null),
        "entryKind",
        "DIRECT_JOURNAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null),
        "entryKind",
        "PURCHASE_SETTLED");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null),
        "entryKind",
        "PURCHASE_ON_CREDIT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null))),
        "entryKind",
        "OPENING_POSITION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts(),
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("operator correction")),
            null,
            null),
        "entryKind",
        "REVERSAL");

    assertEquals(List.of(), violations);
  }

  private static Map<AccountCode, RegisteredAccount> accounts() {
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            accountTaxonomy(AccountType.ASSET),
            true,
            DECLARED_AT);
    RegisteredAccount inventory =
        registeredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
            true,
            DECLARED_AT);
    RegisteredAccount payable =
        registeredAccount(
            new AccountCode("2100"),
            new AccountName("Accounts Payable"),
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_PAYABLE),
            true,
            DECLARED_AT);
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Sales Revenue"),
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE),
            true,
            DECLARED_AT);
    return Map.of(
        cash.accountCode(), cash,
        inventory.accountCode(), inventory,
        payable.accountCode(), payable,
        revenue.accountCode(), revenue);
  }
}
