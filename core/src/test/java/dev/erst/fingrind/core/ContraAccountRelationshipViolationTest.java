package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct wire-contract coverage for every rejected contra-account relationship. */
class ContraAccountRelationshipViolationTest {
  @Test
  void wireValues_areStableAndComplete() {
    assertEquals(
        List.of(
            "self-reference",
            "target-missing",
            "target-inactive",
            "target-not-postable",
            "target-is-contra",
            "account-type-mismatch",
            "statement-taxonomy-mismatch"),
        ContraAccountRelationshipViolation.wireValues());
    assertEquals("self-reference", ContraAccountRelationshipViolation.SELF_REFERENCE.wireValue());
    assertEquals("target-missing", ContraAccountRelationshipViolation.TARGET_MISSING.wireValue());
    assertEquals("target-inactive", ContraAccountRelationshipViolation.TARGET_INACTIVE.wireValue());
    assertEquals(
        "target-not-postable", ContraAccountRelationshipViolation.TARGET_NOT_POSTABLE.wireValue());
    assertEquals(
        "target-is-contra", ContraAccountRelationshipViolation.TARGET_IS_CONTRA.wireValue());
    assertEquals(
        "account-type-mismatch",
        ContraAccountRelationshipViolation.ACCOUNT_TYPE_MISMATCH.wireValue());
    assertEquals(
        "statement-taxonomy-mismatch",
        ContraAccountRelationshipViolation.STATEMENT_TAXONOMY_MISMATCH.wireValue());
  }
}
