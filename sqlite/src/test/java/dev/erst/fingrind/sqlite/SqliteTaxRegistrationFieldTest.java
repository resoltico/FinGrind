package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.TaxAdministrationService;
import dev.erst.fingrind.executor.TaxReadService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Exercises tax-registration declaration, lookup, complete listing, and cursor pagination in
 * SQLite.
 */
class SqliteTaxRegistrationFieldTest extends SqlitePostingFactStoreTestSupport {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void registrations_areAttestedAndReadableThroughEverySQLiteCatalogSurface() {
    Path bookPath = tempDirectory.resolve("tax-registrations.sqlite");
    SqlitePostingFactStore store = openStore(bookAccess(bookPath));
    try (SqlitePostingSession session = SqliteCapabilitySessions.posting(store)) {
      session.openAttestedBook(
          CLOCK.instant(),
          bookIdentity(),
          List.of(),
          SqliteAttestationTestSupport.genesis(bookIdentity(), CLOCK.instant()));
      declareTaxControlAccounts(session);
      TaxAdministrationService administration =
          new TaxAdministrationService(session, session, session, CLOCK);
      DeclaredTaxRegistration latvia =
          assertInstanceOf(
                  DeclareTaxRegistrationResult.Declared.class,
                  administration.declareTaxRegistration(
                      registration("vat-lv", "Latvia VAT", "LV", "vat-standard-sale"),
                      SqliteAttestationTestSupport.authorizer()))
              .registration();
      DeclaredTaxRegistration estonia =
          assertInstanceOf(
                  DeclareTaxRegistrationResult.Declared.class,
                  administration.declareTaxRegistration(
                      registration("vat-ee", "Estonia VAT", "EE", "vat-standard-sale-ee"),
                      SqliteAttestationTestSupport.authorizer()))
              .registration();

      assertEquals(Optional.of(latvia), session.findTaxRegistration(latvia.taxRegistrationId()));
      assertEquals(Optional.of(estonia), session.findTaxRegistration(estonia.taxRegistrationId()));
      assertEquals(Optional.empty(), session.findTaxRegistration(new TaxRegistrationId("missing")));
      assertEquals(2, session.allTaxRegistrations().size());

      TaxReadService reads = new TaxReadService(session);
      ListTaxRegistrationsResult.Listed firstPage =
          assertInstanceOf(
              ListTaxRegistrationsResult.Listed.class,
              reads.listTaxRegistrations(new ListTaxRegistrationsQuery(1, Optional.empty())));
      assertEquals(1, firstPage.page().registrations().size());
      assertTrue(firstPage.page().nextCursor().isPresent());
      ListTaxRegistrationsResult.Listed finalPage =
          assertInstanceOf(
              ListTaxRegistrationsResult.Listed.class,
              reads.listTaxRegistrations(
                  new ListTaxRegistrationsQuery(1, firstPage.page().nextCursor())));
      assertEquals(1, finalPage.page().registrations().size());
      assertFalse(finalPage.page().nextCursor().isPresent());
      assertEquals(
          List.of("vat-ee", "vat-lv"),
          session.allTaxRegistrations().stream()
              .map(registration -> registration.taxRegistrationId().value())
              .toList());
    }
  }

  private static void declareTaxControlAccounts(SqlitePostingSession session) {
    declare(
        session,
        new AccountCode("2100"),
        "Tax payable",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
    declare(
        session,
        new AccountCode("1300"),
        "Tax recoverable",
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET));
  }

  private static void declare(
      SqlitePostingSession session,
      AccountCode code,
      String name,
      AccountType type,
      dev.erst.fingrind.core.AccountTaxonomy taxonomy) {
    assertInstanceOf(
        dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared.class,
        session.declareAccount(
            new AccountDeclaration(code, new AccountName(name), type, taxonomy),
            CLOCK.instant(),
            SqliteAttestationTestSupport.authorizer()));
  }

  private static DeclareTaxRegistrationCommand registration(
      String identifier, String name, String jurisdiction, String taxCode) {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId(identifier),
        new TaxRegistrationName(name),
        new TaxJurisdiction(jurisdiction),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode(taxCode),
                new TaxCodeName("Standard VAT"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }
}
