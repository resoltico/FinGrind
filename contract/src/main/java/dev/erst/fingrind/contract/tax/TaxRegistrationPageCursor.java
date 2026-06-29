package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.SingleTextPageCursorCodec;
import java.util.Objects;

/** Stable cursor for keyset pagination through ascending tax-registration order. */
public record TaxRegistrationPageCursor(TaxRegistrationId taxRegistrationId) {
  /** Validates one tax-registration page cursor. */
  public TaxRegistrationPageCursor {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    return SingleTextPageCursorCodec.encode(taxRegistrationId.value());
  }

  /** Parses one stable public wire value. */
  public static TaxRegistrationPageCursor fromWireValue(String wireValue) {
    return new TaxRegistrationPageCursor(
        new TaxRegistrationId(
            SingleTextPageCursorCodec.decode(
                wireValue, "Unsupported tax registration page cursor")));
  }

  /** Creates one cursor anchored at the supplied declared tax registration. */
  public static TaxRegistrationPageCursor fromRegistration(DeclaredTaxRegistration registration) {
    Objects.requireNonNull(registration, "registration");
    return new TaxRegistrationPageCursor(registration.taxRegistrationId());
  }
}
