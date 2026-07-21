package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable, byte-addressed entries in the normative attestation verification corpus. */
final class AttestationStaticCorpus {
  private AttestationStaticCorpus() {}

  static Fixture fixture(
      String id,
      byte[] baseSource,
      Mutation mutation,
      PolicyFold policyFold,
      VerificationScope scope,
      AttestationAuthorizationFailure expectedFirstFailure) {
    return new Fixture(id, baseSource, mutation, policyFold, scope, expectedFirstFailure);
  }

  static Fixture positive(
      String id, byte[] source, PolicyFold policyFold, VerificationScope scope) {
    return new Fixture(id, source, Mutation.none(), policyFold, scope, null);
  }

  static byte[] rawEnvelope(AttestationAuthorizationEnvelope envelope) {
    AttestationAuthorizationEnvelope checkedEnvelope = Objects.requireNonNull(envelope, "envelope");
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    output.writeBytes(checkedEnvelope.payload());
    int entryCount = checkedEnvelope.entries().size();
    output.write(entryCount >>> Byte.SIZE);
    output.write(entryCount);
    checkedEnvelope.entries().forEach(entry -> entry.appendTo(output));
    return output.toByteArray();
  }

  /** One literal base source, one exact mutation, and one declared verifier outcome. */
  static final class Fixture {
    private final String id;
    private final byte[] baseSource;
    private final Mutation mutation;
    private final PolicyFold policyFold;
    private final VerificationScope scope;
    private final @Nullable AttestationAuthorizationFailure expectedFirstFailure;

    Fixture(
        String id,
        byte[] baseSource,
        Mutation mutation,
        PolicyFold policyFold,
        VerificationScope scope,
        @Nullable AttestationAuthorizationFailure expectedFirstFailure) {
      if (Objects.requireNonNull(id, "id").isBlank()) {
        throw new IllegalArgumentException("fixture id must not be blank.");
      }
      this.id = id;
      this.baseSource = Objects.requireNonNull(baseSource, "baseSource").clone();
      this.mutation = Objects.requireNonNull(mutation, "mutation");
      this.policyFold = Objects.requireNonNull(policyFold, "policyFold");
      this.scope = Objects.requireNonNull(scope, "scope");
      this.expectedFirstFailure = expectedFirstFailure;
    }

    String id() {
      return id;
    }

    byte[] baseSource() {
      return baseSource.clone();
    }

    byte[] source() {
      return mutation.apply(baseSource);
    }

    Mutation mutation() {
      return mutation;
    }

    PolicyFold policyFold() {
      return policyFold;
    }

    VerificationScope scope() {
      return scope;
    }

    boolean isPositive() {
      return expectedFirstFailure == null;
    }

    AttestationAuthorizationFailure expectedFirstFailure() {
      if (expectedFirstFailure == null) {
        throw new IllegalStateException("A positive fixture has no failure outcome.");
      }
      return expectedFirstFailure;
    }
  }

  /** One or more non-overlapping replace-or-delete edits applied to the literal base source. */
  static final class Mutation {
    private static final Mutation NONE = new Mutation(java.util.List.of());

    private final java.util.List<Edit> edits;

    private Mutation(java.util.List<Edit> edits) {
      this.edits = java.util.List.copyOf(Objects.requireNonNull(edits, "edits"));
      int previousEnd = 0;
      for (Edit edit : this.edits) {
        if (edit.offset() < previousEnd) {
          throw new IllegalArgumentException("mutation edits must be ordered and non-overlapping.");
        }
        previousEnd = edit.offset() + edit.replacedByteCount();
      }
    }

    static Mutation replace(int offset, byte[] replacementBytes) {
      byte[] checkedBytes = Objects.requireNonNull(replacementBytes, "replacementBytes");
      return edits(edit(offset, checkedBytes.length, checkedBytes));
    }

    static Mutation edits(Edit... edits) {
      return new Mutation(java.util.List.of(edits));
    }

    static Edit edit(int offset, int replacedByteCount, byte[] replacementBytes) {
      return new Edit(offset, replacedByteCount, replacementBytes);
    }

    static Mutation none() {
      return NONE;
    }

    java.util.List<Edit> edits() {
      return edits;
    }

