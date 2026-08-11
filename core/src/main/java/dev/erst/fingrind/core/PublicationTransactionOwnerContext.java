package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An opaque, authenticated lookup context for one higher-level publication operation.
 *
 * <p>This value is not a recovery authority and must never name a stage or filesystem path. A
 * higher-level adapter derives it from a canonical description of the operation it is resuming, and
 * the journal authenticates the resulting digest. The public recovery surface remains the
 * transaction ID; adapters use this value only to find the one journal already bound to their exact
 * operation while they hold that operation's final-target leases.
 */
public record PublicationTransactionOwnerContext(String value) {
  private static final Pattern CANONICAL_VALUE = Pattern.compile("[0-9a-f]{64}");

  /** Requires exactly one lowercase SHA-256 digest representation. */
  public PublicationTransactionOwnerContext {
    Objects.requireNonNull(value, "value");
    if (!CANONICAL_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Publication transaction owner context must be exactly 64 lowercase hexadecimal characters.");
    }
  }

  /**
   * Derives a context from one canonical, versioned operation description.
   *
   * <p>Callers must supply an unambiguous canonical representation; this method intentionally does
   * not accept a collection of ad-hoc fields whose serialization could drift between retry
   * attempts.
   */
  public static PublicationTransactionOwnerContext fromCanonicalDescription(String description) {
    String checkedDescription = Objects.requireNonNull(description, "description");
    return new PublicationTransactionOwnerContext(
        CryptographicPrimitives.sha256HexUtf8(checkedDescription));
  }
}
