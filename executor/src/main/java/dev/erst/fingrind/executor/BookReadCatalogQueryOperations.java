package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPagePublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import java.util.Objects;

/** Application ownership for catalog and balance-query translation. */
final class BookReadCatalogQueryOperations {
  private final BookkeepingReadService bookkeepingReadService;

  BookReadCatalogQueryOperations(BookkeepingReadService bookkeepingReadService) {
    this.bookkeepingReadService =
        Objects.requireNonNull(bookkeepingReadService, "bookkeepingReadService");
  }

  ListAccountsResult listAccounts(ListAccountsQuery query) {
    return switch (bookkeepingReadService.listAccounts(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          new ListAccountsResult.Listed(
              query,
              BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                  bookkeepingReadService.requireInitializedBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          new ListAccountsResult.Rejected(
              BookReadOutcomeMapper.toPublishedRejection(rejected.rejection()));
    };
  }

  AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.accountBalance(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new AccountBalanceResult.Reported(
                BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                    bookkeepingReadService.requireInitializedBookIdentity(), value)),
        AccountBalanceResult.Rejected::new);
  }
}
