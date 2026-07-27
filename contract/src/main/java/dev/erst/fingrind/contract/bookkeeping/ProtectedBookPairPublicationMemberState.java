package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Durable-publication fact for one member of a protected-book book-and-key pair. */
public enum ProtectedBookPairPublicationMemberState implements WireValue {
  /** No trustworthy final-member publication fact can be established from retained evidence. */
  UNESTABLISHED,

  /** FinGrind did not invoke this member's final publication primitive. */
  NOT_ATTEMPTED,

  /**
   * The publication primitive failed without establishing whether its namespace change occurred.
   */
  OUTCOME_UNCERTAIN,

  /** The final primitive returned, but parent-directory durability could not be confirmed. */
  PUBLISHED_DURABILITY_UNCONFIRMED,

  /** The final primitive returned and parent-directory durability was confirmed. */
  PUBLISHED_DURABLE;

  @Override
  public String wireValue() {
    return switch (this) {
      case UNESTABLISHED -> "unestablished";
      case NOT_ATTEMPTED -> "not-attempted";
      case OUTCOME_UNCERTAIN -> "outcome-uncertain";
      case PUBLISHED_DURABILITY_UNCONFIRMED -> "published-durability-unconfirmed";
      case PUBLISHED_DURABLE -> "published-durable";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtectedBookPairPublicationMemberState.class);
  }
}
