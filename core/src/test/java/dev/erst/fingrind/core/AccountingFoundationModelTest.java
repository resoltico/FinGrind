package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Covers first-class accounting-foundation value objects introduced above the raw ledger kernel.
 */
class AccountingFoundationModelTest {
  @Test
  void evidenceBundle_defensivelyCopiesNestedDocuments() {
    List<SourceDocument> documents =
        new ArrayList<>(
            List.of(
                new SourceDocument(
                    new SourceDocumentId("invoice-1"),
                    SourceDocumentType.INVOICE,
                    LocalDate.parse("2026-05-17"),
                    new SourceDocumentNumber("INV-001"),
                    Optional.of("May invoice"))));

    EvidenceBundle bundle =
        new EvidenceBundle(
            Optional.of(
                new Counterparty(
                    new CounterpartyId("customer-1"),
                    CounterpartyKind.CUSTOMER,
                    new CounterpartyName("Acme Customer"))),
            documents,
            Optional.of(
                new Approval(
                    new ActorId("manager-1"),
                    ApprovalStatus.APPROVED,
                    Optional.of(Instant.parse("2026-05-17T12:00:00Z")),
                    Optional.of("approved"))));

    documents.clear();

    assertEquals(1, bundle.sourceDocuments().size());
    assertThrows(UnsupportedOperationException.class, () -> bundle.sourceDocuments().clear());
  }

