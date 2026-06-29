package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for caller-authored posting entry payload mapping and text rendering. */
class CliPostingEntryPayloadSupportTest {
  @Test
  void entryPayload_mapsEverySupportedEntryVariant() {
    assertNull(CliPostingEntryPayloadSupport.entryPayload(null));

    CliPostingEntryPayload directJournalPayload =
        CliPostingEntryPayloadSupport.entryPayload(directJournalEntry());
    assertNotNull(directJournalPayload);
    assertEquals("DIRECT_JOURNAL", directJournalPayload.entryKind());
    assertNull(directJournalPayload.amount());
    assertNull(directJournalPayload.reversal());
    assertNull(directJournalPayload.openingBalances());

    CliPostingEntryPayload salePayload = CliPostingEntryPayloadSupport.entryPayload(saleEntry());
    assertNotNull(salePayload);
    assertEquals("SALE", salePayload.entryKind());
    assertEquals("1000", salePayload.cashAccountCode());
    assertEquals("4000", salePayload.revenueAccountCode());

    CliPostingEntryPayload expensePayload =
        CliPostingEntryPayloadSupport.entryPayload(expenseEntry());
    assertNotNull(expensePayload);
    assertEquals("EXPENSE", expensePayload.entryKind());
    assertEquals("5000", expensePayload.expenseAccountCode());
    assertEquals("1000", expensePayload.cashAccountCode());

    CliPostingEntryPayload ownerContributionPayload =
        CliPostingEntryPayloadSupport.entryPayload(ownerContributionEntry());
    assertNotNull(ownerContributionPayload);
    assertEquals("OWNER_CONTRIBUTION", ownerContributionPayload.entryKind());
    assertEquals("1000", ownerContributionPayload.cashAccountCode());
    assertEquals("3000", ownerContributionPayload.equityAccountCode());

    CliPostingEntryPayload ownerWithdrawalPayload =
        CliPostingEntryPayloadSupport.entryPayload(ownerWithdrawalEntry());
    assertNotNull(ownerWithdrawalPayload);
    assertEquals("OWNER_WITHDRAWAL", ownerWithdrawalPayload.entryKind());
    assertEquals("1000", ownerWithdrawalPayload.cashAccountCode());
    assertEquals("3010", ownerWithdrawalPayload.equityAccountCode());

    CliPostingEntryPayload openingPositionPayload =
        CliPostingEntryPayloadSupport.entryPayload(openingPositionEntry());
    assertNotNull(openingPositionPayload);
    assertEquals("OPENING_POSITION", openingPositionPayload.entryKind());
    assertNotNull(openingPositionPayload.openingBalances());
    assertEquals(2, openingPositionPayload.openingBalances().size());
    assertEquals("1000", openingPositionPayload.openingBalances().getFirst().accountCode());
    assertEquals("DEBIT", openingPositionPayload.openingBalances().getFirst().side());

    CliPostingEntryPayload reversalPayload =
        CliPostingEntryPayloadSupport.entryPayload(reversalEntry());
    assertNotNull(reversalPayload);
    assertEquals("REVERSAL", reversalPayload.entryKind());
    assertNotNull(reversalPayload.reversal());
    assertEquals("posting-1", reversalPayload.reversal().priorPostingId());
    assertEquals("Correction", reversalPayload.reversal().reason());
  }

