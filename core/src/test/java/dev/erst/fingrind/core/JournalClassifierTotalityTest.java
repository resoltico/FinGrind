package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.AccountDoctrineTestSupport.assetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.nominalTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.JournalLine.EntrySide;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Proves the classifier's structural, typed, compound, and adjustment partition. */
class JournalClassifierTotalityTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final Map<String, AccountRole> ACCOUNT_ROLES =
      Map.of(
          "cash", AccountRole.CASH,
          "inventory", AccountRole.INVENTORY,
          "receivable", AccountRole.RECEIVABLE,
          "payable", AccountRole.PAYABLE,
          "revenue", AccountRole.REVENUE,
          "expense", AccountRole.EXPENSE,
          "equity-contributed", AccountRole.EQUITY_CONTRIBUTED,
          "equity-draws", AccountRole.EQUITY_DRAWS,
          "adjunct", AccountRole.SETTLEMENT_ADJUNCT,
          "aux", AccountRole.AUX);

  @ParameterizedTest(name = "{0}")
  @MethodSource("structuralRows")
  void classify_structuralRowsWin(
      String name,
      JournalEntry journalEntry,
      StructuralContext structural,
      EconomicEventClass expectedEventClass) {
    ClassificationResult result = classifyFixture(journalEntry, EvidenceClass.OTHER, structural);

    assertEquals(expectedEventClass, result.eventClass(), name);
    assertTrue(result.containedTypedEvents().isEmpty(), name);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exactTypedSingletonRows")
  void classify_exactTypedSingletonRows(
      String name, JournalEntry journalEntry, EconomicEventClass expectedEventClass) {
    ClassificationResult result = classifyOrdinary(journalEntry, EvidenceClass.OTHER);

    assertEquals(expectedEventClass, result.eventClass(), name);
    assertEquals(Set.of(expectedEventClass), result.containedTypedEvents(), name);
    assertTrue(result.eventClass().typedSingleton(), name);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("compoundOperationalRows")
  void classify_compoundOperationalRows(
      String name,
      JournalEntry journalEntry,
      Set<EconomicEventClass> expectedContainedTypedEvents) {
    ClassificationResult result = classifyOrdinary(journalEntry, EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.COMPOUND_OPERATIONAL, result.eventClass(), name);
    assertEquals(expectedContainedTypedEvents, result.containedTypedEvents(), name);
    assertFalse(result.containedTypedEvents().isEmpty(), name);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adjustmentRows")
  void classify_adjustmentRows(
      String name, JournalEntry journalEntry, boolean expectedHasCashLine) {
    ClassificationResult result = classifyOrdinary(journalEntry, EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.ADJUSTMENT, result.eventClass(), name);
    assertTrue(result.containedTypedEvents().isEmpty(), name);
    assertEquals(expectedHasCashLine, result.hasCashLine(), name);
  }

  @Test
  void classify_isTotalAndEvidenceNeverSelectsTheEventClass() {
    for (Fixture fixture : representativeFixtures()) {
      EconomicEventClass expectedEventClass = fixture.expectedEventClass();
      for (EvidenceClass evidenceClass : EvidenceClass.values()) {
        ClassificationResult result =
            classifyFixture(fixture.journalEntry(), evidenceClass, fixture.structural());
        assertEquals(
            expectedEventClass, result.eventClass(), fixture.name() + " / " + evidenceClass);
        assertEquals(evidenceClass, result.evidenceClass(), fixture.name() + " / " + evidenceClass);
      }
    }
  }

  @Test
  void classify_promotesSingleContainedTypedEventToCompoundWhenTheAnchorSignatureHasExtraAnchors() {
    ClassificationResult result =
        classifyOrdinary(
            journal(
                line("cash", EntrySide.DEBIT, "100.00"),
                line("expense", EntrySide.DEBIT, "20.00"),
                line("payable", EntrySide.DEBIT, "5.00"),
                line("aux", EntrySide.CREDIT, "25.00"),
                line("revenue", EntrySide.CREDIT, "100.00")),
            EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.COMPOUND_OPERATIONAL, result.eventClass());
    assertEquals(Set.of(EconomicEventClass.SETTLED_SALE), result.containedTypedEvents());
  }

  @Test
  void classify_treatsSingleContainedSaleWithNonTradingExtraAnchorAsCompoundOperational() {
    ClassificationResult result =
        classifyOrdinary(
            journal(
                line("cash", EntrySide.DEBIT, "150.00"),
                line("revenue", EntrySide.CREDIT, "100.00"),
                line("payable", EntrySide.CREDIT, "50.00")),
            EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.COMPOUND_OPERATIONAL, result.eventClass());
    assertEquals(Set.of(EconomicEventClass.SETTLED_SALE), result.containedTypedEvents());
  }

  @Test
  void classify_treatsSaleSignatureWithTwoWrongExtraAnchorsAsCompoundOperational() {
    ClassificationResult result =
        classifyOrdinary(
            journal(
                line("cash", EntrySide.DEBIT, "150.00"),
                line("expense", EntrySide.DEBIT, "50.00"),
                line("revenue", EntrySide.CREDIT, "150.00"),
                line("equity-draws", EntrySide.CREDIT, "50.00")),
            EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.COMPOUND_OPERATIONAL, result.eventClass());
    assertEquals(Set.of(EconomicEventClass.SETTLED_SALE), result.containedTypedEvents());
  }

  @Test
  void classify_treatsSaleSignatureWithFourAnchorsButNoExpenseAsCompoundOperational() {
    ClassificationResult result =
        classifyOrdinary(
            journal(
                line("cash", EntrySide.DEBIT, "150.00"),
                line("equity-draws", EntrySide.DEBIT, "50.00"),
                line("revenue", EntrySide.CREDIT, "150.00"),
                line("payable", EntrySide.CREDIT, "50.00")),
            EvidenceClass.OTHER);

    assertEquals(EconomicEventClass.COMPOUND_OPERATIONAL, result.eventClass());
    assertEquals(Set.of(EconomicEventClass.SETTLED_SALE), result.containedTypedEvents());
  }

  @Test
  void classify_treatsFinanceExpenseAccountsAsAuxiliaryWhenDerivedFromDeclaredTaxonomy() {
    Map<AccountCode, AccountRole> accountRoles =
        Map.of(
            new AccountCode("1000"),
            AccountRole.from(
                AccountType.ASSET,
                assetTaxonomy(
                    AccountNodeKind.POSTABLE,
                    FinancialPositionLineClassification.CURRENT_ASSET,
                    CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new AccountCode("1100"),
            AccountRole.from(
                AccountType.ASSET,
                assetTaxonomy(
                    AccountNodeKind.POSTABLE,
                    FinancialPositionLineClassification.TRADE_RECEIVABLE,
                    CashFlowAssetClassification.NON_CASH)),
            new AccountCode("5900"),
            AccountRole.from(
                AccountType.EXPENSE,
                nominalTaxonomy(
                    AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.FINANCE_EXPENSE)));

    ClassificationResult result =
        JournalClassifier.classify(
            journal(
                new JournalLine(
                    new AccountCode("1000"), EntrySide.DEBIT, Money.parse("EUR", "90.00")),
                new JournalLine(
                    new AccountCode("5900"), EntrySide.DEBIT, Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("1100"), EntrySide.CREDIT, Money.parse("EUR", "100.00"))),
            accountCode ->
                Objects.requireNonNull(accountRoles.get(accountCode), accountCode.value()),
            EvidenceClass.OTHER,
            StructuralContext.ordinary(),
            java.util.Optional.empty());

    assertEquals(EconomicEventClass.AR_SETTLEMENT, result.eventClass());
    assertEquals(
        Set.of(
            new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
            new AnchorEntry(AccountRole.RECEIVABLE, EntrySide.CREDIT)),
        result.anchorSignature());
    assertEquals(Set.of(EconomicEventClass.AR_SETTLEMENT), result.containedTypedEvents());
  }

  @Test
  void classify_validatesEveryAssertedInventoryEventAgainstItsOwnedAnchorSignature() {
    assertAssertedInventoryEvent(
        EconomicEventClass.INVENTORY_CAPITALIZATION,
        journal(
            line("inventory", EntrySide.DEBIT, "100.00"),
            line("cash", EntrySide.CREDIT, "100.00")));
    assertAssertedInventoryEvent(
        EconomicEventClass.INVENTORY_CAPITALIZATION,
        journal(
            line("inventory", EntrySide.DEBIT, "100.00"),
            line("payable", EntrySide.CREDIT, "100.00")));
    assertAssertedInventoryEvent(
        EconomicEventClass.INVENTORY_WRITE_DOWN,
        journal(
            line("expense", EntrySide.DEBIT, "100.00"),
            line("inventory", EntrySide.CREDIT, "100.00")));
    assertAssertedInventoryEvent(
        EconomicEventClass.INVENTORY_SHRINKAGE,
        journal(
            line("expense", EntrySide.DEBIT, "100.00"),
            line("inventory", EntrySide.CREDIT, "100.00")));
    assertAssertedInventoryEvent(
        EconomicEventClass.INVENTORY_COUNT_INCREASE,
        journal(
            line("inventory", EntrySide.DEBIT, "100.00"),
            line("revenue", EntrySide.CREDIT, "100.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JournalClassifier.classify(
                journal(
                    line("cash", EntrySide.DEBIT, "100.00"),
                    line("revenue", EntrySide.CREDIT, "100.00")),
                JournalClassifierTotalityTest::accountRoleForFixture,
                EvidenceClass.OTHER,
                StructuralContext.ordinary(),
                java.util.Optional.of(EconomicEventClass.INVENTORY_CAPITALIZATION)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JournalClassifier.classify(
                journal(
                    line("cash", EntrySide.DEBIT, "100.00"),
                    line("revenue", EntrySide.CREDIT, "100.00")),
                JournalClassifierTotalityTest::accountRoleForFixture,
                EvidenceClass.OTHER,
                StructuralContext.ordinary(),
                java.util.Optional.of(EconomicEventClass.SETTLED_SALE)));
  }

  private static void assertAssertedInventoryEvent(
      EconomicEventClass expectedEventClass, JournalEntry journalEntry) {
    ClassificationResult result =
        JournalClassifier.classify(
            journalEntry,
            JournalClassifierTotalityTest::accountRoleForFixture,
            EvidenceClass.OTHER,
            StructuralContext.ordinary(),
            java.util.Optional.of(expectedEventClass));
    assertEquals(expectedEventClass, result.eventClass());
    assertEquals(Set.of(expectedEventClass), result.containedTypedEvents());
  }

  private static Stream<Arguments> structuralRows() {
    return Stream.of(
        Arguments.of(
            "reversal wins over any typed anchor shape",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"), line("revenue", EntrySide.CREDIT, "10.00")),
            new StructuralContext(java.util.Optional.of(new PostingId("posting-1")), false),
            EconomicEventClass.REVERSAL),
        Arguments.of(
            "opening wins over ordinary classification",
            journal(line("cash", EntrySide.DEBIT, "10.00"), line("aux", EntrySide.CREDIT, "10.00")),
            new StructuralContext(java.util.Optional.empty(), true),
            EconomicEventClass.OPENING));
  }

  private static Stream<Arguments> exactTypedSingletonRows() {
    return Stream.of(
        Arguments.of(
            "settled sale",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"), line("revenue", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.SETTLED_SALE),
        Arguments.of(
            "settled trading sale",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"),
                line("expense", EntrySide.DEBIT, "4.00"),
                line("inventory", EntrySide.CREDIT, "4.00"),
                line("revenue", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.SETTLED_SALE),
        Arguments.of(
            "credit sale",
            journal(
                line("receivable", EntrySide.DEBIT, "10.00"),
                line("revenue", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.CREDIT_SALE),
        Arguments.of(
            "credit trading sale",
            journal(
                line("receivable", EntrySide.DEBIT, "10.00"),
                line("expense", EntrySide.DEBIT, "4.00"),
                line("inventory", EntrySide.CREDIT, "4.00"),
                line("revenue", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.CREDIT_SALE),
        Arguments.of(
            "settled purchase",
            journal(
                line("inventory", EntrySide.DEBIT, "10.00"),
                line("cash", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.SETTLED_PURCHASE),
        Arguments.of(
            "credit purchase",
            journal(
                line("inventory", EntrySide.DEBIT, "10.00"),
                line("payable", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.CREDIT_PURCHASE),
        Arguments.of(
            "settled expense",
            journal(
                line("expense", EntrySide.DEBIT, "10.00"), line("cash", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.SETTLED_EXPENSE),
        Arguments.of(
            "credit expense",
            journal(
                line("expense", EntrySide.DEBIT, "10.00"),
                line("payable", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.CREDIT_EXPENSE),
        Arguments.of(
            "accounts-receivable settlement",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"),
                line("receivable", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.AR_SETTLEMENT),
        Arguments.of(
            "accounts-payable settlement",
            journal(
                line("payable", EntrySide.DEBIT, "10.00"), line("cash", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.AP_SETTLEMENT),
        Arguments.of(
            "owner contribution",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"),
                line("equity-contributed", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.OWNER_CONTRIBUTION),
        Arguments.of(
            "owner withdrawal",
            journal(
                line("equity-draws", EntrySide.DEBIT, "10.00"),
                line("cash", EntrySide.CREDIT, "10.00")),
            EconomicEventClass.OWNER_WITHDRAWAL),
        Arguments.of(
            "settled sale tolerates auxiliary tax lines",
            journal(
                line("cash", EntrySide.DEBIT, "120.00"),
                line("revenue", EntrySide.CREDIT, "100.00"),
                line("aux", EntrySide.CREDIT, "20.00")),
            EconomicEventClass.SETTLED_SALE),
        Arguments.of(
            "accounts-receivable settlement tolerates settlement adjunct discounts",
            journal(
                line("cash", EntrySide.DEBIT, "99.00"),
                line("adjunct", EntrySide.DEBIT, "1.00"),
                line("receivable", EntrySide.CREDIT, "100.00")),
            EconomicEventClass.AR_SETTLEMENT),
        Arguments.of(
            "accounts-receivable settlement tolerates auxiliary exchange losses",
            journal(
                line("cash", EntrySide.DEBIT, "95.00"),
                line("aux", EntrySide.DEBIT, "5.00"),
                line("receivable", EntrySide.CREDIT, "100.00")),
            EconomicEventClass.AR_SETTLEMENT),
        Arguments.of(
            "accounts-payable settlement tolerates settlement fees",
            journal(
                line("payable", EntrySide.DEBIT, "100.00"),
                line("adjunct", EntrySide.DEBIT, "2.00"),
                line("cash", EntrySide.CREDIT, "102.00")),
            EconomicEventClass.AP_SETTLEMENT));
  }

  private static Stream<Arguments> compoundOperationalRows() {
    return Stream.of(
        Arguments.of(
            "cash plus receivable plus revenue is an operational bundle",
            journal(
                line("cash", EntrySide.DEBIT, "150.00"),
                line("receivable", EntrySide.CREDIT, "100.00"),
                line("revenue", EntrySide.CREDIT, "50.00")),
            Set.of(EconomicEventClass.AR_SETTLEMENT, EconomicEventClass.SETTLED_SALE)),
        Arguments.of(
            "expense paid partly in cash and partly on credit is an operational bundle",
            journal(
                line("expense", EntrySide.DEBIT, "80.00"),
                line("cash", EntrySide.CREDIT, "50.00"),
                line("payable", EntrySide.CREDIT, "30.00")),
            Set.of(EconomicEventClass.SETTLED_EXPENSE, EconomicEventClass.CREDIT_EXPENSE)));
  }

  private static Stream<Arguments> adjustmentRows() {
    return Stream.of(
        Arguments.of(
            "expense plus auxiliary contra line remains adjustment",
            journal(
                line("expense", EntrySide.DEBIT, "50.00"), line("aux", EntrySide.CREDIT, "50.00")),
            false),
        Arguments.of(
            "pure auxiliary rebalance remains adjustment",
            journal(line("aux", EntrySide.DEBIT, "40.00"), line("aux", EntrySide.CREDIT, "40.00")),
            false),
        Arguments.of(
            "cash-backed reclassification remains adjustment",
            journal(line("cash", EntrySide.DEBIT, "40.00"), line("aux", EntrySide.CREDIT, "40.00")),
            true));
  }

  private static List<Fixture> representativeFixtures() {
    return List.of(
        new Fixture(
            "reversal",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"), line("revenue", EntrySide.CREDIT, "10.00")),
            new StructuralContext(java.util.Optional.of(new PostingId("posting-1")), false),
            EconomicEventClass.REVERSAL),
        new Fixture(
            "opening",
            journal(line("cash", EntrySide.DEBIT, "10.00"), line("aux", EntrySide.CREDIT, "10.00")),
            new StructuralContext(java.util.Optional.empty(), true),
            EconomicEventClass.OPENING),
        new Fixture(
            "typed singleton",
            journal(
                line("cash", EntrySide.DEBIT, "10.00"), line("revenue", EntrySide.CREDIT, "10.00")),
            StructuralContext.ordinary(),
            EconomicEventClass.SETTLED_SALE),
        new Fixture(
            "compound operational",
            journal(
                line("cash", EntrySide.DEBIT, "150.00"),
                line("receivable", EntrySide.CREDIT, "100.00"),
                line("revenue", EntrySide.CREDIT, "50.00")),
            StructuralContext.ordinary(),
            EconomicEventClass.COMPOUND_OPERATIONAL),
        new Fixture(
            "adjustment",
            journal(
                line("expense", EntrySide.DEBIT, "50.00"), line("aux", EntrySide.CREDIT, "50.00")),
            StructuralContext.ordinary(),
            EconomicEventClass.ADJUSTMENT));
  }

  private static ClassificationResult classifyOrdinary(
      JournalEntry journalEntry, EvidenceClass evidenceClass) {
    return classifyFixture(journalEntry, evidenceClass, StructuralContext.ordinary());
  }

  private static ClassificationResult classifyFixture(
      JournalEntry journalEntry, EvidenceClass evidenceClass, StructuralContext structural) {
    return JournalClassifier.classify(
        journalEntry,
        JournalClassifierTotalityTest::accountRoleForFixture,
        evidenceClass,
        structural,
        java.util.Optional.empty());
  }

  private static JournalEntry journal(JournalLine... lines) {
    return new JournalEntry(EFFECTIVE_DATE, List.of(lines));
  }

  private static JournalLine line(String accountCode, EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private static AccountRole accountRoleForFixture(AccountCode accountCode) {
    AccountRole accountRole = ACCOUNT_ROLES.get(accountCode.value());
    if (accountRole == null) {
      throw new IllegalArgumentException("Unexpected fixture account: " + accountCode.value());
    }
    return accountRole;
  }

  private record Fixture(
      String name,
      JournalEntry journalEntry,
      StructuralContext structural,
      EconomicEventClass expectedEventClass) {}
}
