package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the canonical print-request-template topic inventory. */
class ProtocolRequestTemplateTopicsTest {
  @Test
  void topicsRemainInStablePublicOrder() {
    assertEquals(
        List.of(
            OperationId.POST_ENTRY,
            OperationId.PREFLIGHT_ENTRY,
            OperationId.RECORD_SALE,
            OperationId.RECORD_EXPENSE,
            OperationId.RECORD_OWNER_CONTRIBUTION,
            OperationId.RECORD_OWNER_WITHDRAWAL,
            OperationId.RECORD_OPENING_POSITION,
            OperationId.RECORD_REVERSAL,
            OperationId.DECLARE_ACCOUNT,
            OperationId.DECLARE_TAX_REGISTRATION),
        ProtocolRequestTemplateTopics.topics());
    assertEquals(
        List.of(
            "post-entry",
            "preflight-entry",
            "record-sale",
            "record-expense",
            "record-owner-contribution",
            "record-owner-withdrawal",
            "record-opening-position",
            "record-reversal",
            "declare-account",
            "declare-tax-registration"),
        ProtocolRequestTemplateTopics.topicNames());
    assertEquals(
        "[post-entry|preflight-entry|record-sale|record-expense|record-owner-contribution|record-owner-withdrawal|record-opening-position|record-reversal|declare-account|declare-tax-registration]",
        ProtocolRequestTemplateTopics.syntax());
  }

  @Test
  void supportsOnlyRegisteredRequestTemplateTopics() {
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.RECORD_SALE));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.DECLARE_ACCOUNT));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.DECLARE_TAX_REGISTRATION));
    assertFalse(ProtocolRequestTemplateTopics.supports(OperationId.EXECUTE_PLAN));
  }

  @Test
  void nullOperationIsRejected() {
    assertThrows(
        NullPointerException.class,
        () -> ProtocolRequestTemplateTopics.supports(nullOf(OperationId.class)));
  }
}
