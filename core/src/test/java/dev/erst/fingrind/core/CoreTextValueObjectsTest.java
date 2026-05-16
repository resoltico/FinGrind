package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers boundary validation for core text-backed semantic value objects. */
class CoreTextValueObjectsTest {
  @Test
  void accountName_stripsWhitespaceAndRejectsBlank() {
    assertEquals("Cash", new AccountName("  Cash  ").value());
    assertThrows(IllegalArgumentException.class, () -> new AccountName("   "));
  }

  @Test
  void actorId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("actor-1", new ActorId("  actor-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new ActorId("   "));
  }

  @Test
  void causationId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("cause-1", new CausationId("  cause-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CausationId("   "));
  }

  @Test
  void commandId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("command-1", new CommandId("  command-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CommandId("   "));
  }

  @Test
  void correlationId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("corr-1", new CorrelationId("  corr-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CorrelationId("   "));
  }

  @Test
  void reversalReason_stripsWhitespaceAndRejectsBlank() {
    assertEquals("operator reversal", new ReversalReason("  operator reversal  ").value());
    assertThrows(IllegalArgumentException.class, () -> new ReversalReason("   "));
  }

  @Test
  void bookEntityName_stripsWhitespaceAndRejectsBlankOrOversizedValues() {
    assertEquals("Acme Studio", new BookEntityName("  Acme Studio  ").value());
    assertThrows(NullPointerException.class, () -> new BookEntityName(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new BookEntityName("   "));
    assertThrows(IllegalArgumentException.class, () -> new BookEntityName("x".repeat(256)));
    assertNotEquals("  Acme Studio  ", new BookEntityName("  Acme Studio  ").value());
  }

  @Test
  void businessActivityTag_stripsWhitespaceAndRejectsBlank() {
    assertEquals(
        "translation-services", new BusinessActivityTag("  translation-services  ").value());
    assertThrows(NullPointerException.class, () -> new BusinessActivityTag(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new BusinessActivityTag("   "));
  }

  @Test
  void entityProfile_requiresAllFieldsAndDefensivelyCopiesActivityTags() {
    BookEntityName displayName = new BookEntityName("Acme Studio");
    List<BusinessActivityTag> tags =
        new ArrayList<>(List.of(new BusinessActivityTag("translation-services")));

    EntityProfile profile =
        new EntityProfile(
            displayName,
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.BASIC_STANDARD_REPORTING,
            TaxRegistrationStatus.REGISTERED,
            tags);

    assertEquals(displayName, profile.displayName());
    assertEquals(EntityForm.COMPANY, profile.entityForm());
    assertEquals(OwnerModel.MULTI_OWNER, profile.ownerModel());
    assertEquals(
        ReportingObligationStatus.BASIC_STANDARD_REPORTING, profile.reportingObligationStatus());
    assertEquals(TaxRegistrationStatus.REGISTERED, profile.taxRegistrationStatus());
    assertEquals(
        List.of(new BusinessActivityTag("translation-services")), profile.businessActivityTags());
    tags.add(new BusinessActivityTag("consulting"));
    assertEquals(1, profile.businessActivityTags().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> profile.businessActivityTags().add(new BusinessActivityTag("forbidden")));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                nullOf(),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.BASIC_STANDARD_REPORTING,
                TaxRegistrationStatus.REGISTERED,
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                displayName,
                nullOf(),
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.BASIC_STANDARD_REPORTING,
                TaxRegistrationStatus.REGISTERED,
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                displayName,
                EntityForm.COMPANY,
                nullOf(),
                ReportingObligationStatus.BASIC_STANDARD_REPORTING,
                TaxRegistrationStatus.REGISTERED,
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                displayName,
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                nullOf(),
                TaxRegistrationStatus.REGISTERED,
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                displayName,
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.BASIC_STANDARD_REPORTING,
                nullOf(),
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new EntityProfile(
                displayName,
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.BASIC_STANDARD_REPORTING,
                TaxRegistrationStatus.REGISTERED,
                nullOf()));
  }

  @Test
  void bookIdentity_requiresEveryConstituentValueObject() {
    BookEntityName entityName = new BookEntityName("Acme Studio");
    CurrencyUnit functionalCurrency = CurrencyUnit.of("EUR");
    FiscalYearStart fiscalYearStart = FiscalYearStart.parse("01-01");
    EntityProfile entityProfile =
        new EntityProfile(
            entityName,
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
            TaxRegistrationStatus.UNSPECIFIED,
            List.of());

    BookIdentity bookIdentity =
        new BookIdentity(
            entityProfile, functionalCurrency, fiscalYearStart, AccountingBasis.ACCRUAL);

    assertEquals(entityName, bookIdentity.entityName());
    assertEquals(EntityForm.COMPANY, bookIdentity.entityForm());
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                nullOf(), functionalCurrency, fiscalYearStart, AccountingBasis.ACCRUAL));
    assertThrows(
        NullPointerException.class,
        () -> new BookIdentity(entityProfile, nullOf(), fiscalYearStart, AccountingBasis.ACCRUAL));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(entityProfile, functionalCurrency, nullOf(), AccountingBasis.ACCRUAL));
    assertThrows(
        NullPointerException.class,
        () -> new BookIdentity(entityProfile, functionalCurrency, fiscalYearStart, nullOf()));
  }

  @Test
  void entityAndOwnerWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals("FREELANCER", EntityForm.FREELANCER.wireValue());
    assertEquals("SOLE_PROPRIETORSHIP", EntityForm.SOLE_PROPRIETORSHIP.wireValue());
    assertEquals("COMPANY", EntityForm.COMPANY.wireValue());
    assertEquals("PARTNERSHIP", EntityForm.PARTNERSHIP.wireValue());
    assertEquals("NONPROFIT", EntityForm.NONPROFIT.wireValue());
    assertEquals("BRANCH", EntityForm.BRANCH.wireValue());
    assertEquals("OTHER", EntityForm.OTHER.wireValue());
    assertEquals(EntityForm.FREELANCER, EntityForm.fromWireValue("FREELANCER"));
    assertEquals(EntityForm.OTHER, EntityForm.fromWireValue("OTHER"));
    assertEquals(
        List.of(
            "FREELANCER",
            "SOLE_PROPRIETORSHIP",
            "COMPANY",
            "PARTNERSHIP",
            "NONPROFIT",
            "BRANCH",
            "OTHER"),
        EntityForm.wireValues());
    assertThrows(IllegalArgumentException.class, () -> EntityForm.fromWireValue("freelancer"));

    assertEquals("SOLE_OWNER", OwnerModel.SOLE_OWNER.wireValue());
    assertEquals("MULTI_OWNER", OwnerModel.MULTI_OWNER.wireValue());
    assertEquals("MEMBERSHIP_BODY", OwnerModel.MEMBERSHIP_BODY.wireValue());
    assertEquals("NO_PRIVATE_OWNER", OwnerModel.NO_PRIVATE_OWNER.wireValue());
    assertEquals("UNKNOWN", OwnerModel.UNKNOWN.wireValue());
    assertEquals(OwnerModel.SOLE_OWNER, OwnerModel.fromWireValue("SOLE_OWNER"));
    assertEquals(OwnerModel.UNKNOWN, OwnerModel.fromWireValue("UNKNOWN"));
    assertEquals(
        List.of("SOLE_OWNER", "MULTI_OWNER", "MEMBERSHIP_BODY", "NO_PRIVATE_OWNER", "UNKNOWN"),
        OwnerModel.wireValues());
    assertThrows(IllegalArgumentException.class, () -> OwnerModel.fromWireValue("sole_owner"));
  }

  @Test
  void reportingBasisAndTaxWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals(
        "INTERNAL_MANAGEMENT_ONLY", ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY.wireValue());
    assertEquals(
        "BASIC_STANDARD_REPORTING", ReportingObligationStatus.BASIC_STANDARD_REPORTING.wireValue());
    assertEquals(
        "EXTERNAL_COMPLIANCE_PACK_REQUIRED",
        ReportingObligationStatus.EXTERNAL_COMPLIANCE_PACK_REQUIRED.wireValue());
    assertEquals("UNSPECIFIED", ReportingObligationStatus.UNSPECIFIED.wireValue());
    assertEquals(
        ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
        ReportingObligationStatus.fromWireValue("INTERNAL_MANAGEMENT_ONLY"));
    assertEquals(
        ReportingObligationStatus.UNSPECIFIED,
        ReportingObligationStatus.fromWireValue("UNSPECIFIED"));
    assertEquals(
        List.of(
            "INTERNAL_MANAGEMENT_ONLY",
            "BASIC_STANDARD_REPORTING",
            "EXTERNAL_COMPLIANCE_PACK_REQUIRED",
            "UNSPECIFIED"),
        ReportingObligationStatus.wireValues());
    assertThrows(
        IllegalArgumentException.class,
        () -> ReportingObligationStatus.fromWireValue("internal-management-only"));

    assertEquals("CASH", AccountingBasis.CASH.wireValue());
    assertEquals("ACCRUAL", AccountingBasis.ACCRUAL.wireValue());
    assertEquals("HYBRID_POLICY_DEFINED", AccountingBasis.HYBRID_POLICY_DEFINED.wireValue());
    assertEquals("EXTENSION_DEFINED", AccountingBasis.EXTENSION_DEFINED.wireValue());
    assertEquals(AccountingBasis.CASH, AccountingBasis.fromWireValue("CASH"));
    assertEquals(
        AccountingBasis.EXTENSION_DEFINED, AccountingBasis.fromWireValue("EXTENSION_DEFINED"));
    assertEquals(
        List.of("CASH", "ACCRUAL", "HYBRID_POLICY_DEFINED", "EXTENSION_DEFINED"),
        AccountingBasis.wireValues());
    assertThrows(IllegalArgumentException.class, () -> AccountingBasis.fromWireValue("cash"));

    assertEquals("REGISTERED", TaxRegistrationStatus.REGISTERED.wireValue());
    assertEquals("NOT_REGISTERED", TaxRegistrationStatus.NOT_REGISTERED.wireValue());
    assertEquals("UNSPECIFIED", TaxRegistrationStatus.UNSPECIFIED.wireValue());
    assertEquals(
        TaxRegistrationStatus.REGISTERED, TaxRegistrationStatus.fromWireValue("REGISTERED"));
    assertEquals(
        TaxRegistrationStatus.UNSPECIFIED, TaxRegistrationStatus.fromWireValue("UNSPECIFIED"));
    assertEquals(
        List.of("REGISTERED", "NOT_REGISTERED", "UNSPECIFIED"), TaxRegistrationStatus.wireValues());
    assertThrows(
        IllegalArgumentException.class, () -> TaxRegistrationStatus.fromWireValue("registered"));
  }

