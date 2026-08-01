package dev.erst.fingrind.core.attestation;

/** Canonical cardinality bounds shared by policy admission and non-genesis signing. */
public final class AttestationAuthorizationLimits {
  /** The smallest exact principal quorum that can authorize a signed structure. */
  public static final int MINIMUM_QUORUM = 1;

  /** The largest exact principal quorum accepted by the current attestation format. */
  public static final int MAXIMUM_QUORUM = 64;

  private AttestationAuthorizationLimits() {}
}
