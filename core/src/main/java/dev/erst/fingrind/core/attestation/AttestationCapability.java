package dev.erst.fingrind.core.attestation;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Closed authorization capabilities and their operation-kind ownership. */
enum AttestationCapability {
  POST("post"),
  APPROVE("approve"),
  CLOSE_PERIOD("close-period"),
  BACKUP("backup"),
  ANCHOR("anchor"),
  RESTORE("restore"),
  REKEY("rekey"),
  ENROLL_KEY("enroll-key"),
  REVOKE_KEY("revoke-key"),
  ALTER_POLICY("alter-policy");

  private static final Set<AttestationCapability> DUAL_CONTROL_CAPABILITIES =
      EnumSet.of(RESTORE, REKEY, ENROLL_KEY, REVOKE_KEY, ALTER_POLICY);

  private final String token;

  AttestationCapability(String token) {
    this.token = token;
  }

  String token() {
    return token;
  }

  static AttestationCapability forOperation(AttestationOperationKind operationKind) {
    return Objects.requireNonNull(operationKind, "operationKind").capability();
  }

  static AttestationAuthorizationException unknownOperation() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.UNKNOWN_OPERATION_KIND);
  }

  int genesisQuorum(int founderCount) {
    requireGenesisFounderCount(founderCount);
    return DUAL_CONTROL_CAPABILITIES.contains(this) ? Math.min(2, founderCount) : 1;
  }

  boolean admitsCliOperation() {
    return this != ANCHOR;
  }

  private static void requireGenesisFounderCount(int founderCount) {
    if (founderCount < 1 || founderCount > 5) {
      throw new IllegalArgumentException(
          "Attestation genesis founder count must be between one and five.");
    }
  }
}
