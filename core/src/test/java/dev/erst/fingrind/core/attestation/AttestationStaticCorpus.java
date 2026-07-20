package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.Objects;

/** Immutable, byte-addressed entries in the normative attestation verification corpus. */
final class AttestationStaticCorpus {
  private AttestationStaticCorpus() {}

  static Fixture fixture(
      String id,
      byte[] rawSource,
      Mutation mutation,
      PolicyFold policyFold,
      VerificationScope scope,
      AttestationAuthorizationFailure expectedFirstFailure) {
    return new Fixture(id, rawSource, mutation, policyFold, scope, expectedFirstFailure);
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

  /** One raw source, mutation, policy fold, scope, and expected first verifier result. */
  static final class Fixture {
    private final String id;
    private final byte[] rawSource;
    private final Mutation mutation;
    private final PolicyFold policyFold;
    private final VerificationScope scope;
    private final AttestationAuthorizationFailure expectedFirstFailure;

    Fixture(
        String id,
        byte[] rawSource,
        Mutation mutation,
        PolicyFold policyFold,
        VerificationScope scope,
        AttestationAuthorizationFailure expectedFirstFailure) {
      if (Objects.requireNonNull(id, "id").isBlank()) {
        throw new IllegalArgumentException("fixture id must not be blank.");
      }
      this.id = id;
      this.rawSource = Objects.requireNonNull(rawSource, "rawSource").clone();
      this.mutation = Objects.requireNonNull(mutation, "mutation");
      this.policyFold = Objects.requireNonNull(policyFold, "policyFold");
      this.scope = Objects.requireNonNull(scope, "scope");
      this.expectedFirstFailure =
          Objects.requireNonNull(expectedFirstFailure, "expectedFirstFailure");
    }

    String id() {
      return id;
    }

    byte[] rawSource() {
      return rawSource.clone();
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

    AttestationAuthorizationFailure expectedFirstFailure() {
      return expectedFirstFailure;
    }
  }

  /** One replacement byte sequence at a concrete offset in a raw fixture source. */
  static final class Mutation {
    private final int offset;
    private final byte[] replacementBytes;

    Mutation(int offset, byte[] replacementBytes) {
      if (offset < 0) {
        throw new IllegalArgumentException("mutation offset must not be negative.");
      }
      this.offset = offset;
      this.replacementBytes = Objects.requireNonNull(replacementBytes, "replacementBytes").clone();
    }

    int offset() {
      return offset;
    }

    byte[] replacementBytes() {
      return replacementBytes.clone();
    }

    boolean isRepresentedBy(byte[] source) {
      Objects.requireNonNull(source, "source");
      return offset <= source.length
          && offset + replacementBytes.length <= source.length
          && Arrays.equals(
              replacementBytes,
              Arrays.copyOfRange(source, offset, offset + replacementBytes.length));
    }
  }

  record PolicyFold(String summary) {
    PolicyFold {
      if (Objects.requireNonNull(summary, "summary").isBlank()) {
        throw new IllegalArgumentException("policy fold summary must not be blank.");
      }
    }
  }

  /** The verifier boundary at which a corpus row must fail. */
  enum VerificationScope {
    BOOK,
    GENESIS,
    ARTIFACT,
    AUTHORIZATION,
    OPERATION_PROFILE,
    REGISTRY,
    SYSTEM_DERIVATION
  }
}
