package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
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
  void entityProfile_requiresDisplayName() {
    BookEntityName displayName = new BookEntityName("Acme Studio");

    EntityProfile profile = new EntityProfile(displayName);

    assertEquals(displayName, profile.displayName());
    assertThrows(NullPointerException.class, () -> new EntityProfile(nullOf()));
  }

  @Test
  void bookIdentity_requiresEveryConstituentValueObject() {
    BookEntityName entityName = new BookEntityName("Acme Studio");
    CurrencyUnit functionalCurrency = CurrencyUnit.of("EUR");
    FiscalYearStart fiscalYearStart = FiscalYearStart.parse("01-01");
    EntityProfile entityProfile = new EntityProfile(entityName);

    BookIdentity bookIdentity =
        new BookIdentity(
            entityProfile,
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            functionalCurrency,
            fiscalYearStart);

    assertEquals(entityName, bookIdentity.entityName());
    assertEquals(
        AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
        bookIdentity.bookDoctrine().accountingKernelProfileId());
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                nullOf(),
                BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
                functionalCurrency,
                fiscalYearStart));
    assertThrows(
        NullPointerException.class,
        () -> new BookIdentity(entityProfile, nullOf(), functionalCurrency, fiscalYearStart));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                entityProfile,
                BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
                nullOf(),
                fiscalYearStart));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookIdentity(
                entityProfile,
                BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
                functionalCurrency,
                nullOf()));
  }

  @Test
  void bookDoctrine_requiresEveryConstituentValueObject() {
    BookDoctrine doctrine =
        new BookDoctrine(
            AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
            AccountingBasis.CASH,
            AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
            EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
            BookTemplateId.OWNER_MANAGED_SERVICE,
            null);

    assertEquals(
        AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
        doctrine.accountingKernelProfileId());
    assertEquals(AccountingBasis.CASH, doctrine.accountingBasis());
    assertEquals(
        AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
        doctrine.accountingFrameworkPosition());
    assertEquals(EntityForm.OWNER_MANAGED_SINGLE_ENTITY, doctrine.entityForm());
    assertEquals(BookTemplateId.OWNER_MANAGED_SERVICE, doctrine.bookTemplateId());
    assertEquals(null, doctrine.inventoryCostingDoctrine());
    assertThrows(
        NullPointerException.class,
        () ->
            new BookDoctrine(
                nullOf(),
                AccountingBasis.CASH,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_SERVICE,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                nullOf(),
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_SERVICE,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                AccountingBasis.CASH,
                nullOf(),
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_SERVICE,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                AccountingBasis.CASH,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                nullOf(),
                BookTemplateId.OWNER_MANAGED_SERVICE,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                AccountingBasis.CASH,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                nullOf(),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                AccountingBasis.CASH,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_TRADING,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookDoctrine(
                AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
                AccountingBasis.CASH,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_SERVICE,
                InventoryCostingDoctrine.WEIGHTED_AVERAGE));
  }

  @Test
  void bookDoctrineVocabularyAndBuiltInDoctrineAreStable() {
    assertEquals(
        List.of("NON_STATUTORY_INTERNAL_MANAGEMENT"), AccountingFrameworkPosition.wireValues());
    assertEquals(
        AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
        AccountingFrameworkPosition.fromWireValue(
            AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT.wireValue()));
    assertThrows(
        IllegalArgumentException.class,
        () -> AccountingFrameworkPosition.fromWireValue("non-statutory-internal-management"));

    assertEquals(List.of("OWNER_MANAGED_SINGLE_ENTITY"), EntityForm.wireValues());
    assertEquals(
        EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
        EntityForm.fromWireValue(EntityForm.OWNER_MANAGED_SINGLE_ENTITY.wireValue()));
    assertThrows(
        IllegalArgumentException.class,
        () -> EntityForm.fromWireValue("owner-managed-single-entity"));

    assertEquals(
        List.of("OWNER_MANAGED_SERVICE", "OWNER_MANAGED_TRADING"), BookTemplateId.wireValues());
    assertEquals(
        BookTemplateId.OWNER_MANAGED_SERVICE,
        BookTemplateId.fromWireValue(BookTemplateId.OWNER_MANAGED_SERVICE.wireValue()));
    assertEquals(
        BookTemplateId.OWNER_MANAGED_TRADING,
        BookTemplateId.fromWireValue(BookTemplateId.OWNER_MANAGED_TRADING.wireValue()));
    assertThrows(
        IllegalArgumentException.class,
        () -> BookTemplateId.fromWireValue("owner-managed-service"));

    assertEquals(List.of("CASH", "ACCRUAL"), AccountingBasis.wireValues());
    assertEquals(
        AccountingBasis.CASH, AccountingBasis.fromWireValue(AccountingBasis.CASH.wireValue()));
    assertEquals(
        AccountingBasis.ACCRUAL,
        AccountingBasis.fromWireValue(AccountingBasis.ACCRUAL.wireValue()));
    assertThrows(IllegalArgumentException.class, () -> AccountingBasis.fromWireValue("cash-basis"));

    assertEquals(
        AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE.accountingFrameworkPosition());
    assertEquals(
        AccountingBasis.CASH,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE.accountingBasis());
    assertEquals(
        EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE.entityForm());
    assertEquals(
        BookTemplateId.OWNER_MANAGED_SERVICE,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE.bookTemplateId());
    assertEquals(
        AccountingBasis.ACCRUAL,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL.accountingBasis());
    assertEquals(
        BookTemplateId.OWNER_MANAGED_SERVICE,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL.bookTemplateId());
    assertEquals(
        AccountingBasis.CASH,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING.accountingBasis());
    assertEquals(
        BookTemplateId.OWNER_MANAGED_TRADING,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING.bookTemplateId());
    assertEquals(
        AccountingBasis.ACCRUAL,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL.accountingBasis());
    assertEquals(
        BookTemplateId.OWNER_MANAGED_TRADING,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL.bookTemplateId());
    assertEquals(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        BookDoctrines.forTemplateAndBasis(
            BookTemplateId.OWNER_MANAGED_SERVICE, AccountingBasis.CASH, null));
    assertEquals(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        BookDoctrines.forTemplateAndBasis(
            BookTemplateId.OWNER_MANAGED_SERVICE, AccountingBasis.ACCRUAL, null));
    assertEquals(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        BookDoctrines.forTemplateAndBasis(
            BookTemplateId.OWNER_MANAGED_TRADING,
            AccountingBasis.CASH,
            InventoryCostingDoctrine.WEIGHTED_AVERAGE));
    assertEquals(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL,
        BookDoctrines.forTemplateAndBasis(
            BookTemplateId.OWNER_MANAGED_TRADING,
            AccountingBasis.ACCRUAL,
            InventoryCostingDoctrine.WEIGHTED_AVERAGE));
  }

  @Test
  void accountingKernelProfileId_stripsWhitespaceAndRejectsInvalidValues() {
    assertEquals(
        "internal-management-bookkeeping-kernel",
        new AccountingKernelProfileId("  internal-management-bookkeeping-kernel  ").value());
    assertThrows(NullPointerException.class, () -> new AccountingKernelProfileId(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new AccountingKernelProfileId("   "));
    assertThrows(
        IllegalArgumentException.class, () -> new AccountingKernelProfileId("x".repeat(121)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountingKernelProfileId("Internal-Management-Bookkeeping-Kernel"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountingKernelProfileId("internal_management_bookkeeping_kernel"));
  }

  @Test
  void chartNodeWireVocabularyParsesStableValuesAndRejectsUnknownValues() {
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
            "INVENTORY",
            "NONCURRENT_ASSET",
            "TRADE_RECEIVABLE",
            "CURRENT_LIABILITY",
            "NONCURRENT_LIABILITY",
            "TRADE_PAYABLE",
            "EQUITY_CONTRIBUTION",
            "EQUITY_WITHDRAWAL",
            "RESULT_HOLDING",
            "RETAINED_ACCUMULATED",
            "RESERVE",
            "OTHER_EQUITY"),
        FinancialPositionLineClassification.wireValues());
    assertEquals(
        List.of(
            "CURRENT_ASSET",
            "INVENTORY",
            "NONCURRENT_ASSET",
            "TRADE_RECEIVABLE",
            "CURRENT_LIABILITY",
            "NONCURRENT_LIABILITY",
            "TRADE_PAYABLE",
            "EQUITY_CONTRIBUTION",
            "EQUITY_WITHDRAWAL",
            "RESULT_HOLDING",
            "RETAINED_ACCUMULATED",
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
    assertEquals(AccountType.ASSET, FinancialPositionLineClassification.INVENTORY.accountType());
    assertEquals(
        AccountType.ASSET, FinancialPositionLineClassification.NONCURRENT_ASSET.accountType());
    assertEquals(
        AccountType.ASSET, FinancialPositionLineClassification.TRADE_RECEIVABLE.accountType());
    assertEquals(
        AccountType.LIABILITY, FinancialPositionLineClassification.CURRENT_LIABILITY.accountType());
    assertEquals(
        AccountType.LIABILITY,
        FinancialPositionLineClassification.NONCURRENT_LIABILITY.accountType());
    assertEquals(
        AccountType.LIABILITY, FinancialPositionLineClassification.TRADE_PAYABLE.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.EQUITY_CONTRIBUTION.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.EQUITY_WITHDRAWAL.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.RESULT_HOLDING.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.RETAINED_ACCUMULATED.accountType());
    assertEquals(AccountType.EQUITY, FinancialPositionLineClassification.RESERVE.accountType());
    assertEquals(
        AccountType.EQUITY, FinancialPositionLineClassification.OTHER_EQUITY.accountType());
    assertEquals(
        NormalBalance.DEBIT, FinancialPositionLineClassification.INVENTORY.normalBalance());
    assertEquals(
        NormalBalance.CREDIT,
        FinancialPositionLineClassification.RETAINED_ACCUMULATED.normalBalance());
    assertThrows(
        IllegalArgumentException.class,
        () -> FinancialPositionLineClassification.fromWireValue("current-asset"));
  }

  @Test
  void profitAndLossLineClassificationWireVocabularyAndAccountTypesAreStable() {
    assertEquals(
        List.of(
            "OPERATING_REVENUE",
            "SALES_DISCOUNT_ALLOWANCE",
            "OTHER_REVENUE",
            "FINANCE_INCOME",
            "COST_OF_SALES",
            "OPERATING_EXPENSE",
            "DEPRECIATION_AND_AMORTIZATION",
            "SETTLEMENT_FEE",
            "BAD_DEBT_WRITE_OFF",
            "FINANCE_EXPENSE",
            "OTHER_EXPENSE"),
        ProfitAndLossLineClassification.wireValues());
    for (ProfitAndLossLineClassification classification :
        ProfitAndLossLineClassification.values()) {
      assertEquals(
          classification,
          ProfitAndLossLineClassification.fromWireValue(classification.wireValue()));
    }
    assertEquals(
        AccountType.REVENUE, ProfitAndLossLineClassification.OPERATING_REVENUE.accountType());
    assertEquals(
        AccountType.REVENUE,
        ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE.accountType());
    assertEquals(AccountType.REVENUE, ProfitAndLossLineClassification.OTHER_REVENUE.accountType());
    assertEquals(AccountType.REVENUE, ProfitAndLossLineClassification.FINANCE_INCOME.accountType());
    assertEquals(AccountType.EXPENSE, ProfitAndLossLineClassification.COST_OF_SALES.accountType());
    assertEquals(
        AccountType.EXPENSE, ProfitAndLossLineClassification.OPERATING_EXPENSE.accountType());
    assertEquals(
        AccountType.EXPENSE,
        ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION.accountType());
    assertEquals(AccountType.EXPENSE, ProfitAndLossLineClassification.SETTLEMENT_FEE.accountType());
    assertEquals(
        AccountType.EXPENSE, ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF.accountType());
    assertEquals(
        AccountType.EXPENSE, ProfitAndLossLineClassification.FINANCE_EXPENSE.accountType());
    assertEquals(AccountType.EXPENSE, ProfitAndLossLineClassification.OTHER_EXPENSE.accountType());
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
    assertEquals(
        LocalDate.parse("2026-03-31"),
        aprilStart.containingFiscalYearEnd(LocalDate.parse("2026-03-31")));
    assertEquals(
        LocalDate.parse("2027-03-31"),
        aprilStart.containingFiscalYearEnd(LocalDate.parse("2026-04-01")));
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
    assertEquals(
        LocalDate.parse("2026-02-27"),
        leapDayStart.containingFiscalYearEnd(LocalDate.parse("2025-03-01")));
    assertEquals(
        LocalDate.parse("2025-02-27"),
        leapDayStart.containingFiscalYearEnd(LocalDate.parse("2024-03-01")));
    assertThrows(NullPointerException.class, () -> FiscalYearStart.parse(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> FiscalYearStart.parse("1-1"));
    assertThrows(IllegalArgumentException.class, () -> FiscalYearStart.parse("13-01"));
    assertThrows(NullPointerException.class, () -> aprilStart.containingFiscalYearStart(nullOf()));
    assertThrows(NullPointerException.class, () -> aprilStart.containingFiscalYearEnd(nullOf()));
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

    assertEquals("PERSON", ActorType.PERSON.wireValue());
    assertEquals("SYSTEM", ActorType.SYSTEM.wireValue());
    assertEquals("AGENT", ActorType.AGENT.wireValue());
    assertEquals(java.util.List.of("PERSON", "SYSTEM", "AGENT"), ActorType.wireValues());
    assertEquals(ActorType.PERSON, ActorType.fromWireValue("PERSON"));
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
  }

  @Test
  void postingKindWireVocabularyAndClassificationRemainStable() {
    assertEquals("STANDARD", PostingKind.STANDARD.wireValue());
    assertEquals("OPENING_BALANCE", PostingKind.OPENING_BALANCE.wireValue());
    assertEquals("INTERIM_RESULT_SWEEP", PostingKind.INTERIM_RESULT_SWEEP.wireValue());
    assertEquals("FISCAL_YEAR_CLOSE", PostingKind.FISCAL_YEAR_CLOSE.wireValue());
    assertEquals(PostingKind.STANDARD, PostingKind.fromWireValue("STANDARD"));
    assertEquals(PostingKind.OPENING_BALANCE, PostingKind.fromWireValue("OPENING_BALANCE"));
    assertEquals(
        PostingKind.INTERIM_RESULT_SWEEP, PostingKind.fromWireValue("INTERIM_RESULT_SWEEP"));
    assertEquals(PostingKind.FISCAL_YEAR_CLOSE, PostingKind.fromWireValue("FISCAL_YEAR_CLOSE"));
    assertEquals(
        java.util.List.of(
            "STANDARD", "OPENING_BALANCE", "INTERIM_RESULT_SWEEP", "FISCAL_YEAR_CLOSE"),
        PostingKind.wireValues());
    assertThrows(IllegalArgumentException.class, () -> PostingKind.fromWireValue("CLOSING"));
    assertTrue(PostingKind.STANDARD.isStandard());
    assertTrue(PostingKind.OPENING_BALANCE.isOpeningBalance());
    assertTrue(PostingKind.INTERIM_RESULT_SWEEP.isGenerated());
    assertTrue(PostingKind.FISCAL_YEAR_CLOSE.isGenerated());
    assertFalse(PostingKind.INTERIM_RESULT_SWEEP.isStandard());
    assertFalse(PostingKind.INTERIM_RESULT_SWEEP.isOpeningBalance());
    assertFalse(PostingKind.FISCAL_YEAR_CLOSE.isStandard());
    assertFalse(PostingKind.FISCAL_YEAR_CLOSE.isOpeningBalance());
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
    assertTrue(PostingCoverage.ALL_POSTING_KINDS.includes(PostingKind.INTERIM_RESULT_SWEEP));
    assertTrue(PostingCoverage.ALL_POSTING_KINDS.includes(PostingKind.FISCAL_YEAR_CLOSE));
    assertTrue(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.STANDARD));
    assertTrue(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.OPENING_BALANCE));
    assertFalse(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.INTERIM_RESULT_SWEEP));
    assertFalse(PostingCoverage.NON_CLOSING_POSTINGS.includes(PostingKind.FISCAL_YEAR_CLOSE));
    assertThrows(
        NullPointerException.class, () -> PostingCoverage.ALL_POSTING_KINDS.includes(nullOf()));
  }
}
