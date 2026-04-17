package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for query-side model records and sealed families. */
class BookQueryModelTest {
  @Test
  void accountBalanceQuery_normalizesMissingOptionalsAndRejectsDescendingDateRange() {
    Optional<LocalDate> missingDate = null;
    AccountBalanceQuery query =
        new AccountBalanceQuery(new AccountCode("1000"), missingDate, missingDate);
    AccountBalanceQuery lowerBoundedQuery =
        new AccountBalanceQuery(
            new AccountCode("1000"), Optional.of(LocalDate.parse("2026-04-08")), Optional.empty());
    AccountBalanceQuery orderedRangeQuery =
        new AccountBalanceQuery(
            new AccountCode("1000"),
            Optional.of(LocalDate.parse("2026-04-08")),
            Optional.of(LocalDate.parse("2026-04-09")));

    assertTrue(query.effectiveDateFrom().isEmpty());
    assertTrue(query.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-08")), lowerBoundedQuery.effectiveDateFrom());
    assertTrue(lowerBoundedQuery.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-09")), orderedRangeQuery.effectiveDateTo());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccountBalanceQuery(
                new AccountCode("1000"),
                Optional.of(LocalDate.parse("2026-04-09")),
                Optional.of(LocalDate.parse("2026-04-08"))));
  }

