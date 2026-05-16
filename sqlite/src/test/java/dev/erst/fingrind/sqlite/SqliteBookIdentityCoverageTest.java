package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage for book-identity metadata readers and dependent direct readers. */
class SqliteBookIdentityCoverageTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void loadBookIdentity_returnsEmptyWithoutEntityName() {
    Path bookPath = tempDirectory.resolve("book-identity-missing-entity.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(
              """
              delete from book_meta
              where key = 'entity_name'
              """);
          assertEquals(Optional.empty(), SqliteStatementQueries.loadBookIdentity(database));
        });
  }

  @Test
  void loadBookIdentity_requiresFunctionalCurrencyAndFiscalYearMetadata() {
    Path missingFunctionalCurrencyPath =
        tempDirectory.resolve("book-identity-missing-functional-currency.sqlite");
    initializeBookOnDisk(missingFunctionalCurrencyPath);
    withStandaloneDatabase(
        bookAccess(missingFunctionalCurrencyPath),
        database -> {
          database.executeStatement(
              """
              delete from book_meta
              where key = 'functional_currency_code'
              """);
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.loadBookIdentity(database));
          assertEquals(
              "Initialized SQLite book is missing functional-currency metadata.",
              exception.getMessage());
        });

    Path missingFiscalYearPath = tempDirectory.resolve("book-identity-missing-fiscal-year.sqlite");
    initializeBookOnDisk(missingFiscalYearPath);
    withStandaloneDatabase(
        bookAccess(missingFiscalYearPath),
        database -> {
          database.executeStatement(
              """
              delete from book_meta
              where key = 'fiscal_year_start'
              """);
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.loadBookIdentity(database));
          assertEquals(
              "Initialized SQLite book is missing fiscal-year-start metadata.",
              exception.getMessage());
        });
  }

  @Test
  void lifecycleInspectionMapper_andTrialBalanceReader_requireBookIdentityMetadata() {
    Path bookPath = tempDirectory.resolve("book-identity-required-by-readers.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(
              """
              delete from book_meta
              where key = 'entity_name'
              """);

          IllegalStateException mapperException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteBookLifecycleInspectionMapper.fromSnapshot(
                          new SqliteBookStateSnapshot(
                              SqliteBookContract.APPLICATION_ID,
                              SqliteBookContract.FORMAT_VERSION,
                              SqliteBookState.INITIALIZED_FINGRIND),
                          database));
          assertEquals(
              "Initialized SQLite book is missing book-identity metadata.",
              mapperException.getMessage());

          IllegalStateException trialBalanceException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      new SqliteTrialBalanceReader()
                          .trialBalance(
                              database,
                              SqliteStoreTestIntrospectionSupport.trialBalanceCriteria(
                                  Optional.empty())));
          assertEquals(
              "Initialized SQLite book is missing book identity.",
              trialBalanceException.getMessage());
        });
  }

  @Test
  void loadBookIdentity_returnsCanonicalBookIdentityWhenMetadataIsPresent() {
    Path bookPath = tempDirectory.resolve("book-identity-present.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database ->
            assertEquals(
                Optional.of(SqlitePostingFactFixtureSupport.bookIdentity()),
                SqliteStatementQueries.loadBookIdentity(database)));
  }

  @Test
  void loadBookIdentity_roundTripsEncodedBusinessActivityTags() {
    Path bookPath = tempDirectory.resolve("book-identity-business-activity-tags.sqlite");
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.UNSPECIFIED,
                List.of(
                    new BusinessActivityTag("translation,localization"),
                    new BusinessActivityTag("cafe services"))),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingBasis.ACCRUAL);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertInitializedAtRow(database);
          SqliteMutationWriter.insertBookIdentity(database, bookIdentity);

          assertEquals(
              Optional.of(bookIdentity), SqliteStatementQueries.loadBookIdentity(database));
        });
  }
}