  @Test
  void financialPositionLineClassificationWireVocabularyAndAccountTypesAreStable() {
    assertEquals(
        List.of(
            "CURRENT_ASSET",
            "NONCURRENT_ASSET",
            "CURRENT_LIABILITY",
            "NONCURRENT_LIABILITY",
            "OWNER_CAPITAL",
            "OWNER_DRAWINGS",
            "PARTNER_CAPITAL",
            "PARTNER_CURRENT",
            "SHARE_CAPITAL",
            "RETAINED_EARNINGS",
            "ACCUMULATED_SURPLUS",
            "RESERVE",
            "CURRENT_PERIOD_RESULT",
            "OTHER_EQUITY"),
        FinancialPositionLineClassification.wireValues());
    for (FinancialPositionLineClassification classification :
        FinancialPositionLineClassification.values()) {
      assertEquals(
          classification,
          FinancialPositionLineClassification.fromWireValue(classification.wireValue()));
    }
    assertEquals(
        AccountType.ASSET, FinancialPositionLineClassification.CURRENT_ASSET.accountType());
    assertEquals(
        AccountType.ASSET, FinancialPositionLineClassification.NONCURRENT_ASSET.accountType());
    assertEquals(
        AccountType.LIABILITY, FinancialPositionLineClassification.CURRENT_LIABILITY.accountType());
    assertEquals(
        AccountType.LIABILITY,
        FinancialPositionLineClassification.NONCURRENT_LIABILITY.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.OWNER_CAPITAL.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.OWNER_DRAWINGS.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.PARTNER_CAPITAL.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.PARTNER_CURRENT.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.SHARE_CAPITAL.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.RETAINED_EARNINGS.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.ACCUMULATED_SURPLUS.accountType());
    assertEquals(AccountType.EQUITY, FinancialPositionLineClassification.RESERVE.accountType());
    assertEquals(
        AccountType.EQUITY,
        FinancialPositionLineClassification.CURRENT_PERIOD_RESULT.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.OTHER_EQUITY.accountType());
    assertThrows(
        IllegalArgumentException.class,
        () -> FinancialPositionLineClassification.fromWireValue("current-asset"));
  }

