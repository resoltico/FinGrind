package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Coverage for forwarding defaults on split SQLite read/reporting view adapters. */
class SqliteReadViewCoverageTest extends SqliteStoreFixtureSupport {
  @TempDir Path tempDirectory;

  @Test
  void splitReadViews_delegatePostingReadsThroughTheirNarrowOwners() {
    Path bookPath = tempDirectory.resolve("book.sqlite");
    initializeBookOnDisk(bookPath);
    BookAccess bookAccess = staticBookAccess(bookPath);
    EffectiveDateRange effectiveDateRange = EffectiveDateRange.unbounded();

    try (SqlitePostingFactStore store = openStore(bookAccess)) {
      SqlitePostingFactStorePostingHistoryView postingHistoryView =
          new SqlitePostingFactStorePostingHistoryView() {
            @Override
            public SqliteThreadOwner storeThreadOwner() {
              return store.storeThreadOwner();
            }

            @Override
            public SqliteStoreReadOperations storeReadOperations() {
              return store.storeReadOperations();
            }
          };
      SqlitePostingFactStoreReportingView reportingView =
          new SqlitePostingFactStoreReportingView() {
            @Override
            public SqliteThreadOwner storeThreadOwner() {
              return store.storeThreadOwner();
            }

            @Override
            public SqliteStoreReadOperations storeReadOperations() {
              return store.storeReadOperations();
            }
          };
      SqliteReadReportingCapabilityView reportingCapabilityView =
          new SqliteReadReportingCapabilityView() {
            @Override
            public SqliteThreadOwner storeThreadOwner() {
              return store.storeThreadOwner();
            }

            @Override
            public SqliteStoreReadOperations storeReadOperations() {
              return store.storeReadOperations();
            }

            @Override
            public SqliteStoreLifecycle storeLifecycle() {
              return store.storeLifecycle();
            }

            @Override
            public SqliteStoreContext storeContext() {
              return store.storeContext();
            }
          };

      assertEquals(List.of(), postingHistoryView.postings(effectiveDateRange));
      assertEquals(Optional.empty(), postingHistoryView.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), postingHistoryView.transferredThroughEffectiveDate());
      assertEquals(List.of(), reportingView.postings(effectiveDateRange));
      assertEquals(List.of(), reportingCapabilityView.postings(effectiveDateRange));
    }
  }

  @Test
  void reportingView_projectsTheCanonicalLatestPostingEffectiveDate() {
    Path bookPath = tempDirectory.resolve("latest-posting-effective-date.sqlite");
    try (SqlitePostingFactStore store = openStore(staticBookAccess(bookPath))) {
      SqlitePostingFactFixtureSupport.initializeBookWithMinimalNumericAccounts(store);
      var posting =
          SqlitePostingFactFixtureSupport.postingFact(
              "latest-effective-date", "latest-effective-date", Optional.empty(), Optional.empty());
      SqlitePostingFactStoreTestSupport.commitPosting(store, posting);

      assertEquals(
          Optional.of(posting.journalEntry().effectiveDate()), store.latestPostingEffectiveDate());
    }
  }
}
