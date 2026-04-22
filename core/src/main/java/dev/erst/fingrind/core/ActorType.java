package dev.erst.fingrind.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Classifies the actor that initiated one posting request. */
public enum ActorType implements WireValue {
  HUMAN,
  SYSTEM,
  AGENT;

  /** Returns the stable public wire value for this actor type. */
  @Override
  public String wireValue() {
    return switch (this) {
      case HUMAN -> "HUMAN";
      case SYSTEM -> "SYSTEM";
      case AGENT -> "AGENT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(ActorType::wireValue).toList();
  }

  /** Parses one stable public wire value. */
  public static ActorType fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported actorType: " + wireValue));
  }
}
