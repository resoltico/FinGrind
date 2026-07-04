package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Structural classifier inputs owned outside account-role incidence. */
public record StructuralContext(
    Optional<PostingId> reversesPriorPosting, boolean adoptionOpeningEntry) {
  /** Validates one structural context payload. */
  public StructuralContext {
    Objects.requireNonNull(reversesPriorPosting, "reversesPriorPosting");
  }

  /** Returns the ordinary non-structural context. */
  public static StructuralContext ordinary() {
    return new StructuralContext(Optional.empty(), false);
  }
}