  @Test
  void listAccountsQuery_validatesBounds() {
    assertEquals(new ListAccountsQuery(50, 0), new ListAccountsQuery(50, 0));
    assertThrows(IllegalArgumentException.class, () -> new ListAccountsQuery(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ListAccountsQuery(201, 0));
    assertThrows(IllegalArgumentException.class, () -> new ListAccountsQuery(1, -1));
  }

  @Test
  void listPostingsQuery_normalizesMissingOptionalsAndValidatesBounds() {
    Optional<AccountCode> missingAccount = null;
    Optional<LocalDate> missingDate = null;
    ListPostingsQuery query =
        new ListPostingsQuery(missingAccount, missingDate, missingDate, 50, 0);
    ListPostingsQuery lowerBoundedQuery =
        new ListPostingsQuery(
            Optional.empty(), Optional.of(LocalDate.parse("2026-04-08")), Optional.empty(), 50, 0);
    ListPostingsQuery orderedRangeQuery =
        new ListPostingsQuery(
            Optional.empty(),
            Optional.of(LocalDate.parse("2026-04-08")),
            Optional.of(LocalDate.parse("2026-04-09")),
            50,
            0);

    assertTrue(query.accountCode().isEmpty());
    assertTrue(query.effectiveDateFrom().isEmpty());
    assertTrue(query.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-08")), lowerBoundedQuery.effectiveDateFrom());
    assertTrue(lowerBoundedQuery.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-09")), orderedRangeQuery.effectiveDateTo());
    assertEquals(200, ListPostingsQuery.maxLimit());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListPostingsQuery(missingAccount, missingDate, missingDate, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListPostingsQuery(missingAccount, missingDate, missingDate, 201, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListPostingsQuery(missingAccount, missingDate, missingDate, 1, -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListPostingsQuery(
                Optional.empty(),
                Optional.of(LocalDate.parse("2026-04-09")),
                Optional.of(LocalDate.parse("2026-04-08")),
                1,
                0));
  }

  @Test
  void accountAndPostingPages_copyPayloadsAndValidateBounds() {
    List<DeclaredAccount> accounts = new ArrayList<>(List.of(declaredAccount("1000")));
    AccountPage accountPage = new AccountPage(accounts, 50, 0, false);
    accounts.clear();
    assertEquals(1, accountPage.accounts().size());
    assertThrows(IllegalArgumentException.class, () -> new AccountPage(List.of(), 0, 0, false));
    assertThrows(IllegalArgumentException.class, () -> new AccountPage(List.of(), 1, -1, false));

    List<PostingFact> postings = new ArrayList<>(List.of(postingFact("posting-1", "idem-1")));
    PostingPage postingPage = new PostingPage(postings, 50, 0, false);
    postings.clear();
    assertEquals(1, postingPage.postings().size());
    assertThrows(IllegalArgumentException.class, () -> new PostingPage(List.of(), 0, 0, false));
    assertThrows(IllegalArgumentException.class, () -> new PostingPage(List.of(), 1, -1, false));
  }

  @Test
  void accountBalanceSnapshot_normalizesMissingOptionalsAndCopiesBalances() {
    Optional<LocalDate> missingDate = null;
    List<CurrencyBalance> balances =
        new ArrayList<>(
            List.of(
                new CurrencyBalance(
                    money("10.00"), money("0.00"), money("10.00"), NormalBalance.DEBIT)));

    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(declaredAccount("1000"), missingDate, missingDate, balances);

    balances.clear();
    assertTrue(snapshot.effectiveDateFrom().isEmpty());
    assertTrue(snapshot.effectiveDateTo().isEmpty());
    assertEquals(1, snapshot.balances().size());
  }

  @Test
  void resultRecords_rejectNullPayloads() {
    assertThrows(NullPointerException.class, () -> new ListAccountsResult.Listed(null));
    assertThrows(NullPointerException.class, () -> new ListAccountsResult.Rejected(null));
    assertThrows(NullPointerException.class, () -> new GetPostingResult.Found(null));
    assertThrows(NullPointerException.class, () -> new GetPostingResult.Rejected(null));
    assertThrows(NullPointerException.class, () -> new ListPostingsResult.Listed(null));
    assertThrows(NullPointerException.class, () -> new ListPostingsResult.Rejected(null));
    assertThrows(NullPointerException.class, () -> new AccountBalanceResult.Reported(null));
    assertThrows(NullPointerException.class, () -> new AccountBalanceResult.Rejected(null));
  }

  @Test
  void bookInspection_coversAllStatusesAndRejectsNullMandatoryFields() {
    assertEquals(
        List.of(
            BookInspection.Status.MISSING,
            BookInspection.Status.BLANK_SQLITE,
            BookInspection.Status.INITIALIZED,
            BookInspection.Status.FOREIGN_SQLITE,
            BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
            BookInspection.Status.INCOMPLETE_FINGRIND),
        List.of(BookInspection.Status.values()));
    assertThrows(
        NullPointerException.class,
        () -> new BookInspection(null, false, false, true, null, null, 1, "policy", null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookInspection(
                BookInspection.Status.BLANK_SQLITE, false, false, true, null, null, 1, null, null));
  }

  @Test
  void bookQueryRejection_hasStableWireCodesAndNullChecks() {
    assertEquals(
        List.of("book-not-initialized", "unknown-account", "posting-not-found"),
        List.of(
            BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
            BookQueryRejection.wireCode(
                new BookQueryRejection.UnknownAccount(new AccountCode("1000"))),
            BookQueryRejection.wireCode(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-1")))));
    assertEquals(
        List.of("book-not-initialized", "unknown-account", "posting-not-found"),
        BookQueryRejection.descriptors().stream()
            .map(MachineContract.RejectionDescriptor::code)
            .toList());
    assertThrows(NullPointerException.class, () -> new BookQueryRejection.UnknownAccount(null));
    assertThrows(NullPointerException.class, () -> new BookQueryRejection.PostingNotFound(null));
  }

  @Test
  void accountStateViolations_requiresAtLeastOneViolation() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PostingRejection.AccountStateViolations(List.of()));

    assertFalse(thrown.getMessage().isBlank());
  }

  private static DeclaredAccount declaredAccount(String accountCode) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        Optional.empty(),
        committedProvenance(idempotencyKey));
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            journalLine("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            journalLine("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalLine journalLine(
      String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, money(amount));
  }

  private static Money money(String amount) {
    return new Money(new CurrencyCode("EUR"), new BigDecimal(amount));
  }

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.empty(),
            Optional.empty()),
        Instant.parse("2026-04-07T10:15:30Z"),
        SourceChannel.CLI);
  }
}
