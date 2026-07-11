package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UnitOfMeasure}. */
class UnitOfMeasureTest {
  @Test
  void constructor_normalizesAndValidatesTokenAndScale() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("  kg  ", 3);

    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$", UnitOfMeasure.pattern());
    assertEquals(64, UnitOfMeasure.maxLength());
    assertEquals("kg", unitOfMeasure.token());
    assertEquals(3, unitOfMeasure.quantityScale());
    assertEquals("1.250", unitOfMeasure.parseQuantity("1.25").canonicalDecimal());
    assertEquals(
        "Unit of measure token must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> new UnitOfMeasure("   ", 0))
            .getMessage());
    assertEquals(
        "Unit of measure token must not exceed 64 characters.",
        assertThrows(IllegalArgumentException.class, () -> new UnitOfMeasure("x".repeat(65), 0))
            .getMessage());
    assertEquals(
        "Unit of measure token must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.",
        assertThrows(IllegalArgumentException.class, () -> new UnitOfMeasure("kg*", 0))
            .getMessage());
    assertEquals(
        "Quantity scale must be between 0 and 9 inclusive.",
        assertThrows(IllegalArgumentException.class, () -> new UnitOfMeasure("kg", -1))
            .getMessage());
  }

  @Test
  void requireCompatible_enforcesQuantityScaleOwnership() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("unit", 2);

    unitOfMeasure.requireCompatible(Quantity.parse(2, "1.25"));
    assertEquals(
        "Quantity scale must match unit-of-measure scale 2.",
        assertThrows(
                IllegalArgumentException.class,
                () -> unitOfMeasure.requireCompatible(Quantity.parse(1, "1.2")))
            .getMessage());
  }

  @Test
  void parseQuantity_wrapsScaleIncompatibilityWithTypedFailure() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("unit", 0);

    UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException failure =
        assertThrows(
            UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException.class,
            () -> unitOfMeasure.parseQuantity("0.5"));

    assertEquals(
        "Quantity text is incompatible with the declared unit-of-measure scale.",
        failure.getMessage());
    assertEquals("0.5", failure.quantityText());
    assertSame(unitOfMeasure, failure.unitOfMeasure());
    assertEquals("Quantity must not contain fractional digits at scale 0.", failure.reason());
    Throwable cause = Objects.requireNonNull(failure.getCause());
    assertEquals("Quantity must not contain fractional digits at scale 0.", cause.getMessage());
  }

  @Test
  void quantityIncompatibleWithUnitOfMeasureRejectsNullCause() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("unit", 0);

    assertThrows(
        NullPointerException.class,
        () ->
            new UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException(
                "0.5", unitOfMeasure, nullOf()));
  }

  @Test
  void constructorAndCompatibilityRejectNulls() {
    assertThrows(NullPointerException.class, () -> new UnitOfMeasure(nullOf(), 0));
    assertThrows(
        NullPointerException.class, () -> new UnitOfMeasure("unit", 0).requireCompatible(nullOf()));
  }
}
