package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.absent;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.bool;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.currency;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.date;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.hash;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.instant;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.money;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.mutation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.optionalHash;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.spki;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.text;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.token;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u16;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u32;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u64;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u8;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.uuid;

import java.math.BigInteger;
import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Canonical records and positions shared by deterministic static corpus resource builders. */
final class AttestationCorpusFixtureValues {
  private AttestationCorpusFixtureValues() {}

  static AttestationPreimage genesisRequest(
      List<Signer> founders, Map<AttestationCapability, Integer> policy) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(
        command(
            AttestationOperationKind.BOOK_GENESIS,
            AttestationSourceChannel.CLI,
            "fixture-b-genesis-0"));
    facts.add(bookIdentityRequest());
    for (Signer founder : founders) {
      facts.add(founderRequest(founder));
      for (AttestationCapability capability : AttestationCapability.values()) {
        facts.add(grantRequest(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      facts.add(policyRequest(capability, Objects.requireNonNull(policy.get(capability))));
    }
    return AttestationPreimage.of(facts);
  }

  static AttestationPreimage genesisEffect(
      List<Signer> founders, Map<AttestationCapability, Integer> policy) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(bookIdentityEffect());
    for (Signer founder : founders) {
      facts.add(bindingEffect(founder, "enroll", "operator", null));
      for (AttestationCapability capability : AttestationCapability.values()) {
        facts.add(grantEffect(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      facts.add(policyEffect(capability, Objects.requireNonNull(policy.get(capability))));
    }
    return AttestationPreimage.of(facts);
  }

  static Map<AttestationCapability, Integer> initialPolicy(
      int founderCount, Map<AttestationCapability, Integer> overrides) {
    Map<AttestationCapability, Integer> policy = new java.util.concurrent.ConcurrentHashMap<>();
    for (AttestationCapability capability : AttestationCapability.values()) {
      policy.put(capability, capability.genesisQuorum(founderCount));
    }
    policy.putAll(overrides);
    return Map.copyOf(policy);
  }

  static AttestationPreimage.Fact bookIdentityRequest() {
    return new AttestationPreimage.Fact(
        0x0101,
        List.of(
            uuid(AttestationCorpusFixtures.BOOK_ID),
            text("Acme Attestation Fixture"),
            token("internal-management-bookkeeping-kernel"),
            token("cash"),
            token("non-statutory-internal-management"),
            token("owner-managed-single-entity"),
            token("owner-managed-service"),
            absent(),
            currency(),
            u8(1),
            u8(1),
            date(LocalDate.of(2026, 1, 1))));
  }

  static AttestationPreimage.Fact bookIdentityEffect() {
    return new AttestationPreimage.Fact(
        0x0001,
        List.of(
            mutation(),
            uuid(AttestationCorpusFixtures.BOOK_ID),
            text("Acme Attestation Fixture"),
            token("internal-management-bookkeeping-kernel"),
            token("cash"),
            token("non-statutory-internal-management"),
            token("owner-managed-single-entity"),
            token("owner-managed-service"),
            absent(),
            currency(),
            u8(1),
            u8(1),
            date(LocalDate.of(2026, 1, 1))));
  }

  static AttestationPreimage.Fact founderRequest(Signer signer) {
    return new AttestationPreimage.Fact(
        0x0102,
        List.of(uuid(signer.principalId()), hash(signer.keyId()), spki(signer), token("operator")));
  }

  static AttestationPreimage.Fact bindingRequest(
      Signer signer, String action, String purpose, @Nullable AttestationHash predecessor) {
    return new AttestationPreimage.Fact(
        0x0180,
        List.of(
            uuid(signer.principalId()),
            hash(signer.keyId()),
            token(action),
            spki(signer),
            token(purpose),
            optionalHash(predecessor)));
  }

  static AttestationPreimage.Fact bindingEffect(
      Signer signer, String action, String purpose, @Nullable AttestationHash predecessor) {
    return new AttestationPreimage.Fact(
        0x0002,
        List.of(
            mutation(),
            uuid(signer.principalId()),
            hash(signer.keyId()),
            token(action),
            spki(signer),
            token(purpose),
            optionalHash(predecessor)));
  }

  static AttestationPreimage.Fact grantRequest(Signer signer, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0183, List.of(uuid(signer.principalId()), token(capability.token()), token("grant")));
  }

  static AttestationPreimage.Fact grantEffect(Signer signer, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0003,
        List.of(mutation(), uuid(signer.principalId()), token(capability.token()), token("grant")));
  }

  static AttestationPreimage.Fact policyRequest(AttestationCapability capability, int quorum) {
    return new AttestationPreimage.Fact(0x0103, List.of(token(capability.token()), u16(quorum)));
  }

  static AttestationPreimage.Fact policyEffect(AttestationCapability capability, int quorum) {
    return new AttestationPreimage.Fact(
        0x0005, List.of(mutation(), token(capability.token()), u16(quorum)));
  }

  static AttestationPreimage.Fact revocationRequest(Signer signer) {
    return new AttestationPreimage.Fact(
        0x0181, List.of(hash(signer.keyId()), uuid(signer.principalId()), absent()));
  }

  static AttestationPreimage.Fact revocationEffect(Signer signer) {
    return new AttestationPreimage.Fact(
        0x0004, List.of(mutation(), hash(signer.keyId()), uuid(signer.principalId()), absent()));
  }

  static AttestationPreimage.Fact backupRequest(
      UUID backupId,
      AttestationHash artifactDigest,
      BigInteger sourceOrder,
      AttestationHash sourceHead) {
    return new AttestationPreimage.Fact(
        0x0150,
        List.of(
            uuid(backupId),
            hash(artifactDigest),
            u64(sourceOrder.longValueExact()),
            hash(sourceHead)));
  }

  static AttestationPreimage.Fact backupEffect(
      UUID backupId,
      AttestationHash artifactDigest,
      BigInteger sourceOrder,
      AttestationHash sourceHead) {
    return new AttestationPreimage.Fact(
        0x0006,
        List.of(
            mutation(),
            uuid(backupId),
            hash(artifactDigest),
            u64(sourceOrder.longValueExact()),
            hash(sourceHead)));
  }

  static AttestationPreimage.Fact restoreRequest(
      UUID backupId,
      AttestationHash artifactDigest,
      BigInteger sourceOrder,
      AttestationHash sourceHead) {
    return new AttestationPreimage.Fact(
        0x0160,
        List.of(
            uuid(backupId),
            hash(artifactDigest),
            u64(sourceOrder.longValueExact()),
            hash(sourceHead)));
  }

  static AttestationPreimage.Fact restoreEffect(
      UUID backupId, AttestationHash artifactDigest, BigInteger sourceOrder) {
    return new AttestationPreimage.Fact(
        0x00A0,
        List.of(
            mutation(),
            uuid(backupId),
            hash(artifactDigest),
            u64(sourceOrder.longValueExact()),
            bool(true)));
  }

  static AttestationPreimage.Fact rekeyRequest() {
    return new AttestationPreimage.Fact(0x0170, List.of(u64(2), absent()));
  }

  static AttestationPreimage.Fact rekeyEffect(Instant rekeyedAt) {
    return new AttestationPreimage.Fact(0x0007, List.of(mutation(), u64(2), instant(rekeyedAt)));
  }

  static AttestationPreimage.Fact postingFact(
      UUID postingId,
      UUID commandId,
      AttestationOperationKind kind,
      LocalDate effectiveDate,
      Instant recordedAt,
      String idempotencyKey,
      AttestationSourceChannel sourceChannel) {
    String postingKind =
        kind == AttestationOperationKind.INTERIM_RESULT_SWEEP
                || kind == AttestationOperationKind.FISCAL_YEAR_CLOSE
            ? "period-close"
            : "standard";
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            mutation(),
            uuid(postingId),
            u32(0),
            token(kind.wireToken()),
            token(postingKind),
            token(
                kind == AttestationOperationKind.RECORD_SALE_SETTLED
                    ? "sale-settled"
                    : kind.wireToken()),
            date(effectiveDate),
            instant(recordedAt),
            absent(),
            uuid(commandId),
            text(idempotencyKey),
            absent(),
            token(sourceChannel.wireToken())));
  }