  @Test
  void profitAndLossLineClassificationWireVocabularyAndAccountTypesAreStable() {
    assertEquals(
        List.of(
            "OPERATING_REVENUE",
            "OTHER_REVENUE",
            "FINANCE_INCOME",
            "COST_OF_SALES",
            "OPERATING_EXPENSE",
            "DEPRECIATION_AND_AMORTIZATION",
            "FINANCE_EXPENSE",
            "TAX_EXPENSE"),
        ProfitAndLossLineClassification.wireValues());
    for (ProfitAndLossLineClassification classification :
        ProfitAndLossLineClassification.values()) {
      assertEquals(
          classification,
          ProfitAndLossLineClassification.fromWireValue(classification.wireValue()));
    }
    assertEquals(
        AccountType.REVENUE, ProfitAndLossLineClassification.OPERATING_REVENUE.accountType());
    assertEquals(AccountType.REVENUE, ProfitAndLossLineClassification.OTHER_REVENUE.accountType());
    assertEquals(AccountType.REVENUE, ProfitAndLossLineClassification.FINANCE_INCOME.accountType());
    assertEquals(AccountType.EXPENSE, ProfitAndLossLineClassification.COST_OF_SALES.accountType());
    assertEquals(
        AccountType.EXPENSE, ProfitAndLossLineClassification.OPERATING_EXPENSE.accountType());
    assertEquals(
        AccountType.EXPENSE,
        ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION.accountType());
    assertEquals(
        AccountType.EXPENSE, ProfitAndLossLineClassification.FINANCE_EXPENSE.accountType());
    assertEquals(AccountType.EXPENSE, ProfitAndLossLineClassification.TAX_EXPENSE.accountType());
    assertThrows(
        IllegalArgumentException.class,
        () -> ProfitAndLossLineClassification.fromWireValue("operating-revenue"));
  }

