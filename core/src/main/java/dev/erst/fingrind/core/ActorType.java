package dev.erst.fingrind.core;

import java.util.List;

/** Classifies the actor that initiated one posting request. */
public enum ActorType implements WireValue {
  PERSON,
  SYSTEM,
  AGENT;

  /** Returns the stable public wire value for this actor type. */
  @Override
  public String wireValue() {
    return switch (this) {
      case PERSON -> "PERSON";
      case SYSTEM -> "SYSTEM";
      case AGENT -> "AGENT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ActorType.class);
  }

  /** Parses one stable public wire value. */
  public static ActorType fromWireValue(String wireValue) {
    return WireValue.fromWireValue(ActorType.class, wireValue, "Unsupported actorType");
  }
}
