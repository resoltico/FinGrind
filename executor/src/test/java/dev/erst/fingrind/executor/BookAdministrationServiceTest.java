package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openedBook;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationService}. */
class BookAdministrationServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructor_rejectsNullBookSession() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BookAdministrationService(
                null, new InMemoryBookSession(), new InMemoryBookSession(), FIXED_CLOCK));
  }

  @Test
  void openBook_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service =
          new BookAdministrationService(bookSession, bookSession, bookSession, FIXED_CLOCK);
      org.junit.jupiter.api.Assertions.assertEquals(
          openedBook(FIXED_CLOCK.instant()), service.openBook(bookIdentity()));
    }
  }

  @Test
  void declareAccount_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service =
          new BookAdministrationService(bookSession, bookSession, bookSession, FIXED_CLOCK);
      service.openBook(bookIdentity());
      AccountDeclarationOutcome result =
          service.declareAccount(
              new AccountDeclaration(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                  accountTaxonomy(AccountType.ASSET)));
      org.junit.jupiter.api.Assertions.assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  FIXED_CLOCK.instant())),
          result);
    }
  }

  @Test
  void declareAccount_rejectsMissingBookBeforeChartValidation() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service =
          new BookAdministrationService(bookSession, bookSession, bookSession, FIXED_CLOCK);
      org.junit.jupiter.api.Assertions.assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                  .BookNotInitialized()),
          service.declareAccount(
              new AccountDeclaration(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                  accountTaxonomy(AccountType.ASSET))));
    }
  }

  @Test
  void declareAccount_rejectsInvalidParentHierarchyBeforeDelegatingToBookStore() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service =
          new BookAdministrationService(bookSession, bookSession, bookSession, FIXED_CLOCK);
      service.openBook(bookIdentity());

      AccountDeclarationOutcome result =
          service.declareAccount(
              new AccountDeclaration(
                  new AccountCode("1010"),
                  new AccountName("Child Cash"),
                  AccountType.ASSET,
                  accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                  new AccountTaxonomy(
                      java.util.Optional.of(new AccountCode("9999")),
                      java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                      java.util.Optional.empty())));

      org.junit.jupiter.api.Assertions.assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                  .ParentAccountMissing(new AccountCode("1010"), new AccountCode("9999"))),
          result);
    }
  }

  @Test
  void closePeriod_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service =
          new BookAdministrationService(bookSession, bookSession, bookSession, FIXED_CLOCK);
      service.openBook(bookIdentity());
      service.declareAccount(
          new AccountDeclaration(
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              AccountRole.ORDINARY,
              accountTaxonomy(AccountType.ASSET)));
      service.declareAccount(
          new AccountDeclaration(
              new AccountCode("3000"),
              new AccountName("Capital"),
              AccountType.EQUITY,
              AccountRole.ORDINARY,
              accountTaxonomy(AccountType.EQUITY)));
      service.declareAccount(
          new AccountDeclaration(
              new AccountCode("3200"),
              new AccountName("Retained Earnings"),
              AccountType.EQUITY,
              AccountRole.ORDINARY,
              financialPositionTaxonomy(FinancialPositionLineClassification.RETAINED_EARNINGS)));
      service.declareAccount(
          new AccountDeclaration(
              new AccountCode("4000"),
              new AccountName("Revenue"),
              AccountType.REVENUE,
              AccountRole.ORDINARY,
              accountTaxonomy(AccountType.REVENUE)));

      bookSession.commit(
          new dev.erst.fingrind.executor.bookkeeping.CommittedPosting(
              new dev.erst.fingrind.core.PostingId("posting-1"),
              new dev.erst.fingrind.core.JournalEntry(
                  LocalDate.parse("2026-04-01"),
                  java.util.List.of(
                      new dev.erst.fingrind.core.JournalLine(
                          new AccountCode("1000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                          dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
                      new dev.erst.fingrind.core.JournalLine(
                          new AccountCode("3000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          dev.erst.fingrind.core.Money.parse("EUR", "10.00")))),
              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
              dev.erst.fingrind.core.PostingKind.STANDARD,
              ExecutorAccountingTestSupport.accountingEvidence("idem-1"),
              new dev.erst.fingrind.core.CommittedProvenance(
                  new dev.erst.fingrind.core.RequestProvenance(
                      new dev.erst.fingrind.core.ActorId("actor-1"),
                      dev.erst.fingrind.core.ActorType.AGENT,
                      new dev.erst.fingrind.core.CommandId("command-1"),
                      new dev.erst.fingrind.core.IdempotencyKey("idem-1"),
                      new dev.erst.fingrind.core.CausationId("cause-1"),
                      java.util.Optional.empty()),
                  FIXED_CLOCK.instant(),
                  dev.erst.fingrind.core.SourceChannel.CLI)));
      bookSession.commit(
          new dev.erst.fingrind.executor.bookkeeping.CommittedPosting(
              new dev.erst.fingrind.core.PostingId("posting-2"),
              new dev.erst.fingrind.core.JournalEntry(
                  LocalDate.parse("2026-04-07"),
                  java.util.List.of(
                      new dev.erst.fingrind.core.JournalLine(
                          new AccountCode("1000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                          dev.erst.fingrind.core.Money.parse("EUR", "12.00")),
                      new dev.erst.fingrind.core.JournalLine(
                          new AccountCode("4000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          dev.erst.fingrind.core.Money.parse("EUR", "12.00")))),
              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
              dev.erst.fingrind.core.PostingKind.STANDARD,
              ExecutorAccountingTestSupport.accountingEvidence("idem-2"),
              new dev.erst.fingrind.core.CommittedProvenance(
                  new dev.erst.fingrind.core.RequestProvenance(
                      new dev.erst.fingrind.core.ActorId("actor-2"),
                      dev.erst.fingrind.core.ActorType.AGENT,
                      new dev.erst.fingrind.core.CommandId("command-2"),
                      new dev.erst.fingrind.core.IdempotencyKey("idem-2"),
                      new dev.erst.fingrind.core.CausationId("cause-2"),
                      java.util.Optional.empty()),
                  FIXED_CLOCK.instant(),
                  dev.erst.fingrind.core.SourceChannel.CLI)));

      PeriodCloseOutcome outcome =
          new PeriodCloseService(
                  bookSession,
                  bookSession,
                  bookSession,
                  bookSession,
                  () -> new dev.erst.fingrind.core.PostingId("period-close-1"),
                  FIXED_CLOCK)
              .closePeriod(
                  new ReportingPeriod(
                      LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-07")));

      org.junit.jupiter.api.Assertions.assertEquals(
          1,
          org.junit.jupiter.api.Assertions.assertInstanceOf(
                  PeriodCloseOutcome.Closed.class, outcome)
              .closedPeriod()
              .closingPostingIds()
              .size());
    }
  }
}
