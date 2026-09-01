package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the report-range admission boundary for persisted debit and credit aggregates. */
class LedgerAggregateMoneyRangePolicyTest {
  private static final PostingAcceptancePolicy POLICY = PostingAcceptancePolicy.currentKernel();
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void rejectionFor_refusesACandidateThatWouldOverflowOneLedgerAggregate() {
    PostingAcceptancePolicyTest.RecordingValidationBook book = initializedBook("1000", "2000");
    book.postings = List.of(maximumPosting("largest-existing", "previous-key"));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            POLICY
                .rejectionFor(PostingAcceptancePolicyTest.command("aggregate-overflow"), book)
                .orElseThrow());

    assertEquals("ledger-aggregate-money-range-exceeded", rejection.violations().getFirst().code());
  }

  @Test
  void rejectionFor_refusesAnAlreadyUnrepresentableHistoricalAggregateBeforeAWrite() {
    PostingAcceptancePolicyTest.RecordingValidationBook book =
        initializedBook("1000", "2000", "3000", "4000");
    var maximumPosting = maximumJournalEntry();
    book.postings =
        List.of(
            PostingAcceptancePolicyTest.existingPosting(
                "existing-one", "existing-one-key", maximumPosting),
            PostingAcceptancePolicyTest.existingPosting(
                "existing-two", "existing-two-key", maximumPosting));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            POLICY
                .rejectionFor(
                    PostingAcceptancePolicyTest.command(
                        "historical-overflow",
                        List.of(
                            PostingAcceptancePolicyTest.line(
                                "3000", JournalLine.EntrySide.DEBIT, "1.00"),
                            PostingAcceptancePolicyTest.line(
                                "4000", JournalLine.EntrySide.CREDIT, "1.00"))),
                    book)
                .orElseThrow());

    assertEquals("ledger-aggregate-money-range-exceeded", rejection.violations().getFirst().code());
    assertTrue(rejection.violations().getFirst().message().contains("account '1000'"));
  }

  private static PostingAcceptancePolicyTest.RecordingValidationBook initializedBook(
      String... accountCodes) {
    var book = new PostingAcceptancePolicyTest.RecordingValidationBook();
    book.initialized = true;
    for (String accountCode : accountCodes) {
      addAggregateAccount(book, accountCode);
    }
    return book;
  }

  private static void addAggregateAccount(
      PostingAcceptancePolicyTest.RecordingValidationBook book, String accountCode) {
    AccountCode account = new AccountCode(accountCode);
    book.accounts.put(account, registeredAggregateAccount(account));
  }

  private static dev.erst.fingrind.executor.bookkeeping.RegisteredAccount
      registeredAggregateAccount(AccountCode accountCode) {
    boolean debit = "1000".equals(accountCode.value()) || "3000".equals(accountCode.value());
    return registeredAccount(
        accountCode,
        new AccountName(debit ? "Cash" : "Revenue"),
        debit ? AccountType.ASSET : AccountType.REVENUE,
        debit ? NormalBalance.DEBIT : NormalBalance.CREDIT,
        true,
        DECLARED_AT);
  }

  private static dev.erst.fingrind.executor.bookkeeping.CommittedPosting maximumPosting(
      String postingId, String idempotencyKey) {
    return PostingAcceptancePolicyTest.existingPosting(
        postingId, idempotencyKey, maximumJournalEntry());
  }

  private static JournalEntry maximumJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-06"),
        List.of(
            new JournalLine(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                Money.ofMinorUnits(EUR, Long.MAX_VALUE)),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                Money.ofMinorUnits(EUR, Long.MAX_VALUE))));
  }
}
