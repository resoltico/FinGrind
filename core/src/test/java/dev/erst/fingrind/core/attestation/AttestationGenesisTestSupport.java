package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.BOOK_ID;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.orderedEntries;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Shared deterministic genesis builders for attestation authorization tests. */
final class AttestationGenesisTestSupport {
  private AttestationGenesisTestSupport() {}

  static AttestationGenesisAuthorizationContext genesisContext(TestCredential... credentials) {
    AttestationPreimage requestPreimage = genesisRequestPreimage(credentials);
    AttestationPreimage effectPreimage = genesisEffectPreimage(credentials);
    return AttestationGenesisAuthorizationContext.verify(
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            requestPreimage,
            effectPreimage),
        requestPreimage,
        effectPreimage);
  }

  static AttestationPreimage genesisRequestPreimage() {
    return command(AttestationOperationKind.BOOK_GENESIS, AttestationSourceChannel.CLI);
  }

  static AttestationPreimage genesisRequestPreimage(TestCredential... credentials) {
    List<AttestationFounder> founders =
        java.util.Arrays.stream(credentials).map(AttestationGenesisTestSupport::founder).toList();
    List<AttestationPreimage.Fact> records = new ArrayList<>();
    records.add(commandFact(AttestationOperationKind.BOOK_GENESIS, AttestationSourceChannel.CLI));
    records.add(bookIdentityRequest());
    for (AttestationFounder founder : founders) {
      records.add(founderRequest(founder));
      for (AttestationCapability capability : AttestationCapability.values()) {
        records.add(founderGrantRequest(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      records.add(genesisPolicyRequest(capability, founders.size()));
    }
    return AttestationPreimage.of(records);
  }

  static AttestationPreimage genesisEffectPreimage(TestCredential... credentials) {
    List<AttestationFounder> founders =
        java.util.Arrays.stream(credentials).map(AttestationGenesisTestSupport::founder).toList();
    List<AttestationPreimage.Fact> records = new ArrayList<>();
    records.add(bookIdentity());
    for (AttestationFounder founder : founders) {
      records.add(founderBinding(founder));
      for (AttestationCapability capability : AttestationCapability.values()) {
        records.add(founderGrant(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      records.add(genesisPolicy(capability, founders.size()));
    }
    return AttestationPreimage.of(records);
  }

  static AttestationOperationPayload genesisPayload(
      BigInteger operationOrder,
      AttestationHash previousHead,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    return new AttestationOperationPayload(
        BOOK_ID,
        operationOrder,
        AttestationOperationKind.BOOK_GENESIS.wireToken(),
        previousHead,
        Instant.parse("2026-07-20T00:00:00Z"),
        AttestationHash.sha256(requestPreimage.encoded()),
        AttestationHash.sha256(effectPreimage.encoded()));
  }

  static AttestationFounder founder(TestCredential credential) {
    return new AttestationFounder(
        credential.principalId(),
        credential.keyId(),
        AttestationSpki.of(credential.pair().getPublic().getEncoded()));
  }

  static AttestationAuthorizationEnvelope signedGenesisEnvelope(
      AttestationGenesisAuthorizationContext context, TestCredential... credentials) {
    byte[] payload = context.payload();
    return new AttestationAuthorizationEnvelope(payload, orderedEntries(payload, credentials));
  }

  static AttestationPreimage replaceFirstRecord(
      AttestationPreimage preimage,
      int recordTypeTag,
      UnaryOperator<AttestationPreimage.Fact> replacement) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(preimage.records());
    for (int index = 0; index < records.size(); index++) {
      if (records.get(index).recordTypeTag() == recordTypeTag) {
        records.set(index, replacement.apply(records.get(index)));
        return AttestationPreimage.of(records);
      }
    }
    throw new IllegalArgumentException("Expected test preimage record type is absent.");
  }

  static AttestationPreimage appendRecord(
      AttestationPreimage preimage, AttestationPreimage.Fact record) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(preimage.records());
    records.add(record);
    return AttestationPreimage.of(records);
  }

  static AttestationPreimage withoutRecords(AttestationPreimage preimage, int recordTypeTag) {
    return AttestationPreimage.of(
        preimage.records().stream()
            .filter(record -> record.recordTypeTag() != recordTypeTag)
            .toList());
  }

  static AttestationPreimage.Fact withField(
      AttestationPreimage.Fact record, int fieldIndex, AttestationField replacement) {
    List<AttestationField> fields = new ArrayList<>(record.fields());
    fields.set(fieldIndex, replacement);
    return new AttestationPreimage.Fact(record.recordTypeTag(), fields);
  }

  private static AttestationPreimage command(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    return AttestationPreimage.of(List.of(commandFact(operationKind, sourceChannel)));
  }

  private static AttestationPreimage.Fact commandFact(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(operationKind.wireToken())),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationField.present(AttestationTextFieldValue.token(sourceChannel.wireToken()))));
  }

  private static AttestationPreimage.Fact bookIdentity() {
    List<AttestationField> fields = new ArrayList<>();
    fields.add(AttestationField.present(AttestationNumericFieldValue.mutation(0)));
    fields.addAll(bookIdentityFields());
    return new AttestationPreimage.Fact(0x0001, fields);
  }

  private static AttestationPreimage.Fact bookIdentityRequest() {
    return new AttestationPreimage.Fact(0x0101, bookIdentityFields());
  }

  private static List<AttestationField> bookIdentityFields() {
    return List.of(
        AttestationField.present(AttestationBinaryFieldValue.uuid(BOOK_ID)),
        AttestationField.present(AttestationTextFieldValue.text("Genesis test book")),
        AttestationField.present(AttestationTextFieldValue.token("standard")),
        AttestationField.present(AttestationTextFieldValue.token("cash")),
        AttestationField.present(AttestationTextFieldValue.token("local")),
        AttestationField.present(AttestationTextFieldValue.token("limited")),
        AttestationField.present(AttestationTextFieldValue.token("service")),
        AttestationField.absent(),
        AttestationField.present(AttestationTextFieldValue.currency("EUR")),
        AttestationField.present(AttestationNumericFieldValue.unsigned8(1)),
        AttestationField.present(AttestationNumericFieldValue.unsigned8(1)),
        AttestationField.present(AttestationTextFieldValue.date(LocalDate.of(2026, 1, 1))));
  }

  private static AttestationPreimage.Fact founderBinding(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0002,
        List.of(
            AttestationField.present(AttestationNumericFieldValue.mutation(0)),
            AttestationField.present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            AttestationField.present(AttestationBinaryFieldValue.hash(founder.keyId())),
            AttestationField.present(AttestationTextFieldValue.token("enroll")),
            AttestationField.present(AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            AttestationField.present(AttestationTextFieldValue.token("operator")),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact founderGrant(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0003,
        List.of(
            AttestationField.present(AttestationNumericFieldValue.mutation(0)),
            AttestationField.present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            AttestationField.present(AttestationTextFieldValue.token(capability.token())),
            AttestationField.present(AttestationTextFieldValue.token("grant"))));
  }

  private static AttestationPreimage.Fact founderRequest(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0102,
        List.of(
            AttestationField.present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            AttestationField.present(AttestationBinaryFieldValue.hash(founder.keyId())),
            AttestationField.present(AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            AttestationField.present(AttestationTextFieldValue.token("operator"))));
  }

  private static AttestationPreimage.Fact founderGrantRequest(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0183,
        List.of(
            AttestationField.present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            AttestationField.present(AttestationTextFieldValue.token(capability.token())),
            AttestationField.present(AttestationTextFieldValue.token("grant"))));
  }

  private static AttestationPreimage.Fact genesisPolicy(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0005,
        List.of(
            AttestationField.present(AttestationNumericFieldValue.mutation(0)),
            AttestationField.present(AttestationTextFieldValue.token(capability.token())),
            AttestationField.present(
                AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }

  private static AttestationPreimage.Fact genesisPolicyRequest(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0103,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(capability.token())),
            AttestationField.present(
                AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }
}
