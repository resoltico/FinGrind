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
    createSchemaOnlyBook(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInitializedAtRow(database);
          assertEquals(Optional.empty(), SqliteStatementQueries.loadBookIdentity(database));
        });
  }

  @Test
  void loadBookIdentity_requiresEntityProfileAndBookPolicyRows() {
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
                  functional_currency_code,
                  fiscal_year_start
              ) values (1, 'Acme Studio', 'EUR', '01-01')
              """);
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.loadBookIdentity(database));
          assertEquals(
              "Initialized SQLite book is missing entity profile.", exception.getMessage());
        });

    Path missingBookPolicyPath = tempDirectory.resolve("book-identity-missing-book-policy.sqlite");
    createSchemaOnlyBook(missingBookPolicyPath);
    withStandaloneDatabase(
        bookAccess(missingBookPolicyPath),
        database -> {
          insertInitializedAtRow(database);
          database.executeStatement(
              """
              insert into book_identity (
                  singleton_id,
                  entity_name,
                  functional_currency_code,
                  fiscal_year_start
              ) values (1, 'Acme Studio', 'EUR', '01-01')
              """);
          database.executeStatement(
              """
              insert into entity_profile (
                  singleton_id,
                  entity_form,
                  owner_model,
                  reporting_obligation_status,
                  tax_registration_status,
                  business_activity_tags
              ) values (
                  1,
                  'COMPANY',
                  'MULTI_OWNER',
                  'INTERNAL_MANAGEMENT_ONLY',
                  'UNSPECIFIED',
                  ''
              )
              """);
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.loadBookIdentity(database));
          assertEquals("Initialized SQLite book is missing book policy.", exception.getMessage());
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

  @Test
  void loadBookIdentity_roundTripsRegisteredTaxStatusWithoutTaxProfileMetadata() {
    Path bookPath = tempDirectory.resolve("book-identity-registered-tax-status.sqlite");
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Registered Studio"),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.REGISTERED,
                List.of(new BusinessActivityTag("translation-services"))),
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
