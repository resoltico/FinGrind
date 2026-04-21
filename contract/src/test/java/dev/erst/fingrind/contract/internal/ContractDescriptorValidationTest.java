package dev.erst.fingrind.contract.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers invariant helpers shared by machine-contract descriptor records. */
class ContractDescriptorValidationTest {
  @Test
  void requireText_trimsAndRejectsBlankValues() {
    assertEquals("descriptor", ContractDescriptorValidation.requireText(" descriptor ", "field"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ContractDescriptorValidation.requireText(" ", "field"));
    assertThrows(
        NullPointerException.class, () -> ContractDescriptorValidation.requireText(null, "field"));
  }

  @Test
  void requireOptionalText_allowsNullAndTrimsPresentValues() {
    assertNull(ContractDescriptorValidation.requireOptionalText(null, "field"));
    assertEquals(
        "descriptor", ContractDescriptorValidation.requireOptionalText(" descriptor ", "field"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ContractDescriptorValidation.requireOptionalText(" ", "field"));
  }

  @Test
  void requireValue_requiresNonNullReferences() {
    assertEquals("value", ContractDescriptorValidation.requireValue("value", "field"));
    assertThrows(
        NullPointerException.class, () -> ContractDescriptorValidation.requireValue(null, "field"));
  }

  @Test
  void copyList_coalescesNullAndDefensivelyCopiesValues() {
    assertEquals(List.of(), ContractDescriptorValidation.copyList(null, "field"));

    List<String> values = new ArrayList<>(List.of("alpha"));
    List<String> copied = ContractDescriptorValidation.copyList(values, "field");
    values.clear();

    assertEquals(List.of("alpha"), copied);
    assertThrows(UnsupportedOperationException.class, () -> copied.add("beta"));
    assertThrows(
        NullPointerException.class, () -> ContractDescriptorValidation.copyList(List.of(), null));
  }
}
