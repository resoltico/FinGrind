package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for query-side model records and sealed families. */
class BookQueryModelTest {
  @Test
  void accountBalanceQuery_acceptsNullableBoundsAndRejectsDescendingDateRange() {
    AccountBalanceQuery query = AccountBalanceQuery.unbounded(new AccountCode("1000"));
    AccountBalanceQuery lowerBoundedQuery =
        new AccountBalanceQuery(new AccountCode("1000"), LocalDate.parse("2026-04-08"), null);
    AccountBalanceQuery orderedRangeQuery =
        new AccountBalanceQuery(
            new AccountCode("1000"), LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-09"));
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
                LocalDate.parse("2026-04-09"),
                LocalDate.parse("2026-04-08")));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountBalanceQuery(
                new AccountCode("1000"), nullOf(), PostingCoverage.ALL_POSTING_KINDS));
  }

  @Test
  void accountBalanceAndLedgerQueries_preservePostingCoverageAcrossFactories() {
    AccountBalanceQuery explicitBalanceQuery =
        AccountBalanceQuery.unbounded(
            new AccountCode("1000"), PostingCoverage.NON_CLOSING_POSTINGS);
    AccountBalanceQuery explicitBalanceRangeQuery =
        new AccountBalanceQuery(
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.NON_CLOSING_POSTINGS);
    AccountLedgerQuery defaultLedgerQuery = AccountLedgerQuery.unbounded(new AccountCode("1000"));
    AccountLedgerQuery explicitLedgerQuery =
        AccountLedgerQuery.unbounded(new AccountCode("1000"), PostingCoverage.NON_CLOSING_POSTINGS);
    AccountLedgerQuery explicitLedgerRangeQuery =
        new AccountLedgerQuery(
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.NON_CLOSING_POSTINGS);

    assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, explicitBalanceQuery.postingCoverage());
    assertTrue(explicitBalanceQuery.effectiveDateFrom().isEmpty());
    assertTrue(explicitBalanceQuery.effectiveDateTo().isEmpty());
    assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, explicitBalanceRangeQuery.postingCoverage());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), explicitBalanceRangeQuery.effectiveDateFrom());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), explicitBalanceRangeQuery.effectiveDateTo());
    assertEquals(PostingCoverage.ALL_POSTING_KINDS, defaultLedgerQuery.postingCoverage());
    assertTrue(defaultLedgerQuery.effectiveDateFrom().isEmpty());
    assertTrue(defaultLedgerQuery.effectiveDateTo().isEmpty());
    assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, explicitLedgerQuery.postingCoverage());
    assertTrue(explicitLedgerQuery.effectiveDateFrom().isEmpty());
    assertTrue(explicitLedgerQuery.effectiveDateTo().isEmpty());
    assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, explicitLedgerRangeQuery.postingCoverage());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), explicitLedgerRangeQuery.effectiveDateFrom());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), explicitLedgerRangeQuery.effectiveDateTo());
  }

  @Test
  void listAccountsQuery_validatesBounds() {
    assertEquals(
        new ListAccountsQuery(50, Optional.empty()), new ListAccountsQuery(50, Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new ListAccountsQuery(0, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class, () -> new ListAccountsQuery(201, Optional.empty()));
    assertThrows(NullPointerException.class, () -> new ListAccountsQuery(1, nullOf()));
  }

  @Test
  void listPostingsQuery_rejectsNullCursorAndValidatesBounds() {
    ListPostingsQuery query =
        new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty());
    ListPostingsQuery lowerBoundedQuery =
        new ListPostingsQuery(
            Optional.empty(), LocalDate.parse("2026-04-08"), null, 50, Optional.empty());
    ListPostingsQuery orderedRangeQuery =
        new ListPostingsQuery(
            Optional.empty(),
            LocalDate.parse("2026-04-08"),
            LocalDate.parse("2026-04-09"),
            50,
            Optional.empty());
    assertTrue(query.accountCode().isEmpty());
    assertTrue(query.cursor().isEmpty());
    assertTrue(query.effectiveDateFrom().isEmpty());
    assertTrue(query.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-08")), lowerBoundedQuery.effectiveDateFrom());
    assertTrue(lowerBoundedQuery.effectiveDateTo().isEmpty());
    assertEquals(Optional.of(LocalDate.parse("2026-04-09")), orderedRangeQuery.effectiveDateTo());
    assertEquals(200, ListPostingsQuery.maxLimit());
    assertThrows(
        NullPointerException.class,
        () -> new ListPostingsQuery(nullOf(), EffectiveDateRange.unbounded(), 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListPostingsQuery(Optional.empty(), null, null, 0, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListPostingsQuery(Optional.empty(), null, null, 201, Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () -> new ListPostingsQuery(Optional.empty(), null, null, 1, nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListPostingsQuery(
                Optional.empty(),
                LocalDate.parse("2026-04-09"),
                LocalDate.parse("2026-04-08"),
                1,
                Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () -> new ListPostingsQuery(Optional.empty(), nullOf(), 1, Optional.empty()));
  }

  @Test
  void accountAndPostingPages_copyPayloadsAndValidateBounds() {
    List<DeclaredAccount> accounts = new ArrayList<>(List.of(declaredAccount("1000")));
    AccountPage accountPage = ContractFixtures.accountPage(accounts, 50, Optional.empty());
    AccountPage nextAccountPage =
        ContractFixtures.accountPage(
            List.of(declaredAccount("1000")),
            50,
            Optional.of(new AccountPageCursor(new AccountCode("1000"))));
    accounts.clear();
    assertEquals(1, accountPage.accounts().size());
    assertFalse(accountPage.hasMore());
    assertTrue(nextAccountPage.hasMore());
    assertThrows(
        IllegalArgumentException.class,
        () -> ContractFixtures.accountPage(List.of(), 0, Optional.empty()));
    assertThrows(
        NullPointerException.class, () -> ContractFixtures.accountPage(List.of(), 1, nullOf()));
    List<PostingFact> postings = new ArrayList<>(List.of(postingFact("posting-1", "idem-1")));
    PostingPage postingPage =
        ContractFixtures.postingPage(
            Optional.empty(), EffectiveDateRange.unbounded(), postings, 50, Optional.empty());
    postings.clear();
    assertEquals(1, postingPage.postings().size());
    assertFalse(postingPage.hasMore());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractFixtures.postingPage(
                Optional.empty(), EffectiveDateRange.unbounded(), List.of(), 0, Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            ContractFixtures.postingPage(
                Optional.empty(), EffectiveDateRange.unbounded(), List.of(), 1, nullOf()));
  }

  @Test
  void postingPageCursor_roundTripsStableWireValues() {
    PostingFact postingFact = postingFact("posting-1", "idem-1");
    PostingFact multilinePostingFact = postingFact("posting\n1", "idem-2");
    PostingPageCursor cursor = PostingPageCursor.fromPosting(postingFact);
    PostingPageCursor multilineCursor = PostingPageCursor.fromPosting(multilinePostingFact);
    assertEquals(cursor, PostingPageCursor.fromWireValue(cursor.wireValue()));
    assertEquals(multilineCursor, PostingPageCursor.fromWireValue(multilineCursor.wireValue()));
    assertThrows(NullPointerException.class, () -> PostingPageCursor.fromWireValue(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> PostingPageCursor.fromWireValue("%"));
    assertThrows(
        IllegalArgumentException.class, () -> PostingPageCursor.fromWireValue("not-a-cursor"));
    byte[] validBytes = Base64.getUrlDecoder().decode(cursor.wireValue());
    byte[] unsupportedVersion = Arrays.copyOf(validBytes, validBytes.length);
    unsupportedVersion[0] = 2;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(unsupportedVersion)));
    byte[] truncated = Arrays.copyOf(validBytes, 20);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)));
    byte[] mismatchedLength = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(mismatchedLength).putInt(21, 999);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(mismatchedLength)));
    byte[] negativeLength = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(negativeLength).putInt(21, -1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(negativeLength)));
    byte[] invalidEpochDay = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(invalidEpochDay).putLong(1, Long.MAX_VALUE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(invalidEpochDay)));
    byte[] invalidInstant = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(invalidInstant).putLong(9, Long.MAX_VALUE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostingPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(invalidInstant)));
  }

  @Test
  void accountPageCursor_roundTripsStableWireValues() {
    DeclaredAccount account = declaredAccount("1000");
    AccountPageCursor cursor = AccountPageCursor.fromAccount(account);
    assertEquals(cursor, AccountPageCursor.fromWireValue(cursor.wireValue()));
    assertThrows(NullPointerException.class, () -> AccountPageCursor.fromWireValue(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> AccountPageCursor.fromWireValue("%"));
    assertThrows(
        IllegalArgumentException.class, () -> AccountPageCursor.fromWireValue("not-a-cursor"));
    byte[] validBytes = Base64.getUrlDecoder().decode(cursor.wireValue());
    byte[] unsupportedVersion = Arrays.copyOf(validBytes, validBytes.length);
    unsupportedVersion[0] = 2;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(unsupportedVersion)));
    byte[] truncated = Arrays.copyOf(validBytes, 4);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)));
    byte[] negativeLength = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(negativeLength).putInt(1, -1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(negativeLength)));
    byte[] mismatchedLength = Arrays.copyOf(validBytes, validBytes.length);
    ByteBuffer.wrap(mismatchedLength).putInt(1, 999);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountPageCursor.fromWireValue(
                Base64.getUrlEncoder().withoutPadding().encodeToString(mismatchedLength)));
  }

  @Test
  void accountBalanceSnapshot_rejectsNullOptionalsAndCopiesBalances() {
    List<CurrencyBalance> balances =
        new ArrayList<>(List.of(CurrencyBalance.ofTotals(money("10.00"), money("0.00"))));
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            ContractFixtures.bookIdentity(),
            declaredAccount("1000"),
            Optional.empty(),
            Optional.empty(),
            PostingCoverage.ALL_POSTING_KINDS,
            balances);
    balances.clear();
    assertTrue(snapshot.effectiveDateFrom().isEmpty());
    assertTrue(snapshot.effectiveDateTo().isEmpty());
    assertEquals(1, snapshot.balances().size());
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountBalanceSnapshot(
                ContractFixtures.bookIdentity(),
                declaredAccount("1000"),
                nullOf(),
                Optional.empty(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountBalanceSnapshot(
                ContractFixtures.bookIdentity(),
                declaredAccount("1000"),
                Optional.empty(),
                nullOf(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of()));
  }

  @Test
  void resultRecords_rejectNullPayloads() {
    assertThrows(NullPointerException.class, () -> new ListAccountsResult.Listed(nullOf()));
    assertThrows(NullPointerException.class, () -> new ListAccountsResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new GetPostingResult.Found(ContractFixtures.bookIdentity(), nullOf()));
    assertThrows(NullPointerException.class, () -> new GetPostingResult.Rejected(nullOf()));
    assertThrows(NullPointerException.class, () -> new ListPostingsResult.Listed(nullOf()));
    assertThrows(NullPointerException.class, () -> new ListPostingsResult.Rejected(nullOf()));
    assertThrows(NullPointerException.class, () -> new AccountBalanceResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new AccountBalanceResult.Rejected(nullOf()));
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
    assertEquals(
        List.of(
            "missing",
            "blank-sqlite",
            "initialized",
            "foreign-sqlite",
            "unsupported-format-version",
            "incomplete-fingrind"),
        BookInspection.Status.wireValues());
    assertEquals(
        BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
        BookInspection.Status.fromWireValue("unsupported-format-version"));
    assertThrows(
        IllegalArgumentException.class, () -> BookInspection.Status.fromWireValue("UNKNOWN"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookInspection.Existing(BookInspection.Status.BLANK_SQLITE, 0, -1, 1));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookInspection.Initialized(
                1, 1, 1, nullOf(), ContractFixtures.bookIdentity(), closeReadyInspection()));
    assertThrows(IllegalArgumentException.class, () -> new BookInspection.Missing(0));
  }

  private static BookInspection.CloseReadiness closeReadyInspection() {
    return new BookInspection.CloseReadiness(
        true,
        FinancialPositionLineClassification.CONTRIBUTED_CAPITAL,
        new AccountCode("3200"),
        null,
        null,
        List.of());
  }

  @Test
  void bookQueryRejection_hasStableWireCodesAndNullChecks() {
    assertEquals(
        List.of("query-book-not-initialized", "unknown-account", "posting-not-found"),
        List.of(
            BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
            BookQueryRejection.wireCode(
                new BookQueryRejection.UnknownAccount(new AccountCode("1000"))),
            BookQueryRejection.wireCode(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-1")))));
    assertEquals(
        List.of("query-book-not-initialized", "unknown-account", "posting-not-found"),
        BookQueryRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
    assertEquals(
        BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
        BookQueryRejection.bookNotInitializedCode());
    assertThrows(NullPointerException.class, () -> new BookQueryRejection.UnknownAccount(nullOf()));
    assertThrows(
        NullPointerException.class, () -> new BookQueryRejection.PostingNotFound(nullOf()));
  }

  @Test
  void accountStateViolations_requiresAtLeastOneViolation() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PostingRejection.AccountStateViolations(List.of()));
    assertNotNull(thrown.getMessage());
    String message = java.util.Objects.requireNonNull(thrown.getMessage());
    assertFalse(message.isBlank());
  }

  private static DeclaredAccount declaredAccount(String accountCode) {
    return ContractFixtures.declaredAccount(
        accountCode,
        "Cash",
        AccountType.ASSET,
        AccountRole.ORDINARY,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        PostingLineage.direct(),
        PostingKind.STANDARD,
        ContractFixtures.accountingEvidence(idempotencyKey),
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
    return Money.parse("EUR", amount);
  }

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.empty()),
        Instant.parse("2026-04-07T10:15:30Z"),
        SourceChannel.CLI);
  }
}
