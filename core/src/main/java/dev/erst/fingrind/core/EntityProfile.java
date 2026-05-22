package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Canonical identity profile for one accounting entity. */
public record EntityProfile(
    BookEntityName displayName, List<BusinessActivityTag> businessActivityTags) {
  /** Validates one entity profile. */
  public EntityProfile {
    Objects.requireNonNull(displayName, "displayName");
    businessActivityTags =
        List.copyOf(Objects.requireNonNull(businessActivityTags, "businessActivityTags"));
  }
}