  static AttestationPreimage.Fact journalLine(
      UUID postingId, int order, String accountCode, String side) {
    return new AttestationPreimage.Fact(
        0x0025,
        List.of(
            mutation(),
            uuid(postingId),
            u32(order),
            text(accountCode),
            token(side),
            money(10_000),
            absent()));
  }

  static AttestationPreimage.Fact command(
      AttestationOperationKind kind,
      AttestationSourceChannel sourceChannel,
      String idempotencyKey) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            token(kind.wireToken()),
            text(idempotencyKey),
            absent(),
            token(sourceChannel.wireToken())));
  }

  static AttestationCredentialBinding binding(
      Signer signer, int acceptedOrder, String purpose, @Nullable AttestationHash predecessor) {
    return new AttestationCredentialBinding(
        BigInteger.valueOf(acceptedOrder),
        signer.principalId(),
        signer.keyId(),
        predecessor == null
            ? AttestationCredentialBinding.BindingAction.ENROLL
            : AttestationCredentialBinding.BindingAction.ROLLOVER,
        AttestationSpki.of(signer.keyPair().getPublic().getEncoded()),
        "system".equals(purpose)
            ? AttestationCredentialPurpose.SYSTEM
            : AttestationCredentialPurpose.OPERATOR,
        predecessor);
  }

  static AttestationCapabilityGrant grant(
      Signer signer, AttestationCapability capability, int acceptedOrder) {
    return new AttestationCapabilityGrant(
        BigInteger.valueOf(acceptedOrder),
        signer.principalId(),
        capability,
        AttestationGrantState.GRANT);
  }

  static AttestationPreimage preimage(AttestationPreimage.Fact... facts) {
    return AttestationPreimage.of(List.of(facts));
  }

  static Instant recordedAt(int operationOrder) {
    return AttestationCorpusFixtures.GENESIS_RECORDED_AT.plusMillis(operationOrder);
  }

  static UUID postingId(int suffix) {
    return UUID.fromString(String.format("30000000-0000-7000-8000-%012d", suffix));
  }

  static UUID commandId(int suffix) {
    return UUID.fromString(String.format("30000000-0000-7000-8000-%012d", suffix + 1));
  }

  record Signer(UUID principalId, KeyPair keyPair, AttestationHash keyId) {
    Signer {
      Objects.requireNonNull(principalId, "principalId");
      Objects.requireNonNull(keyPair, "keyPair");
      Objects.requireNonNull(keyId, "keyId");
    }
  }
}