  @Test
  void statementLineKindWireVocabularyIsStable() {
    assertEquals(
        List.of("DECLARED_ACCOUNT", "CURRENT_PERIOD_RESULT"), StatementLineKind.wireValues());
    assertEquals(
        StatementLineKind.DECLARED_ACCOUNT,
        StatementLineKind.fromWireValue(StatementLineKind.DECLARED_ACCOUNT.wireValue()));
    assertEquals(
        StatementLineKind.CURRENT_PERIOD_RESULT,
        StatementLineKind.fromWireValue(StatementLineKind.CURRENT_PERIOD_RESULT.wireValue()));
    assertThrows(
        IllegalArgumentException.class, () -> StatementLineKind.fromWireValue("declared-account"));
  }

  @Test
  void fiscalYearStart_parsesCanonicalWireValuesAndTracksContainingFiscalYear() {
    FiscalYearStart januaryStart = FiscalYearStart.parse("01-01");
    FiscalYearStart aprilStart = FiscalYearStart.parse("04-01");
    FiscalYearStart leapDayStart = FiscalYearStart.parse("02-29");

    assertEquals("01-01", januaryStart.wireValue());
    assertEquals(java.time.MonthDay.of(1, 1), januaryStart.monthDay());
    assertEquals("04-01", aprilStart.toString());
    assertEquals(
        LocalDate.parse("2025-04-01"),
        aprilStart.containingFiscalYearStart(LocalDate.parse("2026-03-31")));
    assertEquals(
        LocalDate.parse("2026-04-01"),
        aprilStart.containingFiscalYearStart(LocalDate.parse("2026-04-01")));
    assertTrue(
        aprilStart.containsSingleFiscalYear(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2027-03-31")));
    assertFalse(
        aprilStart.containsSingleFiscalYear(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2027-04-01")));
    assertEquals(
        LocalDate.parse("2025-02-28"),
        leapDayStart.containingFiscalYearStart(LocalDate.parse("2025-03-01")));
    assertEquals(
        LocalDate.parse("2024-02-29"),
        leapDayStart.containingFiscalYearStart(LocalDate.parse("2024-03-01")));
    assertThrows(NullPointerException.class, () -> FiscalYearStart.parse(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> FiscalYearStart.parse("1-1"));
    assertThrows(IllegalArgumentException.class, () -> FiscalYearStart.parse("13-01"));
    assertThrows(NullPointerException.class, () -> aprilStart.containingFiscalYearStart(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> aprilStart.containsSingleFiscalYear(nullOf(), LocalDate.parse("2026-04-01")));
    assertThrows(
        NullPointerException.class,
        () -> aprilStart.containsSingleFiscalYear(LocalDate.parse("2026-04-01"), nullOf()));
  }

  @Test
  void coreWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals("DEBIT", NormalBalance.DEBIT.wireValue());
    assertEquals("CREDIT", NormalBalance.CREDIT.wireValue());
    assertEquals(NormalBalance.DEBIT, NormalBalance.fromWireValue("DEBIT"));
    assertEquals(NormalBalance.CREDIT, NormalBalance.fromWireValue("CREDIT"));
    assertEquals(java.util.List.of("DEBIT", "CREDIT"), NormalBalance.wireValues());
    assertThrows(IllegalArgumentException.class, () -> NormalBalance.fromWireValue("debit"));

    assertEquals("DEBIT", BalanceSide.DEBIT.wireValue());
    assertEquals("CREDIT", BalanceSide.CREDIT.wireValue());
    assertEquals("ZERO", BalanceSide.ZERO.wireValue());
    assertEquals(BalanceSide.DEBIT, BalanceSide.fromWireValue("DEBIT"));
    assertEquals(BalanceSide.CREDIT, BalanceSide.fromWireValue("CREDIT"));
    assertEquals(BalanceSide.ZERO, BalanceSide.fromWireValue("ZERO"));
    assertEquals(java.util.List.of("DEBIT", "CREDIT", "ZERO"), BalanceSide.wireValues());
    assertThrows(IllegalArgumentException.class, () -> BalanceSide.fromWireValue("debit"));

    assertEquals("HUMAN", ActorType.HUMAN.wireValue());
    assertEquals("SYSTEM", ActorType.SYSTEM.wireValue());
    assertEquals("AGENT", ActorType.AGENT.wireValue());
    assertEquals(java.util.List.of("HUMAN", "SYSTEM", "AGENT"), ActorType.wireValues());
    assertEquals(ActorType.HUMAN, ActorType.fromWireValue("HUMAN"));
    assertEquals(ActorType.SYSTEM, ActorType.fromWireValue("SYSTEM"));
    assertEquals(ActorType.AGENT, ActorType.fromWireValue("AGENT"));
    assertThrows(IllegalArgumentException.class, () -> ActorType.fromWireValue("ROBOT"));

    assertEquals("CLI", SourceChannel.CLI.wireValue());
    assertEquals("CLI", SourceChannel.CLI.toString());
    assertEquals("SYSTEM", SourceChannel.SYSTEM.wireValue());
    assertEquals("SYSTEM", SourceChannel.SYSTEM.toString());
    assertEquals(java.util.List.of("CLI", "SYSTEM"), SourceChannel.wireValues());
    assertArrayEquals(
        new SourceChannel[] {SourceChannel.CLI, SourceChannel.SYSTEM}, SourceChannel.values());
    assertEquals(SourceChannel.CLI, SourceChannel.fromWireValue("CLI"));
    assertEquals(SourceChannel.SYSTEM, SourceChannel.fromWireValue("SYSTEM"));
    assertThrows(IllegalArgumentException.class, () -> SourceChannel.fromWireValue("API"));
    assertThrows(NullPointerException.class, () -> SourceChannel.fromWireValue(nullOf()));

    assertEquals("DEBIT", JournalLine.EntrySide.DEBIT.wireValue());
    assertEquals("CREDIT", JournalLine.EntrySide.CREDIT.wireValue());
    assertEquals(JournalLine.EntrySide.DEBIT, JournalLine.EntrySide.fromWireValue("DEBIT"));
    assertEquals(JournalLine.EntrySide.CREDIT, JournalLine.EntrySide.fromWireValue("CREDIT"));
    assertEquals(java.util.List.of("DEBIT", "CREDIT"), JournalLine.EntrySide.wireValues());
    assertThrows(IllegalArgumentException.class, () -> JournalLine.EntrySide.fromWireValue("LEFT"));
  }

  @Test
  void accountTaxonomyWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals("ASSET", AccountType.ASSET.wireValue());
    assertEquals("LIABILITY", AccountType.LIABILITY.wireValue());
    assertEquals("EQUITY", AccountType.EQUITY.wireValue());
    assertEquals("REVENUE", AccountType.REVENUE.wireValue());
    assertEquals("EXPENSE", AccountType.EXPENSE.wireValue());
    assertEquals(AccountType.ASSET, AccountType.fromWireValue("ASSET"));
    assertEquals(AccountType.EXPENSE, AccountType.fromWireValue("EXPENSE"));
    assertEquals(
        java.util.List.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"),
        AccountType.wireValues());
    assertThrows(IllegalArgumentException.class, () -> AccountType.fromWireValue("asset"));

    assertEquals("ORDINARY", AccountRole.ORDINARY.wireValue());
    assertEquals("CONTRA", AccountRole.CONTRA.wireValue());
    assertEquals("CONTRA", AccountRole.CONTRA.wireValue());
    assertEquals(AccountRole.ORDINARY, AccountRole.fromWireValue("ORDINARY"));
    assertEquals(AccountRole.CONTRA, AccountRole.fromWireValue("CONTRA"));
    assertEquals(java.util.List.of("ORDINARY", "CONTRA"), AccountRole.wireValues());
    assertThrows(IllegalArgumentException.class, () -> AccountRole.fromWireValue("ordinary"));
  }

  @Test
  void postingKindWireVocabularyAndClassificationRemainStable() {
    assertEquals("STANDARD", PostingKind.STANDARD.wireValue());
    assertEquals("OPENING_BALANCE", PostingKind.OPENING_BALANCE.wireValue());
    assertEquals("PERIOD_CLOSE", PostingKind.PERIOD_CLOSE.wireValue());
    assertEquals(PostingKind.STANDARD, PostingKind.fromWireValue("STANDARD"));
    assertEquals(PostingKind.OPENING_BALANCE, PostingKind.fromWireValue("OPENING_BALANCE"));
    assertEquals(PostingKind.PERIOD_CLOSE, PostingKind.fromWireValue("PERIOD_CLOSE"));
    assertEquals(
        java.util.List.of("STANDARD", "OPENING_BALANCE", "PERIOD_CLOSE"), PostingKind.wireValues());
    assertEquals(
        java.util.List.of("STANDARD", "OPENING_BALANCE"), PostingKind.callerSelectableWireValues());
    assertThrows(IllegalArgumentException.class, () -> PostingKind.fromWireValue("CLOSING"));
    assertTrue(PostingKind.STANDARD.isStandard());
    assertTrue(PostingKind.STANDARD.isCallerSelectable());
    assertTrue(PostingKind.OPENING_BALANCE.isOpeningBalance());
    assertTrue(PostingKind.OPENING_BALANCE.isCallerSelectable());
    assertTrue(PostingKind.PERIOD_CLOSE.isGenerated());
    assertFalse(PostingKind.PERIOD_CLOSE.isStandard());
    assertFalse(PostingKind.PERIOD_CLOSE.isOpeningBalance());
    assertFalse(PostingKind.PERIOD_CLOSE.isCallerSelectable());
  }

  @Test
  void postingCoverageWireVocabularyAndMembershipRemainStable() {
    assertEquals("all-posting-kinds", PostingCoverage.ALL_POSTING_KINDS.wireValue());
    assertEquals("non-closing-postings", PostingCoverage.NON_CLOSING_POSTINGS.wireValue());
    assertEquals(
        java.util.List.of("all-posting-kinds", "non-closing-postings"),
        PostingCoverage.wireValues());
    assertEquals(
        PostingCoverage.ALL_POSTING_KINDS, PostingCoverage.fromWireValue("all-posting-kinds"));
    assertEquals(
        PostingCoverage.NON_CLOSING_POSTINGS,
        PostingCoverage.fromWireValue("non-closing-postings"));
    assertThrows(IllegalArgumentException.class, () -> PostingCoverage.fromWireValue("all"));
    assertThrows(NullPointerException.class, () -> PostingCoverage.fromWireValue(nullOf()));

    assertFalse(PostingCoverage.ALL_POSTING_KINDS.isNonClosingOnly());
    assertTrue(PostingCoverage.NON_CLOSING_POSTINGS.isNonClosingOnly());
    assertTrue(PostingCoverage.ALL_POSTING_KINDS.includes(PostingKind.STANDARD));
    assertTrue(PostingCoverage.ALL_POSTING_KINDS.includes(PostingKind.OPENING_BALANCE));
    assertTrue(PostingCoverage.ALL_POSTING_KINDS.includes(PostingKind.PERIOD_CLOSE));
    assertTrue(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.STANDARD));
    assertTrue(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.OPENING_BALANCE));
    assertFalse(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.PERIOD_CLOSE));
    assertThrows(
        NullPointerException.class, () -> PostingCoverage.ALL_POSTING_KINDS.includes(nullOf()));
  }
}
