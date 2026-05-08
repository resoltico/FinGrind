package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the shared {@link WireValue} enum vocabulary cache. */
class WireValueTest {
  private static final MethodHandle WIRE_VALUES = wireValueMethod("wireValues");
  private static final MethodHandle FROM_WIRE_VALUE = wireValueMethod("fromWireValue");

  @Test
  void helpers_publishAndParseStableEnumVocabularies() {
    assertEquals(List.of("DEBIT", "CREDIT"), WireValue.wireValues(NormalBalance.class));
    assertEquals(
        NormalBalance.DEBIT,
        WireValue.fromWireValue(NormalBalance.class, "DEBIT", "Unsupported normalBalance"));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
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
    Object result = invokeWireValueHelper(WIRE_VALUES, enumType);
    return ((List<?>) result).stream().map(String.class::cast).toList();
  }

  private static Object unsafeFromWireValue(
      Class<?> enumType, String wireValue, String unsupportedValueLabel) {
    return invokeWireValueHelper(FROM_WIRE_VALUE, enumType, wireValue, unsupportedValueLabel);
  }

  private static Object invokeWireValueHelper(MethodHandle helper, Object... arguments) {
    try {
      return helper.invokeWithArguments(arguments);
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke a WireValue helper.", throwable);
    }
  }

  private static MethodHandle wireValueMethod(String methodName) {
    try {
      return switch (methodName) {
        case "wireValues" ->
            MethodHandles.lookup()
                .findStatic(
                    WireValue.class, methodName, MethodType.methodType(List.class, Class.class));
        case "fromWireValue" ->
            MethodHandles.lookup()
                .findStatic(
                    WireValue.class,
                    methodName,
                    MethodType.methodType(Enum.class, Class.class, String.class, String.class));
        default ->
            throw new IllegalArgumentException("Unsupported WireValue helper: " + methodName);
      };
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new LinkageError("Failed to bind WireValue helper " + methodName + ".", exception);
    }
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
