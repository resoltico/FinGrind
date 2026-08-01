package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Projects the self-authorizing genesis request and initial authorization-registry effect facts.
 */
final class AttestationGenesisPreimageProjection {
  private AttestationGenesisPreimageProjection() {}

  static AttestationOperationPreimages project(
      UUID bookId, BookIdentity bookIdentity, List<AttestationFounder> founders) {
    return new AttestationOperationPreimages(
        requestPreimage(bookId, bookIdentity, founders).encoded(),
        effectPreimage(bookId, bookIdentity, founders).encoded());
  }

  static AttestationPreimage.Fact bookIdentityEffect(UUID bookId, BookIdentity identity) {
    List<AttestationField> fields = new ArrayList<>();
    fields.add(
        AttestationPreimageProjectionFields.present(AttestationNumericFieldValue.mutation(0)));
    fields.addAll(bookIdentityFields(bookId, identity));
    return new AttestationPreimage.Fact(0x0001, fields);
  }

  private static AttestationPreimage requestPreimage(
      UUID bookId, BookIdentity bookIdentity, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(command());
    facts.add(bookIdentityRequest(bookId, bookIdentity));
    for (AttestationFounder founder : founders) {
      facts.add(founderRequest(founder));
      for (AttestationCapability capability : AttestationCapability.values()) {
        facts.add(founderGrantRequest(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      facts.add(policyRequest(capability, founders.size()));
    }
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage effectPreimage(
      UUID bookId, BookIdentity bookIdentity, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(bookIdentityEffect(bookId, bookIdentity));
    for (AttestationFounder founder : founders) {
      facts.add(founderBinding(founder));
      for (AttestationCapability capability : AttestationCapability.values()) {
        facts.add(founderGrant(founder, capability));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      facts.add(policyEffect(capability, founders.size()));
    }
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact command() {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationPreimageProjectionFields.token(
                AttestationOperationKind.BOOK_GENESIS.wireToken()),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationPreimageProjectionFields.token(AttestationSourceChannel.CLI.wireToken())));
  }

  private static AttestationPreimage.Fact bookIdentityRequest(UUID bookId, BookIdentity identity) {
    return new AttestationPreimage.Fact(0x0101, bookIdentityFields(bookId, identity));
  }

  private static List<AttestationField> bookIdentityFields(UUID bookId, BookIdentity identity) {
    return List.of(
        AttestationPreimageProjectionFields.uuid(bookId),
        AttestationPreimageProjectionFields.text(identity.entityName().value()),
        AttestationPreimageProjectionFields.token(
            identity.bookDoctrine().accountingKernelProfileId().value()),
        AttestationPreimageProjectionFields.token(
            identifierToken(identity.bookDoctrine().accountingBasis().wireValue())),
        AttestationPreimageProjectionFields.token(
            identifierToken(identity.bookDoctrine().accountingFrameworkPosition().wireValue())),
        AttestationPreimageProjectionFields.token(
            identifierToken(identity.bookDoctrine().entityForm().wireValue())),
        AttestationPreimageProjectionFields.token(
            identifierToken(identity.bookDoctrine().bookTemplateId().wireValue())),
        identity.bookDoctrine().inventoryCostingDoctrine() == null
            ? AttestationField.absent()
            : AttestationPreimageProjectionFields.token(
                identifierToken(identity.bookDoctrine().inventoryCostingDoctrine().wireValue())),
        AttestationPreimageProjectionFields.present(
            AttestationTextFieldValue.currency(identity.functionalCurrency().code())),
        AttestationPreimageProjectionFields.present(
            AttestationNumericFieldValue.unsigned8(identity.fiscalYearStart().month())),
        AttestationPreimageProjectionFields.present(
            AttestationNumericFieldValue.unsigned8(identity.fiscalYearStart().day())),
        AttestationPreimageProjectionFields.date(identity.bookStartEffectiveDate()));
  }

  private static AttestationPreimage.Fact founderRequest(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0102,
        List.of(
            AttestationPreimageProjectionFields.uuid(founder.principalId()),
            AttestationPreimageProjectionFields.present(
                AttestationBinaryFieldValue.hash(founder.keyId())),
            AttestationPreimageProjectionFields.present(
                AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            AttestationPreimageProjectionFields.token("operator")));
  }

  private static AttestationPreimage.Fact founderBinding(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0002,
        List.of(
            AttestationPreimageProjectionFields.present(AttestationNumericFieldValue.mutation(0)),
            AttestationPreimageProjectionFields.uuid(founder.principalId()),
            AttestationPreimageProjectionFields.present(
                AttestationBinaryFieldValue.hash(founder.keyId())),
            AttestationPreimageProjectionFields.token("enroll"),
            AttestationPreimageProjectionFields.present(
                AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            AttestationPreimageProjectionFields.token("operator"),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact founderGrantRequest(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0183,
        List.of(
            AttestationPreimageProjectionFields.uuid(founder.principalId()),
            AttestationPreimageProjectionFields.token(capability.token()),
            AttestationPreimageProjectionFields.token("grant")));
  }

  private static AttestationPreimage.Fact founderGrant(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0003,
        List.of(
            AttestationPreimageProjectionFields.present(AttestationNumericFieldValue.mutation(0)),
            AttestationPreimageProjectionFields.uuid(founder.principalId()),
            AttestationPreimageProjectionFields.token(capability.token()),
            AttestationPreimageProjectionFields.token("grant")));
  }

  private static AttestationPreimage.Fact policyRequest(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0103,
        List.of(
            AttestationPreimageProjectionFields.token(capability.token()),
            AttestationPreimageProjectionFields.present(
                AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }

  private static AttestationPreimage.Fact policyEffect(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0005,
        List.of(
            AttestationPreimageProjectionFields.present(AttestationNumericFieldValue.mutation(0)),
            AttestationPreimageProjectionFields.token(capability.token()),
            AttestationPreimageProjectionFields.present(
                AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }

  private static String identifierToken(String wireValue) {
    return Objects.requireNonNull(wireValue, "wireValue")
        .toLowerCase(java.util.Locale.ROOT)
        .replace('_', '-');
  }
}
