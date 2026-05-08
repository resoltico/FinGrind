package dev.erst.fingrind.contract.internal;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        NullPointerException.class,
        () -> ContractDescriptorValidation.requireText(nullOf(), "field"));
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
        NullPointerException.class,
        () -> ContractDescriptorValidation.requireValue(nullOf(), "field"));
  }

  @Test
  void copyList_rejectsNullListAndDefensivelyCopiesValues() {
    assertEquals(
        "field must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> ContractDescriptorValidation.copyList(nullOf(), "field"))
            .getMessage());
    List<String> values = new ArrayList<>(List.of("alpha"));
    List<String> copied = ContractDescriptorValidation.copyList(values, "field");
    values.clear();
    assertEquals(List.of("alpha"), copied);
    assertThrows(UnsupportedOperationException.class, () -> copied.add("beta"));
    assertThrows(
        NullPointerException.class,
        () -> ContractDescriptorValidation.copyList(List.of(), nullOf()));
  }

  @Test
  void copyList_rejectsNullElementsWithFieldContext() {
    List<String> values = new ArrayList<>();
    values.add("alpha");
    values.add(nullOf());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractDescriptorValidation.copyList(values, "field"));

    assertEquals("field[1] must not be null.", exception.getMessage());
  }

  @Test
  void copyMap_rejectsNullMapAndDefensivelyCopiesValues() {
    assertEquals(
        "field must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> ContractDescriptorValidation.copyMap(nullOf(), "field"))
            .getMessage());
    Map<String, String> values = new ConcurrentHashMap<>(Map.of("alpha", "one"));
    Map<String, String> copied = ContractDescriptorValidation.copyMap(values, "field");
    values.clear();
    assertEquals(Map.of("alpha", "one"), copied);
    assertEquals(List.of("alpha"), List.copyOf(copied.keySet()));
    assertThrows(UnsupportedOperationException.class, () -> copied.put("beta", "two"));
    assertThrows(
        NullPointerException.class, () -> ContractDescriptorValidation.copyMap(Map.of(), nullOf()));
  }

  @Test
  void copyMap_rejectsNullKeysAndValuesWithFieldContext() {
    Map<String, String> nullKeyValues = Collections.singletonMap(nullOf(), "one");
    Map<String, String> nullValueValues = Collections.singletonMap("alpha", nullOf());

    NullPointerException nullKey =
        assertThrows(
            NullPointerException.class,
            () -> ContractDescriptorValidation.copyMap(nullKeyValues, "field"));
    NullPointerException nullValue =
        assertThrows(
            NullPointerException.class,
            () -> ContractDescriptorValidation.copyMap(nullValueValues, "field"));

    assertEquals("field must not contain a null key.", nullKey.getMessage());
    assertEquals("field[alpha] must not contain a null value.", nullValue.getMessage());
  }
}
