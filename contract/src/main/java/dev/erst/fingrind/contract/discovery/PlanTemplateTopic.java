package dev.erst.fingrind.contract.discovery;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Stable topics for executable ledger-plan scaffolds. */
public enum PlanTemplateTopic {
  GENERAL("general"),
  TAX_SETUP("tax-setup"),
  FIXED_ASSET_SETUP("fixed-asset-setup"),
  FINANCING_SETUP("financing-setup");

  private final String wireName;

  PlanTemplateTopic(String wireName) {
    this.wireName = Objects.requireNonNull(wireName, "wireName");
  }

  /** Returns the command-line topic token. */
  public String wireName() {
    return wireName;
  }

  /** Returns all accepted command-line topic tokens. */
  public static List<String> wireNames() {
    return Arrays.stream(values()).map(PlanTemplateTopic::wireName).toList();
  }

  /** Parses one command-line topic token. */
  public static PlanTemplateTopic requireWireName(String value) {
    Objects.requireNonNull(value, "value");
    return Arrays.stream(values())
        .filter(topic -> topic.wireName.equals(value))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported plan-template topic '"
                        + value
                        + "'. Expected one of: "
                        + String.join(", ", wireNames())
                        + "."));
  }
}
