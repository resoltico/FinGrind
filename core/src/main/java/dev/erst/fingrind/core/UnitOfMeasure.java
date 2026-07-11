package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable inventory-account unit-of-measure token with one owned quantity scale. */
public record UnitOfMeasure(String token, int quantityScale) {
  private static final int MAX_LENGTH = 64;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$";

  /** Returns the canonical public regex accepted for one unit-of-measure token. */
  public static String pattern() {
    return PATTERN;
  }

  /** Returns the canonical maximum UTF-16 length accepted for one unit-of-measure token. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one unit-of-measure token with its owned quantity scale. */
  public UnitOfMeasure {
    Objects.requireNonNull(token, "token");
    token = token.strip();
    if (token.isEmpty()) {
      throw new IllegalArgumentException("Unit of measure token must not be blank.");
    }
    if (token.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Unit of measure token must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!token.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Unit of measure token must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
    Quantity.requireSupportedScale(quantityScale);
  }

  /** Parses one quantity owned by this unit of measure's exact scale. */
  public Quantity parseQuantity(String quantityText) {
    try {
      return Quantity.parse(quantityScale, quantityText);
    } catch (IllegalArgumentException exception) {
      throw new QuantityIncompatibleWithUnitOfMeasureException(quantityText, this, exception);
    }
  }

  /** Requires one quantity to match this unit of measure's exact quantity scale. */
  public void requireCompatible(Quantity quantity) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.scale() != quantityScale) {
      throw new IllegalArgumentException(
          "Quantity scale must match unit-of-measure scale " + quantityScale + ".");
    }
  }

  /** Raised when quantity text is incompatible with this unit of measure's exact scale. */
  public static final class QuantityIncompatibleWithUnitOfMeasureException
      extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String quantityText;
    private final transient UnitOfMeasure unitOfMeasure;

    /** Creates one quantity incompatibility against the selected unit of measure owner. */
    public QuantityIncompatibleWithUnitOfMeasureException(
        String quantityText, UnitOfMeasure unitOfMeasure, IllegalArgumentException cause) {
      super(
          "Quantity text is incompatible with the declared unit-of-measure scale.",
          Objects.requireNonNull(cause, "cause"));
      this.quantityText = Objects.requireNonNull(quantityText, "quantityText");
      this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    /** Returns the rejected quantity text. */
    public String quantityText() {
      return quantityText;
    }

    /** Returns the unit of measure whose exact scale rejected the quantity text. */
    public UnitOfMeasure unitOfMeasure() {
      return unitOfMeasure;
    }

    /** Returns the stable rejection reason that higher layers may publish verbatim. */
    public String reason() {
      return Objects.requireNonNullElse(
          Objects.requireNonNull(getCause(), "cause").getMessage(),
          "Quantity text is incompatible with the declared unit-of-measure scale.");
    }
  }
}
