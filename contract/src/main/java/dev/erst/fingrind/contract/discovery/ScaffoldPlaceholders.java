package dev.erst.fingrind.contract.discovery;

import java.util.Objects;
import java.util.Set;

/** Canonical scaffold placeholder values that must be replaced before submission. */
public final class ScaffoldPlaceholders {
  public static final String EFFECTIVE_DATE = "replace-before-commit-effective-date";
  public static final String ACTOR_ID = "replace-before-commit-actor-id";
  public static final String COMMAND_ID = "replace-before-commit-command-id";
  public static final String IDEMPOTENCY_KEY = "replace-before-commit-idempotency-key";
  public static final String CAUSATION_ID = "replace-before-commit-causation-id";

  private static final Set<String> RESERVED_VALUES =
      Set.of(EFFECTIVE_DATE, ACTOR_ID, COMMAND_ID, IDEMPOTENCY_KEY, CAUSATION_ID);

  private ScaffoldPlaceholders() {}

  /** Returns whether the supplied value is a canonical unreplaced scaffold placeholder. */
  public static boolean isReserved(String value) {
    return RESERVED_VALUES.contains(Objects.requireNonNull(value, "value"));
  }
}
