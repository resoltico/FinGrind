package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliFuzzFixtureParsingTest {
  @Test
  void parsing_and_posting_id_helpers_are_deterministic() {
    byte[] requestBytes = CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8);

    assertEquals(
        "2026-04-07",
        CliFuzzFixtures.journalEntry(CliFuzzFixtures.readPostEntryCommand(requestBytes))
            .effectiveDate()
            .toString());
    assertEquals(
        "plan-1",
        CliFuzzFixtures.readLedgerPlan(CliFuzzLedgerPlanFixtureSupport.basicValidLedgerPlanBytes())
            .planId()
            .value());
    assertEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value());
    assertNotEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator("other".getBytes(UTF_8)).nextPostingId().value());
    assertEquals(Instant.parse("2026-04-07T12:00:00Z"), CliFuzzFixtures.fixedClock().instant());
    assertThrows(
        NullPointerException.class, () -> CliFuzzFixtures.readPostEntryCommand(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.readLedgerPlan(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.postingIdGenerator(nullValue()));
  }

  @Test
  void bookkeeping_helpers_follow_typed_and_administrative_entry_currency_shapes() {
    PostEntryCommand typedCommand =
        CliFuzzFixtures.readPostEntryCommand(CliFuzzRequestSeedSupport.validJpyRequestBytes());
    PostEntryCommand openingPositionCommand =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzHarnessTestSupport.openAccountingPositionRequestJson(
                    new CliFuzzHarnessTestSupport.OpenAccountingPositionRequestInput(
                        "2026-04-08",
                        """
                        [
                          {
                            "accountCode": "5000",
                            "side": "CREDIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "12345"
                            }
                          },
                          {
                            "accountCode": "6000",
                            "side": "DEBIT",
                            "amount": {
                              "currencyCode": "GBP",
                              "minorUnits": "12345"
                            }
                          }
                        ]
                        """,
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-manual-1",
                            "opening-balance-sheet",
                            "2026-04-08",
                            "actor-manual-1",
                            "PERSON",
                            "command-manual-1",
                            "idem-manual-1",
                            "cause-manual-1",
                            null)))
                .getBytes(UTF_8));
    PostEntryCommand cashExpenseCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-09"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("CHF", "42"),
                null,
                null,
                null));
    PostEntryCommand equityContributionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-10"),
                new AccountCode("1100"),
                new AccountCode("3100"),
                new MonetaryAmount("CAD", "750"),
                null));
    PostEntryCommand equityWithdrawalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-11"),
                new AccountCode("3100"),
                new AccountCode("1100"),
                new MonetaryAmount("USD", "55"),
                null));
    PostEntryCommand structuredOpeningPositionCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-12"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("SEK", "4200")),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("SEK", "4200")))));
    PostEntryCommand reversalCommand =
        CliFuzzFixtureCommandSupport.withEntry(
            typedCommand,
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-13"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(
                        new dev.erst.fingrind.core.PostingId("posting-1")),
                    new dev.erst.fingrind.core.ReversalReason("operator reversal")),
                null,
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-13"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.parse("NOK", "12.50")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.parse("NOK", "12.50"))))));

    assertEquals("JPY", CliFuzzFixtures.journalEntry(typedCommand).currencyUnit().code());
    assertEquals(
        "JPY",
        CliFuzzFixtures.bookkeepingCommand(typedCommand).journalEntry().currencyUnit().code());
    assertEquals("GBP", CliFuzzFixtures.journalEntry(openingPositionCommand).currencyUnit().code());
    assertEquals(
        "CHF",
        CliFuzzFixtures.bookkeepingCommand(cashExpenseCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "CAD",
        CliFuzzFixtures.bookkeepingCommand(equityContributionCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "USD",
        CliFuzzFixtures.bookkeepingCommand(equityWithdrawalCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "SEK",
        CliFuzzFixtures.bookkeepingCommand(structuredOpeningPositionCommand)
            .journalEntry()
            .currencyUnit()
            .code());
    assertEquals(
        "NOK",
        CliFuzzFixtures.bookkeepingCommand(reversalCommand).journalEntry().currencyUnit().code());
    assertEquals(PostingKind.OPENING_BALANCE, CliFuzzFixtures.postingKind(openingPositionCommand));
    assertEquals(
        PostingKind.OPENING_BALANCE, CliFuzzFixtures.postingKind(structuredOpeningPositionCommand));
    assertEquals(PostingKind.STANDARD, CliFuzzFixtures.postingKind(reversalCommand));
  }

  @SuppressWarnings({"NullAway", "TypeParameterUnusedInFormals"})
  private static <T> T nullValue() {
    return null;
  }
}