  @Test
  void renderEntryFacts_rendersScalarAndReversalFacts() {
    String expenseFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(expenseEntry()));
    String ownerContributionFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(ownerContributionEntry()));
    String ownerWithdrawalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(ownerWithdrawalEntry()));
    String reversalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(reversalEntry()));

    assertTrue(expenseFacts.contains("Entry kind"));
    assertTrue(expenseFacts.contains("Cash account"));
    assertTrue(expenseFacts.contains("1000"));
    assertTrue(expenseFacts.contains("Expense account"));
    assertTrue(expenseFacts.contains("5000"));
    assertTrue(expenseFacts.contains("Amount"));
    assertTrue(expenseFacts.contains("12.50"));
    assertFalse(expenseFacts.contains("Opening balances"));

    assertTrue(ownerContributionFacts.contains("Equity account"));
    assertTrue(ownerContributionFacts.contains("3000"));

    assertTrue(ownerWithdrawalFacts.contains("Equity account"));
    assertTrue(ownerWithdrawalFacts.contains("3010"));

    assertTrue(reversalFacts.contains("Prior posting id"));
    assertTrue(reversalFacts.contains("posting-1"));
    assertTrue(reversalFacts.contains("Reason"));
    assertTrue(reversalFacts.contains("Correction"));
  }

  @Test
  void renderEntryFacts_rendersAppliedTaxWithoutSeparateTaxAccount() {
    String rendered =
        CliPostingEntryPayloadSupport.renderEntryFacts(
            new CliPostingEntryPayload(
                "EXPENSE",
                "1000",
                NullTestSupport.nullOf(String.class),
                "5600",
                NullTestSupport.nullOf(String.class),
                new MonetaryAmount("EUR", "11200"),
                NullTestSupport.nullOf(CliForeignExchangeJsonModels.ForeignExchangePayload.class),
                new CliTaxJsonModels.TaxSelectionPayload("vat-lv", "vat-nonrecoverable-expense"),
                new CliTaxJsonModels.AppliedTaxPayload(
                    "vat-lv",
                    "vat-nonrecoverable-expense",
                    "VAT Nonrecoverable Expense",
                    120_000,
                    "INCLUSIVE",
                    "INPUT_EXPENSE_NONRECOVERABLE",
                    new MonetaryAmount("EUR", "10000"),
                    new MonetaryAmount("EUR", "1200"),
                    new MonetaryAmount("EUR", "11200"),
                    NullTestSupport.nullOf(String.class)),
                NullTestSupport.nullOf(CliBookQueryJsonModels.ReversalPayload.class),
                NullTestSupport
                    .<List<dev.erst.fingrind.cli.json.CliOpeningBalancePayload>>nullOf()));

    assertTrue(rendered.contains("Resolved tax code name"));
    assertTrue(rendered.contains("VAT Nonrecoverable Expense"));
    assertTrue(rendered.contains("Tax account"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderEntryFacts_rendersOpeningBalancesOnlyWhenPresent() {
    String openingFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(openingPositionEntry()));
    String directJournalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(
            new CliPostingEntryPayload(
                "DIRECT_JOURNAL",
                null,
                null,
                null,
                null,
                null,
                NullTestSupport.nullOf(CliForeignExchangeJsonModels.ForeignExchangePayload.class),
                NullTestSupport.nullOf(CliTaxJsonModels.TaxSelectionPayload.class),
                NullTestSupport.nullOf(CliTaxJsonModels.AppliedTaxPayload.class),
                NullTestSupport.nullOf(CliBookQueryJsonModels.ReversalPayload.class),
                List.of()));

    assertTrue(openingFacts.contains("Opening balances"));
    assertTrue(openingFacts.contains("1000"));
    assertTrue(openingFacts.contains("3000"));
    assertTrue(openingFacts.contains("12.50"));
    assertFalse(directJournalFacts.contains("Opening balances"));
  }

  @Test
  void entryPayload_andRenderedFacts_includeOwnedForeignExchangeDetails() {
    CliPostingEntryPayload payload =
        entryPayload(
            new BookkeepingEntry.Sale(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                foreignExchangeDetails(),
                null,
                null));

    assertNotNull(payload.foreignExchange());
    assertEquals("USD", payload.foreignExchange().transactionAmount().currencyCode());
    assertEquals("9200", payload.foreignExchange().functionalAmount().minorUnits());
    assertEquals("SPOT_SETTLEMENT", payload.foreignExchange().treatmentKind());

    String rendered = CliPostingEntryPayloadSupport.renderEntryFacts(payload);
    assertTrue(rendered.contains("Transaction amount"));
    assertTrue(rendered.contains("USD 100.00"));
    assertTrue(rendered.contains("Functional amount"));
    assertTrue(rendered.contains("EUR 92.00"));
    assertTrue(rendered.contains("FX treatment"));
    assertTrue(rendered.contains("Quote source"));
    assertTrue(rendered.contains("ecb-spot"));
  }

  private static BookkeepingEntry directJournalEntry() {
    return new BookkeepingEntry.DirectJournal(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("12.50")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("12.50")))),
        null);
  }

  private static BookkeepingEntry saleEntry() {
    return new BookkeepingEntry.Sale(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1250"),
        null,
        null,
        null);
  }

  private static BookkeepingEntry expenseEntry() {
    return new BookkeepingEntry.Expense(
        LocalDate.parse("2026-04-07"),
        new AccountCode("5000"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        null,
        null,
        null);
  }

  private static BookkeepingEntry ownerContributionEntry() {
    return new BookkeepingEntry.OwnerContribution(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("3000"),
        new MonetaryAmount("EUR", "1250"),
        null);
  }

  private static BookkeepingEntry ownerWithdrawalEntry() {
    return new BookkeepingEntry.OwnerWithdrawal(
        LocalDate.parse("2026-04-07"),
        new AccountCode("3010"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        null);
  }

  private static BookkeepingEntry openingPositionEntry() {
    return new BookkeepingEntry.OpeningPosition(
        LocalDate.parse("2026-04-07"),
        List.of(
            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new MonetaryAmount("EUR", "1250")),
            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                new AccountCode("3000"),
                JournalLine.EntrySide.CREDIT,
                new MonetaryAmount("EUR", "1250"))));
  }

  private static BookkeepingEntry reversalEntry() {
    return new BookkeepingEntry.Reversal(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("12.50")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("12.50")))),
        new PostingLineage.Reversal(
            new ReversalReference(new PostingId("posting-1")), new ReversalReason("Correction")),
        null);
  }

  private static dev.erst.fingrind.core.Money money(String amount) {
    return dev.erst.fingrind.core.Money.parse("EUR", amount);
  }

  private static CliPostingEntryPayload entryPayload(BookkeepingEntry entry) {
    return Objects.requireNonNull(CliPostingEntryPayloadSupport.entryPayload(entry));
  }

  private static ForeignExchangeDetails foreignExchangeDetails() {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "10000"),
        new MonetaryAmount("EUR", "9200"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            LocalDate.parse("2026-04-06"),
            "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
  }
}
