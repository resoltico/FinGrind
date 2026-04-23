package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the shared {@link WireValue} enum vocabulary cache. */
@NullUnmarked
class WireValueTest {
  @Test
  void helpers_publishAndParseStableEnumVocabularies() {
    assertEquals(List.of("DEBIT", "CREDIT"), WireValue.wireValues(NormalBalance.class));
    assertEquals(
        NormalBalance.DEBIT,
        WireValue.fromWireValue(NormalBalance.class, "DEBIT", "Unsupported normalBalance"));
  }

  @Test
  void helpers_rejectNullAndUnknownInputs() {
    assertThrows(NullPointerException.class, () -> WireValue.wireValues(null));
    assertThrows(
        NullPointerException.class,
        () -> WireValue.fromWireValue(NormalBalance.class, null, "Unsupported normalBalance"));
    assertThrows(
        NullPointerException.class,
        () -> WireValue.fromWireValue(NormalBalance.class, "DEBIT", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> WireValue.fromWireValue(NormalBalance.class, "ZERO", "Unsupported normalBalance"));
  }

  @Test
  void helpers_rejectTypesThatDoNotOwnWireValues() {
    assertThrows(IllegalArgumentException.class, () -> unsafeWireValues(String.class));
    assertThrows(IllegalArgumentException.class, () -> unsafeWireValues(PlainEnum.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> unsafeFromWireValue(PlainEnum.class, "VALUE", "Unsupported plain"));
  }

  @Test
  void helpers_rejectBrokenEnumContracts() {
    assertThrows(IllegalStateException.class, () -> unsafeWireValues(DuplicateWireValue.class));
    assertThrows(IllegalStateException.class, () -> unsafeWireValues(BlankWireValue.class));
  }

  private static List<String> unsafeWireValues(Class<?> enumType) {
    return WireValue.wireValues((Class) enumType);
  }

  private static Object unsafeFromWireValue(
      Class<?> enumType, String wireValue, String unsupportedValueLabel) {
    return WireValue.fromWireValue((Class) enumType, wireValue, unsupportedValueLabel);
  }

  /** Enum without the {@link WireValue} contract. */
  private enum PlainEnum {
    VALUE
  }

  /** Enum that violates the unique wire-value invariant. */
  private enum DuplicateWireValue implements WireValue {
    LEFT("same"),
    RIGHT("same");

    private final String wireValue;

    DuplicateWireValue(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }

  /** Enum that violates the non-blank wire-value invariant. */
  private enum BlankWireValue implements WireValue {
    BLANK(" ");

    private final String wireValue;

    BlankWireValue(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }
}
