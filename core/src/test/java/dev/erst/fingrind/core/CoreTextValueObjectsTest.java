package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertEquals(java.util.List.of("CLI"), SourceChannel.wireValues());
    assertArrayEquals(new SourceChannel[] {SourceChannel.CLI}, SourceChannel.values());
    assertEquals(SourceChannel.CLI, SourceChannel.fromWireValue("CLI"));
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
    assertEquals("RETAINED_EARNINGS", AccountRole.RETAINED_EARNINGS.wireValue());
    assertEquals(AccountRole.ORDINARY, AccountRole.fromWireValue("ORDINARY"));
    assertEquals(AccountRole.RETAINED_EARNINGS, AccountRole.fromWireValue("RETAINED_EARNINGS"));
    assertEquals(
        java.util.List.of("ORDINARY", "CONTRA", "RETAINED_EARNINGS"), AccountRole.wireValues());
    assertThrows(IllegalArgumentException.class, () -> AccountRole.fromWireValue("ordinary"));
  }

  @Test
  void postingKindWireVocabularyAndClassificationRemainStable() {
    assertEquals("STANDARD", PostingKind.STANDARD.wireValue());
    assertEquals("PERIOD_CLOSE", PostingKind.PERIOD_CLOSE.wireValue());
    assertEquals(PostingKind.STANDARD, PostingKind.fromWireValue("STANDARD"));
    assertEquals(PostingKind.PERIOD_CLOSE, PostingKind.fromWireValue("PERIOD_CLOSE"));
    assertEquals(java.util.List.of("STANDARD", "PERIOD_CLOSE"), PostingKind.wireValues());
    assertThrows(IllegalArgumentException.class, () -> PostingKind.fromWireValue("CLOSING"));
    assertTrue(PostingKind.STANDARD.isStandard());
    assertFalse(PostingKind.PERIOD_CLOSE.isStandard());
  }
}
