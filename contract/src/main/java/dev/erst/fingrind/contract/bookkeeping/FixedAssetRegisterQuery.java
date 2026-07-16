package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Point-in-time request for the durable fixed-asset register. */
public record FixedAssetRegisterQuery(Optional<LocalDate> effectiveDateAsOf) {
  /** Validates one fixed-asset register request. */
  public FixedAssetRegisterQuery {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
  }
}
