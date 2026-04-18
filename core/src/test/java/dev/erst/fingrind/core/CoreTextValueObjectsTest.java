package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void finiteWireVocabulariesParseStableValuesAndRejectUnknownValues() {
    assertEquals("DEBIT", NormalBalance.DEBIT.wireValue());
    assertEquals("CREDIT", NormalBalance.CREDIT.wireValue());
    assertEquals(NormalBalance.DEBIT, NormalBalance.fromWireValue("DEBIT"));
    assertEquals(NormalBalance.CREDIT, NormalBalance.fromWireValue("CREDIT"));
    assertEquals(java.util.List.of("DEBIT", "CREDIT"), NormalBalance.wireValues());
    assertThrows(IllegalArgumentException.class, () -> NormalBalance.fromWireValue("debit"));

    assertEquals("USER", ActorType.USER.wireValue());
    assertEquals("SYSTEM", ActorType.SYSTEM.wireValue());
    assertEquals("AGENT", ActorType.AGENT.wireValue());
    assertEquals(java.util.List.of("USER", "SYSTEM", "AGENT"), ActorType.wireValues());
    assertEquals(ActorType.USER, ActorType.fromWireValue("USER"));
    assertEquals(ActorType.SYSTEM, ActorType.fromWireValue("SYSTEM"));
    assertEquals(ActorType.AGENT, ActorType.fromWireValue("AGENT"));
    assertThrows(IllegalArgumentException.class, () -> ActorType.fromWireValue("ROBOT"));

    assertEquals("CLI", SourceChannel.CLI.wireValue());
    assertEquals(java.util.List.of("CLI"), SourceChannel.wireValues());
    assertEquals(SourceChannel.CLI, SourceChannel.fromWireValue("CLI"));
    assertThrows(IllegalArgumentException.class, () -> SourceChannel.fromWireValue("API"));

    assertEquals("DEBIT", JournalLine.EntrySide.DEBIT.wireValue());
    assertEquals("CREDIT", JournalLine.EntrySide.CREDIT.wireValue());
    assertEquals(JournalLine.EntrySide.DEBIT, JournalLine.EntrySide.fromWireValue("DEBIT"));
    assertEquals(JournalLine.EntrySide.CREDIT, JournalLine.EntrySide.fromWireValue("CREDIT"));
    assertEquals(java.util.List.of("DEBIT", "CREDIT"), JournalLine.EntrySide.wireValues());
    assertThrows(IllegalArgumentException.class, () -> JournalLine.EntrySide.fromWireValue("LEFT"));
  }
}
