package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage for book-identity metadata readers and dependent direct readers. */
class SqliteBookIdentityCoverageTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void loadBookIdentity_returnsEmptyWithoutEntityName() {
    Path bookPath = tempDirectory.resolve("book-identity-missing-entity.sqlite");
    createSchemaOnlyBook(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInitializedAtRow(database);
          assertEquals(Optional.empty(), SqliteStatementQueries.loadBookIdentity(database));
        });
  }

  @Test
  void loadBookIdentity_materializesEntityProfileFromBookIdentityRow() {
    Path missingEntityProfilePath =
        tempDirectory.resolve("book-identity-missing-entity-profile.sqlite");
    createSchemaOnlyBook(missingEntityProfilePath);
    withStandaloneDatabase(
        bookAccess(missingEntityProfilePath),
        database -> {
          insertInitializedAtRow(database);
          database.executeStatement(
              """
              insert into book_identity (
                  singleton_id,
                  entity_name,
                  accounting_kernel_profile,
                  accounting_basis,
                  accounting_framework_position,
                  entity_form,
                  book_template_id,
                  functional_currency_code,
                  fiscal_year_start_month,
                  fiscal_year_start_day
              ) values (
                  1,
                  'Acme Studio',
                  'internal-management-bookkeeping-kernel',
                  'CASH_BASIS',
                  'NON_STATUTORY_INTERNAL_MANAGEMENT',
                  'OWNER_MANAGED_SINGLE_ENTITY',
                  'OWNER_MANAGED_SERVICE',
                  'EUR',
                  1,
                  1
              )
              """);
          assertEquals(
              Optional.of(
                  new BookIdentity(
                      new EntityProfile(new BookEntityName("Acme Studio")),
                      BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
                      CurrencyUnit.of("EUR"),
                      FiscalYearStart.parse("01-01"))),
              SqliteStatementQueries.loadBookIdentity(database));
        });
  }

  @Test
  void lifecycleInspectionMapper_andTrialBalanceReader_requireBookIdentityMetadata() {
    Path bookPath = tempDirectory.resolve("book-identity-required-by-readers.sqlite");
    createSchemaOnlyBook(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInitializedAtRow(database);

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
  void loadBookIdentity_roundTripsNarrowDoctrineIdentity() {
    Path bookPath = tempDirectory.resolve("book-identity-narrow-doctrine.sqlite");
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));
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

  @Test
  void loadBookIdentity_roundTripsBuiltInAccountingKernelProfile() {
    Path bookPath = tempDirectory.resolve("book-identity-registered-tax-status.sqlite");
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Registered Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));
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