  @Test
  void bookIdentity_requiresTaxProfileConsistencyWithDeclaredTaxStatus() {
    EntityProfile registeredProfile =
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.BASIC_STANDARD_REPORTING,
            TaxRegistrationStatus.REGISTERED,
            List.of(new BusinessActivityTag("translation-services")));
    EntityProfile unregisteredProfile =
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.BASIC_STANDARD_REPORTING,
            TaxRegistrationStatus.NOT_REGISTERED,
            List.of(new BusinessActivityTag("translation-services")));
    TaxRegistration registration =
        new TaxRegistration(
            new TaxJurisdictionCode("LV"),
            new TaxRegistrationId("LV123456789"),
            TaxFilingFrequency.MONTHLY);
    TaxCodeDefinition taxCodeDefinition =
        new TaxCodeDefinition(
            new TaxCode("VAT21"),
            new TaxCodeName("Standard VAT"),
            new TaxJurisdictionCode("LV"),
            new PercentageRate(2_100),
            TaxPricingMode.EXCLUSIVE,
            TaxRecoverability.FULLY_RECOVERABLE,
            new AccountCode("2300"),
            Optional.of(new AccountCode("1300")));
    TaxProfile registeredProfileTaxFacts =
        new TaxProfile(List.of(registration), List.of(taxCodeDefinition));

    BookIdentity registeredBook =
        new BookIdentity(
            registeredProfile,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingBasis.ACCRUAL,
            registeredProfileTaxFacts);

    assertEquals(registeredProfileTaxFacts, registeredBook.taxProfile());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookIdentity(
                registeredProfile,
                CurrencyUnit.of("EUR"),
                FiscalYearStart.parse("01-01"),
                AccountingBasis.ACCRUAL,
                TaxProfile.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookIdentity(
                unregisteredProfile,
                CurrencyUnit.of("EUR"),
                FiscalYearStart.parse("01-01"),
                AccountingBasis.ACCRUAL,
                registeredProfileTaxFacts));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookIdentity(
                unregisteredProfile,
                CurrencyUnit.of("EUR"),
                FiscalYearStart.parse("01-01"),
                AccountingBasis.ACCRUAL,
                new TaxProfile(List.of(), List.of(taxCodeDefinition))));
  }

  @Test
  void taxProfile_defensivelyCopiesRegistrationsAndDefinitions() {
    List<TaxRegistration> registrations =
        new ArrayList<>(
            List.of(
                new TaxRegistration(
                    new TaxJurisdictionCode("LV"),
                    new TaxRegistrationId("LV123456789"),
                    TaxFilingFrequency.MONTHLY)));
    List<TaxCodeDefinition> definitions =
        new ArrayList<>(
            List.of(
                new TaxCodeDefinition(
                    new TaxCode("VAT21"),
                    new TaxCodeName("Standard VAT"),
                    new TaxJurisdictionCode("LV"),
                    new PercentageRate(2_100),
                    TaxPricingMode.EXCLUSIVE,
                    TaxRecoverability.FULLY_RECOVERABLE,
                    new AccountCode("2300"),
                    Optional.of(new AccountCode("1300")))));

    TaxProfile taxProfile = new TaxProfile(registrations, definitions);

    registrations.clear();
    definitions.clear();

    assertEquals(1, taxProfile.registrations().size());
    assertEquals(1, taxProfile.taxCodeDefinitions().size());
    assertThrows(UnsupportedOperationException.class, () -> taxProfile.registrations().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> taxProfile.taxCodeDefinitions().clear());
    assertEquals(EvidenceBundle.empty(), EvidenceBundle.empty());
  }

  @Test
  void taxComponent_requiresOneCurrency() {
    TaxComponent component =
        new TaxComponent(
            new TaxCode("VAT-STD"),
            new TaxJurisdictionCode("EU"),
            TaxPricingMode.EXCLUSIVE,
            TaxRecoverability.FULLY_RECOVERABLE,
            new PercentageRate(2_000),
            PositiveMoney.of(Money.parse("EUR", "100.00")),
            PositiveMoney.of(Money.parse("EUR", "20.00")));

    assertEquals(new TaxCode("VAT-STD"), component.taxCode());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TaxComponent(
                new TaxCode("VAT-STD"),
                new TaxJurisdictionCode("EU"),
                TaxPricingMode.EXCLUSIVE,
                TaxRecoverability.FULLY_RECOVERABLE,
                new PercentageRate(2_000),
                PositiveMoney.of(Money.parse("EUR", "100.00")),
                PositiveMoney.of(Money.parse("USD", "20.00"))));
  }

  @Test
  void functionalMeasurement_requiresAlignedCurrencies() {
    ExchangeRateEvidence evidence =
        new ExchangeRateEvidence(
            CurrencyUnit.of("USD"),
            CurrencyUnit.of("EUR"),
            new ExchangeRate("0.9200"),
            Instant.parse("2026-05-17T10:15:30Z"),
            ExchangeRateSourceKind.BANK,
            Optional.of("daily-fix"));

    FunctionalMeasurement measurement =
        new FunctionalMeasurement(
            Money.parse("USD", "100.00"), Money.parse("EUR", "92.00"), evidence);

    assertEquals(CurrencyUnit.of("USD"), measurement.transactionAmount().currencyUnit());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FunctionalMeasurement(
                Money.parse("GBP", "100.00"), Money.parse("EUR", "92.00"), evidence));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FunctionalMeasurement(
                Money.parse("USD", "100.00"), Money.parse("USD", "92.00"), evidence));
  }

  @Test
  void textBackedAccountingFoundationValueObjectsRejectBlank() {
    assertThrows(NullPointerException.class, () -> new CounterpartyId(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new CounterpartyId("   "));
    assertThrows(IllegalArgumentException.class, () -> new CounterpartyName("   "));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentId("   "));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentNumber("   "));
    assertThrows(IllegalArgumentException.class, () -> new TaxJurisdictionCode("   "));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationId("   "));
    assertThrows(IllegalArgumentException.class, () -> new TaxCode("   "));
    assertThrows(IllegalArgumentException.class, () -> new TaxCodeName("   "));
    assertThrows(IllegalArgumentException.class, () -> new InventoryItemId("   "));
    assertThrows(IllegalArgumentException.class, () -> new InventoryItemName("   "));
    assertThrows(IllegalArgumentException.class, () -> new MigrationBatchId("   "));
    assertThrows(IllegalArgumentException.class, () -> new OrganizationId("   "));
    assertThrows(IllegalArgumentException.class, () -> new AccountingEntityId("   "));
    assertThrows(IllegalArgumentException.class, () -> new ReportingGroupId("   "));
  }

  @Test
  void textBackedAccountingFoundationValueObjectsNormalizeAndRejectOversizedValues() {
    assertEquals("business-event-1", new BusinessEventId("  business-event-1  ").value());
    assertEquals("counterparty-1", new CounterpartyId("  counterparty-1  ").value());
    assertEquals("Acme Customer", new CounterpartyName("  Acme Customer  ").value());
    assertEquals("document-1", new SourceDocumentId("  document-1  ").value());
    assertEquals("INV-001", new SourceDocumentNumber("  INV-001  ").value());
    assertEquals("LV", new TaxJurisdictionCode("  LV  ").value());
    assertEquals("LV123456789", new TaxRegistrationId("  LV123456789  ").value());
    assertEquals("VAT21", new TaxCode("  VAT21  ").value());
    assertEquals("Standard VAT", new TaxCodeName("  Standard VAT  ").value());
    assertEquals("inventory-1", new InventoryItemId("  inventory-1  ").value());
    assertEquals("Consulting package", new InventoryItemName("  Consulting package  ").value());
    assertEquals("migration-1", new MigrationBatchId("  migration-1  ").value());
    assertEquals("org-1", new OrganizationId("  org-1  ").value());
    assertEquals("entity-1", new AccountingEntityId("  entity-1  ").value());
    assertEquals("group-1", new ReportingGroupId("  group-1  ").value());

    assertThrows(NullPointerException.class, () -> new BusinessEventId(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new BusinessEventId("   "));
    assertThrows(IllegalArgumentException.class, () -> new BusinessEventId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new CounterpartyId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new CounterpartyName("x".repeat(256)));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentNumber("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new TaxJurisdictionCode("x".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new TaxCode("x".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> new TaxCodeName("x".repeat(256)));
    assertThrows(IllegalArgumentException.class, () -> new InventoryItemId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new InventoryItemName("x".repeat(256)));
    assertThrows(IllegalArgumentException.class, () -> new MigrationBatchId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new OrganizationId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new AccountingEntityId("x".repeat(129)));
    assertThrows(IllegalArgumentException.class, () -> new ReportingGroupId("x".repeat(129)));
  }

  @Test
  void sourceEvidenceModelsNormalizeOptionalTextAndRejectInvalidOptionalText() {
    SourceDocument sourceDocument =
        new SourceDocument(
            new SourceDocumentId("invoice-1"),
            SourceDocumentType.INVOICE,
            LocalDate.parse("2026-05-17"),
            new SourceDocumentNumber("INV-001"),
            Optional.of("  May invoice  "));
    Approval approval =
        new Approval(
            new ActorId("manager-1"),
            ApprovalStatus.APPROVED,
            Optional.of(Instant.parse("2026-05-17T12:00:00Z")),
            Optional.of("  approved  "));
    ExchangeRateEvidence exchangeRateEvidence =
        new ExchangeRateEvidence(
            CurrencyUnit.of("USD"),
            CurrencyUnit.of("EUR"),
            new ExchangeRate("0.9200"),
            Instant.parse("2026-05-17T10:15:30Z"),
            ExchangeRateSourceKind.BANK,
            Optional.of("  daily-fix  "));

    assertEquals(Optional.of("May invoice"), sourceDocument.description());
    assertEquals(Optional.of("approved"), approval.note());
    assertEquals(Optional.of("daily-fix"), exchangeRateEvidence.sourceReference());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceDocument(
                new SourceDocumentId("invoice-1"),
                SourceDocumentType.INVOICE,
                LocalDate.parse("2026-05-17"),
                new SourceDocumentNumber("INV-001"),
                Optional.of("   ")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceDocument(
                new SourceDocumentId("invoice-1"),
                SourceDocumentType.INVOICE,
                LocalDate.parse("2026-05-17"),
                new SourceDocumentNumber("INV-001"),
                Optional.of("x".repeat(513))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Approval(
                new ActorId("manager-1"),
                ApprovalStatus.APPROVED,
                Optional.of(Instant.parse("2026-05-17T12:00:00Z")),
                Optional.of("   ")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Approval(
                new ActorId("manager-1"),
                ApprovalStatus.APPROVED,
                Optional.of(Instant.parse("2026-05-17T12:00:00Z")),
                Optional.of("x".repeat(513))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExchangeRateEvidence(
                CurrencyUnit.of("USD"),
                CurrencyUnit.of("EUR"),
                new ExchangeRate("0.9200"),
                Instant.parse("2026-05-17T10:15:30Z"),
                ExchangeRateSourceKind.BANK,
                Optional.of("   ")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExchangeRateEvidence(
                CurrencyUnit.of("USD"),
                CurrencyUnit.of("EUR"),
                new ExchangeRate("0.9200"),
                Instant.parse("2026-05-17T10:15:30Z"),
                ExchangeRateSourceKind.BANK,
                Optional.of("x".repeat(256))));
  }

  @Test
  void accountingFoundationRangeAndStructureObjectsValidateSupportedShapes() {
    TaxRegistration registration =
        new TaxRegistration(
            new TaxJurisdictionCode("LV"),
            new TaxRegistrationId("LV123456789"),
            TaxFilingFrequency.MONTHLY);
    TaxCodeDefinition definition =
        new TaxCodeDefinition(
            new TaxCode("VAT21"),
            new TaxCodeName("Standard VAT"),
            new TaxJurisdictionCode("LV"),
            new PercentageRate(2_100),
            TaxPricingMode.EXCLUSIVE,
            TaxRecoverability.FULLY_RECOVERABLE,
            new AccountCode("2300"),
            Optional.of(new AccountCode("1300")));
    PercentageRate zeroRate = new PercentageRate(0);
    PercentageRate standardRate = new PercentageRate(2_100);
    InventoryQuantity quantity = new InventoryQuantity(5);
    ExchangeRate exchangeRate = new ExchangeRate("1.2500");

    assertEquals(new TaxJurisdictionCode("LV"), registration.jurisdictionCode());
    assertEquals(new TaxCode("VAT21"), definition.taxCode());
    assertEquals(0, zeroRate.basisPoints());
    assertEquals(2_100, standardRate.basisPoints());
    assertEquals(5L, quantity.units());
    assertEquals("1.25", exchangeRate.value());
    assertEquals("1.25", exchangeRate.canonicalDecimal());
    assertThrows(IllegalArgumentException.class, () -> new InventoryQuantity(0));
    assertThrows(IllegalArgumentException.class, () -> new InventoryQuantity(-1));
    assertThrows(IllegalArgumentException.class, () -> new PercentageRate(-1));
    assertThrows(IllegalArgumentException.class, () -> new PercentageRate(1_000_001));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("0"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("-1.00"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate(" 1.25 "));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1.2e0"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("01.25"));
  }

  @Test
  void exchangeRate_enforcesCanonicalPlainDecimalGrammar() {
    assertEquals("12", new ExchangeRate("12.000").canonicalDecimal());
    assertEquals("0.0045", new ExchangeRate("0.004500").canonicalDecimal());
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate(""));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("+1"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate(".5"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("/5"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1."));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1..2"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1.2E0"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1a"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1./"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("1.2a"));
    assertThrows(IllegalArgumentException.class, () -> new ExchangeRate("0.0000"));
  }

  @Test
  void accountingFoundationWireVocabulariesAreStable() {
    assertEquals(
        List.of(
            "ISSUE_INVOICE",
            "RECEIVE_PAYMENT",
            "RECORD_BILL",
            "PAY_BILL",
            "RECORD_SALE",
            "PURCHASE_INVENTORY",
            "RECORD_PAYROLL_RUN",
            "RECORD_OWNER_DRAW",
            "RECORD_BANK_FEE",
            "RECORD_TAX_PAYMENT",
            "OPENING_BALANCE_STATEMENT",
            "PRIOR_PERIOD_ADJUSTMENT"),
        BusinessEventKind.wireValues());
    assertEquals(BusinessEventKind.RECORD_SALE, BusinessEventKind.fromWireValue("RECORD_SALE"));
    assertWireRoundTrips(
        BusinessEventStatus.values(),
        BusinessEventStatus::fromWireValue,
        BusinessEventStatus::wireValues);
    assertWireRoundTrips(
        CashFlowActivity.values(), CashFlowActivity::fromWireValue, CashFlowActivity::wireValues);
    assertWireRoundTrips(
        CounterpartyKind.values(), CounterpartyKind::fromWireValue, CounterpartyKind::wireValues);
    assertWireRoundTrips(
        SourceDocumentType.values(),
        SourceDocumentType::fromWireValue,
        SourceDocumentType::wireValues);
    assertWireRoundTrips(
        ApprovalStatus.values(), ApprovalStatus::fromWireValue, ApprovalStatus::wireValues);
    assertWireRoundTrips(
        TaxFilingFrequency.values(),
        TaxFilingFrequency::fromWireValue,
        TaxFilingFrequency::wireValues);
    assertWireRoundTrips(
        TaxPricingMode.values(), TaxPricingMode::fromWireValue, TaxPricingMode::wireValues);
    assertWireRoundTrips(
        TaxRecoverability.values(),
        TaxRecoverability::fromWireValue,
        TaxRecoverability::wireValues);
    assertWireRoundTrips(
        ExchangeRateSourceKind.values(),
        ExchangeRateSourceKind::fromWireValue,
        ExchangeRateSourceKind::wireValues);
    assertWireRoundTrips(
        OtherComprehensiveIncomeClassification.values(),
        OtherComprehensiveIncomeClassification::fromWireValue,
        OtherComprehensiveIncomeClassification::wireValues);
    assertWireRoundTrips(
        DisclosureNoteKind.values(),
        DisclosureNoteKind::fromWireValue,
        DisclosureNoteKind::wireValues);
    assertWireRoundTrips(
        PriorPeriodAdjustmentKind.values(),
        PriorPeriodAdjustmentKind::fromWireValue,
        PriorPeriodAdjustmentKind::wireValues);
  }

  private static <E extends Enum<E> & WireValue> void assertWireRoundTrips(
      E[] values, Function<String, E> parser, Supplier<List<String>> wireValuesSupplier) {
    for (E value : values) {
      assertEquals(value, parser.apply(value.wireValue()));
    }
    assertEquals(values.length, wireValuesSupplier.get().size());
  }
}