    byte[] apply(byte[] baseSource) {
      byte[] checkedBase = Objects.requireNonNull(baseSource, "baseSource");
      int resultingLength = checkedBase.length;
      for (Edit edit : edits) {
        if (edit.offset() > checkedBase.length
            || edit.offset() + edit.replacedByteCount() > checkedBase.length) {
          throw new IllegalArgumentException("mutation exceeds its base source.");
        }
        resultingLength += edit.replacementBytes().length - edit.replacedByteCount();
      }
      byte[] result = new byte[resultingLength];
      int sourceOffset = 0;
      int targetOffset = 0;
      for (Edit edit : edits) {
        int unchangedLength = edit.offset() - sourceOffset;
        System.arraycopy(checkedBase, sourceOffset, result, targetOffset, unchangedLength);
        targetOffset += unchangedLength;
        byte[] replacementBytes = edit.replacementBytes();
        System.arraycopy(replacementBytes, 0, result, targetOffset, replacementBytes.length);
        targetOffset += replacementBytes.length;
        sourceOffset = edit.offset() + edit.replacedByteCount();
      }
      System.arraycopy(
          checkedBase, sourceOffset, result, targetOffset, checkedBase.length - sourceOffset);
      return result;
    }

    /** One offset-addressed replacement in the base source. */
    static final class Edit {
      private final int offset;
      private final int replacedByteCount;
      private final byte[] replacementBytes;

      private Edit(int offset, int replacedByteCount, byte[] replacementBytes) {
        if (offset < 0 || replacedByteCount < 0) {
          throw new IllegalArgumentException("mutation bounds must not be negative.");
        }
        this.offset = offset;
        this.replacedByteCount = replacedByteCount;
        this.replacementBytes =
            Objects.requireNonNull(replacementBytes, "replacementBytes").clone();
      }

      int offset() {
        return offset;
      }

      int replacedByteCount() {
        return replacedByteCount;
      }

      byte[] replacementBytes() {
        return replacementBytes.clone();
      }
    }
  }

  /** Concrete registry facts expected at one fixed historical resolving position. */
  record PolicyFold(
      BigInteger resolvingOrder,
      AttestationCapability capability,
      int quorum,
      int eligiblePrincipalCount,
      int operatorEligiblePrincipalCount,
      int systemEligiblePrincipalCount,
      boolean activeSystemWorkflow) {
    PolicyFold {
      Objects.requireNonNull(resolvingOrder, "resolvingOrder");
      Objects.requireNonNull(capability, "capability");
      if (resolvingOrder.signum() < 0
          || quorum < 1
          || eligiblePrincipalCount < 0
          || operatorEligiblePrincipalCount < 0
          || systemEligiblePrincipalCount < 0) {
        throw new IllegalArgumentException("policy fold values must be non-negative and concrete.");
      }
    }

    void requireMatches(AttestationRegistry registry) {
      AttestationRegistry checkedRegistry = Objects.requireNonNull(registry, "registry");
      boolean actualActiveSystemWorkflow =
          checkedRegistry.hasActiveSystemWorkflow(
                  AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, resolvingOrder)
              || checkedRegistry.hasActiveSystemWorkflow(
                  AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE, resolvingOrder);
      java.util.List<String> mismatches = new ArrayList<>();
      if (checkedRegistry.quorumAt(capability, resolvingOrder) != quorum) {
        mismatches.add("quorum");
      }
      if (checkedRegistry.eligiblePrincipalCount(capability, resolvingOrder)
          != eligiblePrincipalCount) {
        mismatches.add("eligible principals");
      }
      if (checkedRegistry.eligiblePrincipalCount(
              capability, resolvingOrder, AttestationCredentialPurpose.OPERATOR)
          != operatorEligiblePrincipalCount) {
        mismatches.add("operator-purpose principals");
      }
      if (checkedRegistry.eligiblePrincipalCount(
              capability, resolvingOrder, AttestationCredentialPurpose.SYSTEM)
          != systemEligiblePrincipalCount) {
        mismatches.add("system-purpose principals");
      }
      if (actualActiveSystemWorkflow != activeSystemWorkflow) {
        mismatches.add("active system workflow");
      }
      if (!mismatches.isEmpty()) {
        throw new AssertionError(
            "Static corpus policy fold disagrees on " + String.join(", ", mismatches) + ".");
      }
    }
  }

  /** The verifier boundary at which a corpus row must fail. */
  enum VerificationScope {
    BOOK,
    GENESIS,
    ARTIFACT,
    RECEIPT,
    AUTHORIZATION,
    OPERATION_PROFILE,
    REGISTRY,
    SYSTEM_DERIVATION
  }
}
