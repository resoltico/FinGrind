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

    EntityProfile profile = new EntityProfile(displayName, tags);

    assertEquals(displayName, profile.displayName());
    assertEquals(
        List.of(new BusinessActivityTag("translation-services")), profile.businessActivityTags());
    tags.add(new BusinessActivityTag("consulting"));
    assertEquals(1, profile.businessActivityTags().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> profile.businessActivityTags().add(new BusinessActivityTag("forbidden")));
    assertThrows(NullPointerException.class, () -> new EntityProfile(nullOf(), List.of()));
    assertThrows(NullPointerException.class, () -> new EntityProfile(displayName, nullOf()));
  }

  @Test
  void bookIdentity_requiresEveryConstituentValueObject() {
    BookEntityName entityName = new BookEntityName("Acme Studio");
    CurrencyUnit functionalCurrency = CurrencyUnit.of("EUR");
    FiscalYearStart fiscalYearStart = FiscalYearStart.parse("01-01");
    EntityProfile entityProfile = new EntityProfile(entityName, List.of());

    BookIdentity bookIdentity =
        new BookIdentity(
            entityProfile,
            functionalCurrency,
            fiscalYearStart,
            AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);

    assertEquals(entityName, bookIdentity.entityName());
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                nullOf(),
                functionalCurrency,
                fiscalYearStart,
                AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                entityProfile,
                nullOf(),
                fiscalYearStart,
                AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                entityProfile,
                functionalCurrency,
                nullOf(),
                AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1));
    assertThrows(
        NullPointerException.class,
        () -> new BookIdentity(entityProfile, functionalCurrency, fiscalYearStart, nullOf()));
  }

  @Test
  void accountingPolicyAndChartNodeWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals(
        "INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1",
        AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1.wireValue());
    assertEquals(
        AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1,
        AccountingPolicyProfile.fromWireValue("INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1"));
    assertEquals(
        List.of("INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1"), AccountingPolicyProfile.wireValues());
    assertThrows(
        IllegalArgumentException.class,
        () -> AccountingPolicyProfile.fromWireValue("internal-management"));

    assertEquals("HEADER", AccountNodeKind.HEADER.wireValue());
    assertEquals("POSTABLE", AccountNodeKind.POSTABLE.wireValue());
    assertEquals(AccountNodeKind.HEADER, AccountNodeKind.fromWireValue("HEADER"));
    assertEquals(AccountNodeKind.POSTABLE, AccountNodeKind.fromWireValue("POSTABLE"));
    assertEquals(List.of("HEADER", "POSTABLE"), AccountNodeKind.wireValues());
    assertTrue(AccountNodeKind.HEADER.allowsChildren());
    assertFalse(AccountNodeKind.HEADER.allowsPosting());
    assertFalse(AccountNodeKind.POSTABLE.allowsChildren());
    assertTrue(AccountNodeKind.POSTABLE.allowsPosting());
    assertThrows(IllegalArgumentException.class, () -> AccountNodeKind.fromWireValue("header"));
  }

  @Test
  void retainedEvidenceSupportTypesValidateCanonicalValues() {
    assertEquals(
        "evidence://documents/invoice-1.pdf",
        new StorageLocator("  evidence://documents/invoice-1.pdf  ").value());
    assertThrows(IllegalArgumentException.class, () -> new StorageLocator("   "));
    assertThrows(IllegalArgumentException.class, () -> new StorageLocator("x".repeat(513)));

    String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertEquals(sha256, new ContentSha256(sha256).value());
    assertThrows(IllegalArgumentException.class, () -> new ContentSha256("abc"));

    assertEquals("APPROVED", ApprovalDecision.APPROVED.wireValue());
    assertEquals("REJECTED", ApprovalDecision.REJECTED.wireValue());
    assertEquals(ApprovalDecision.APPROVED, ApprovalDecision.fromWireValue("APPROVED"));
    assertEquals(ApprovalDecision.REJECTED, ApprovalDecision.fromWireValue("REJECTED"));
    assertEquals(List.of("APPROVED", "REJECTED"), ApprovalDecision.wireValues());
    assertThrows(IllegalArgumentException.class, () -> ApprovalDecision.fromWireValue("approved"));
  }

  @Test
  void financialPositionLineClassificationWireVocabularyAndAccountTypesAreStable() {
    assertEquals(
        List.of(
            "CURRENT_ASSET",
            "NONCURRENT_ASSET",
            "CURRENT_LIABILITY",
            "NONCURRENT_LIABILITY",
            "CONTRIBUTED_CAPITAL",
            "DISTRIBUTIONS",
            "ACCUMULATED_RESULT",
            "RESERVE",
            "OTHER_EQUITY"),
        FinancialPositionLineClassification.wireValues());
    assertEquals(
        List.of(
            "CURRENT_ASSET",
            "NONCURRENT_ASSET",
            "CURRENT_LIABILITY",
            "NONCURRENT_LIABILITY",
            "CONTRIBUTED_CAPITAL",
            "DISTRIBUTIONS",
            "ACCUMULATED_RESULT",
            "RESERVE",
            "OTHER_EQUITY"),
        FinancialPositionLineClassification.declaredAccountWireValues());
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
        AccountType.EQUITY, FinancialPositionLineClassification.CONTRIBUTED_CAPITAL.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.DISTRIBUTIONS.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.ACCUMULATED_RESULT.accountType());
    assertEquals(AccountType.EQUITY, FinancialPositionLineClassification.RESERVE.accountType());
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
    assertThrows(IllegalArgumentException.class, () -> PostingKind.fromWireValue("CLOSING"));
    assertTrue(PostingKind.STANDARD.isStandard());
    assertTrue(PostingKind.OPENING_BALANCE.isOpeningBalance());
    assertTrue(PostingKind.PERIOD_CLOSE.isGenerated());
    assertFalse(PostingKind.PERIOD_CLOSE.isStandard());
    assertFalse(PostingKind.PERIOD_CLOSE.isOpeningBalance());
    assertFalse(PostingKind.STANDARD.isGenerated());
    assertFalse(PostingKind.OPENING_BALANCE.isGenerated());
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
