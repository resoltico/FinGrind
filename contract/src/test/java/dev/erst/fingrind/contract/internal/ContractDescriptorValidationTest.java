package dev.erst.fingrind.contract.internal;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  void requireExactText_preservesExactValidValuesAndRejectsBlankValues() {
    assertEquals(
        "descriptor", ContractDescriptorValidation.requireExactText("descriptor", "field"));
    IllegalArgumentException blank =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractDescriptorValidation.requireExactText("  \t", "field"));
    assertEquals("field must not be blank.", blank.getMessage());
    IllegalArgumentException boundaryWhitespace =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractDescriptorValidation.requireExactText(" descriptor", "field"));
    assertEquals(
        "field must not contain leading or trailing whitespace.", boundaryWhitespace.getMessage());
    assertThrows(
        NullPointerException.class,
        () -> ContractDescriptorValidation.requireExactText(nullOf(), "field"));
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

  @Test
  void copySchemaMap_deepFreezesNestedMapsAndLists() {
    List<Object> required = new ArrayList<>(List.of("effectiveDate"));
    Map<String, Object> nestedProperties =
        new ConcurrentHashMap<>(Map.of("required", required, "type", "object"));
    Map<String, Object> schema =
        new ConcurrentHashMap<>(Map.of("properties", nestedProperties, "type", "object"));

    Map<String, Object> copied = ContractDescriptorValidation.copySchemaMap(schema, "schema");
    Map<String, Object> copiedProperties =
        castMap(Objects.requireNonNull(copied.get("properties"), "properties"));
    List<Object> copiedRequired =
        castList(Objects.requireNonNull(copiedProperties.get("required"), "required"));

    nestedProperties.clear();
    required.clear();

    assertEquals("object", copied.get("type"));
    assertEquals("object", copiedProperties.get("type"));
    assertEquals(List.of("effectiveDate"), copiedRequired);
    assertThrows(UnsupportedOperationException.class, () -> copied.put("items", Map.of()));
    assertThrows(
        UnsupportedOperationException.class, () -> copiedProperties.put("items", Map.of()));
    assertThrows(UnsupportedOperationException.class, () -> copiedRequired.add("posting"));
  }

  @Test
  void copySchemaMap_rejectsUnsupportedNestedValueTypes() {
    Map<String, Object> schema =
        new ConcurrentHashMap<>(Map.of("properties", new StringBuilder("invalid")));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractDescriptorValidation.copySchemaMap(schema, "schema"));

    assertEquals(
        "schema[properties] contains unsupported schema value type: java.lang.StringBuilder.",
        exception.getMessage());
  }

  @Test
  void copySchemaMap_preservesEnumValuesAsScalarSchemaLeaves() {
    Map<String, Object> schema =
        new ConcurrentHashMap<>(Map.of("operationId", OperationId.POST_ENTRY));

    Map<String, Object> copied = ContractDescriptorValidation.copySchemaMap(schema, "schema");

    assertEquals(OperationId.POST_ENTRY, copied.get("operationId"));
  }

  @Test
  void copySchemaMap_rejectsNonStringNestedMapKeys() {
    Map<Object, Object> nestedProperties = new ConcurrentHashMap<>(Map.of(1, "invalid"));
    Map<String, Object> schema = new ConcurrentHashMap<>(Map.of("properties", nestedProperties));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractDescriptorValidation.copySchemaMap(schema, "schema"));

    assertEquals(
        "schema[properties] must contain only string-keyed schema maps.", exception.getMessage());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> castList(Object value) {
    return (List<Object>) value;
  }
}
