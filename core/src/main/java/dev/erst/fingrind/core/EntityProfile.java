package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical identity profile for one accounting entity. */
public record EntityProfile(BookEntityName displayName) {
  /** Validates one entity profile. */
  public EntityProfile {
    Objects.requireNonNull(displayName, "displayName");
  }
}
