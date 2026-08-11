package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Verifies the journal's strict discovery and primitive grammar boundaries. */
class PublicationTransactionJournalGrammarTest {
  private static final String TRANSACTION_ID = "0123456789abcdef0123456789abcdef";
  private static final String OWNER_CONTEXT = "a".repeat(64);

  @Test
  void admitsOnlyCanonicalJournalNames() {
    String canonical = "txn-" + TRANSACTION_ID + ".json";

    assertTrue(PublicationTransactionJournalFileNames.isCanonical(canonical));
    assertEquals(
        new PublicationTransactionId(TRANSACTION_ID),
        PublicationTransactionJournalFileNames.transactionIdFromCanonical(canonical));
    assertFalse(PublicationTransactionJournalFileNames.isCanonical("txn-short.json"));
    assertFalse(
        PublicationTransactionJournalFileNames.isCanonical("bad-" + TRANSACTION_ID + ".json"));
    assertFalse(
        PublicationTransactionJournalFileNames.isCanonical("txn-" + TRANSACTION_ID + ".jsox"));
    assertFalse(
        PublicationTransactionJournalFileNames.isCanonical("txn-" + "g".repeat(32) + ".json"));
  }

  @Test
  void appliesTheJournalJsonGrammarToEveryPrimitiveBoundary() throws Exception {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode values =
        mapper.readTree("{\"text\":\"value\",\"number\":7,\"items\":[]}").asObject();

    assertEquals("value", PublicationTransactionJournalJsonFields.requiredString(values, "text"));
    assertEquals(7, PublicationTransactionJournalJsonFields.requiredInt(values, "number"));
    assertTrue(PublicationTransactionJournalJsonFields.requiredArray(values, "items").isEmpty());
    assertEquals(
        OWNER_CONTEXT,
        PublicationTransactionJournalJsonFields.requiredHex(
            mapper.readTree("{\"context\":\"" + OWNER_CONTEXT + "\"}").asObject(), "context", 64));
    PublicationTransactionJournalJsonFields.requireExactProperties(
        values, List.of("text", "number", "items"), "value object");

    assertMalformed(() -> PublicationTransactionJournalJsonFields.requireObject(nullOf(), "root"));
    assertMalformed(
        () -> PublicationTransactionJournalJsonFields.requireObject(mapper.readTree("[]"), "root"));
    assertMalformed(() -> PublicationTransactionJournalJsonFields.requiredArray(values, "missing"));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requiredArray(
                mapper.readTree("{\"items\":\"wrong\"}").asObject(), "items"));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requiredHexNode(nullOf(), "ownerContext", 64));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requiredHexNode(
                mapper.readTree("12"), "ownerContext", 64));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requiredHexNode(
                mapper.readTree("\"" + "A".repeat(64) + "\""), "ownerContext", 64));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requireExactProperties(
                values, List.of("text"), "value object"));
    assertMalformed(
        () ->
            PublicationTransactionJournalJsonFields.requireExactProperties(
                mapper.readTree("{\"other\":\"value\",\"number\":7,\"items\":[]}").asObject(),
                List.of("text", "number", "items"),
                "value object"));
  }

  @Test
  void derivesOpaqueOwnerContextAndMakesUnsupportedLookupFailClosed() throws Exception {
    String description = "owner-context-v1\u0000book";
    PublicationTransactionOwnerContext context =
        PublicationTransactionOwnerContext.fromCanonicalDescription(description);
    PublicationTransactionService unsupported = unsupportedService();

    assertEquals(CryptographicPrimitives.sha256HexUtf8(description), context.value());
    assertThrows(
        NullPointerException.class, () -> new PublicationTransactionOwnerContext(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionOwnerContext("A".repeat(64)));
    assertThrows(
        NullPointerException.class,
        () -> PublicationTransactionOwnerContext.fromCanonicalDescription(nullOf()));
    assertThrows(
        NullPointerException.class, () -> unsupported.recoverMatchingOwnerContext(nullOf()));
    assertThrows(IOException.class, () -> unsupported.recoverMatchingOwnerContext(context));
  }

  private static void assertMalformed(CheckedOperation operation) {
    assertThrows(PublicationTransactionJournalViolation.class, operation::run);
  }

  private static PublicationTransactionService unsupportedService() {
    return new PublicationTransactionService() {
      @Override
      public PublicationTransactionResult publish(PublicationTransactionRequest request) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PublicationTransactionStageReservation reserveStages(
          PublicationTransactionRequest request) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PublicationTransactionResult publishReservedStages(
          PublicationTransactionStageReservation reservation) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PublicationTransactionRecoveryReceipt recoverWithReceipt(
          PublicationTransactionId transactionId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  /** Invokes one journal-grammar operation that may reject malformed input. */
  @FunctionalInterface
  private interface CheckedOperation {
    void run() throws IOException;
  }
}
