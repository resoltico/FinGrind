package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;

/** Projects paginated read pages and balance snapshots into the public bookkeeping contract. */
public final class BookkeepingReadPagePublishedLanguageTranslator {
  private BookkeepingReadPagePublishedLanguageTranslator() {}

  /** Projects one local account-registry page back into the public published language. */
  public static AccountPage toPublished(BookIdentity bookIdentity, AccountRegistryPage page) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(page, "page");
    return new AccountPage(
        bookIdentity,
        page.accounts().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPagePublishedLanguageTranslator::toPublished));
  }

  /** Projects one local posting-history page back into the public published language. */
  public static PostingPage toPublished(
      BookIdentity bookIdentity, PostingHistoryQuery query, PostingHistoryPage page) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(page, "page");
    return new PostingPage(
        bookIdentity,
        query.accountCode(),
        query.effectiveDateRange(),
        page.postings().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPagePublishedLanguageTranslator::toPublished));
  }

  /** Projects one local account-balance view back into the public published language. */
  public static AccountBalanceSnapshot toPublished(
      BookIdentity bookIdentity, AccountBalanceView view) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(view, "view");
    return new AccountBalanceSnapshot(
        bookIdentity,
        BookkeepingPublishedLanguageTranslator.toPublished(view.account()),
        view.effectiveDateRange().effectiveDateFrom(),
        view.effectiveDateRange().effectiveDateTo(),
        view.postingCoverage(),
        view.balances());
  }

  private static AccountPageCursor toPublished(AccountRegistryCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return new AccountPageCursor(cursor.accountCode());
  }

  private static PostingPageCursor toPublished(PostingHistoryCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return new PostingPageCursor(cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId());
  }
}
