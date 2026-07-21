package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.BookIdentity;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Builds the self-authorizing, unanimously signed genesis operation for one new protected book. */
public final class AttestationGenesis {
  private static final byte[] ZERO_HEAD = new byte[AttestationHash.BYTE_LENGTH];

  private AttestationGenesis() {}

  /**
   * Creates the exact genesis evidence for one new book.
   *
   * <p>The supplied list must contain one through five distinct principal-bound credentials. The
   * credentials establish every initial capability grant and the mandatory default policy.
   */
  public static AttestationEvidence create(
      UUID bookId,
      BookIdentity bookIdentity,
      Instant recordedAt,
      List<AttestationSigningCredential> founders) {
    UUID checkedBookId = Objects.requireNonNull(bookId, "bookId");
    BookIdentity checkedBookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    List<AttestationSigningCredential> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    if (checkedFounders.isEmpty() || checkedFounders.size() > 5) {
      throw new IllegalArgumentException("Genesis requires between one and five founders.");
    }
    List<AttestationFounder> founderFacts =
        checkedFounders.stream().map(AttestationGenesis::founder).toList();
    requireDistinctFounderCredentials(founderFacts);
    AttestationPreimage request = requestPreimage(checkedBookId, checkedBookIdentity, founderFacts);
    AttestationPreimage effect = effectPreimage(checkedBookId, checkedBookIdentity, founderFacts);
    return AttestationOperationSigner.sign(
        checkedBookId,
        BigInteger.ZERO,
        AttestationOperationKind.BOOK_GENESIS.wireToken(),
        ZERO_HEAD,
        checkedRecordedAt,
        request.encoded(),
        effect.encoded(),
        checkedFounders);
  }

  private static AttestationFounder founder(AttestationSigningCredential credential) {
    AttestationSigningCredential checkedCredential =
        Objects.requireNonNull(credential, "founders must not contain null");
    AttestationPublicCredential publicCredential = checkedCredential.publicCredential();
    return new AttestationFounder(
        checkedCredential.principalId(),
        AttestationHash.of(publicCredential.keyId()),
        AttestationSpki.of(publicCredential.spki()));
  }

  private static void requireDistinctFounderCredentials(List<AttestationFounder> founders) {
    long principalCount = founders.stream().map(AttestationFounder::principalId).distinct().count();
    long keyCount = founders.stream().map(AttestationFounder::keyId).distinct().count();
    if (principalCount != founders.size() || keyCount != founders.size()) {
      throw new IllegalArgumentException("Genesis founders must have distinct principals and keys.");
    }
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
            presentToken(AttestationOperationKind.BOOK_GENESIS.wireToken()),
            AttestationField.absent(),
            AttestationField.absent(),
            presentToken(AttestationSourceChannel.CLI.wireToken())));
  }

  private static AttestationPreimage.Fact bookIdentityRequest(UUID bookId, BookIdentity identity) {
    return new AttestationPreimage.Fact(0x0101, bookIdentityFields(bookId, identity));
  }

  private static AttestationPreimage.Fact bookIdentityEffect(UUID bookId, BookIdentity identity) {
    List<AttestationField> fields = new ArrayList<>();
    fields.add(present(AttestationNumericFieldValue.mutation(0)));
    fields.addAll(bookIdentityFields(bookId, identity));
    return new AttestationPreimage.Fact(0x0001, fields);
  }

  private static List<AttestationField> bookIdentityFields(UUID bookId, BookIdentity identity) {
    return List.of(
        present(AttestationBinaryFieldValue.uuid(bookId)),
        presentText(identity.entityName().value()),
        presentToken(identity.bookDoctrine().accountingKernelProfileId().value()),
        presentToken(identifierToken(identity.bookDoctrine().accountingBasis().wireValue())),
        presentToken(
            identifierToken(identity.bookDoctrine().accountingFrameworkPosition().wireValue())),
        presentToken(identifierToken(identity.bookDoctrine().entityForm().wireValue())),
        presentToken(identifierToken(identity.bookDoctrine().bookTemplateId().wireValue())),
        identity.bookDoctrine().inventoryCostingDoctrine() == null
            ? AttestationField.absent()
            : presentToken(
                identifierToken(identity.bookDoctrine().inventoryCostingDoctrine().wireValue())),
        present(AttestationTextFieldValue.currency(identity.functionalCurrency().code())),
        present(AttestationNumericFieldValue.unsigned8(identity.fiscalYearStart().month())),
        present(AttestationNumericFieldValue.unsigned8(identity.fiscalYearStart().day())),
        present(
            AttestationTextFieldValue.date(
                identity.bookStartEffectiveDate())));
  }

  private static AttestationPreimage.Fact founderRequest(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0102,
        List.of(
            present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            present(AttestationBinaryFieldValue.hash(founder.keyId())),
            present(AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            presentToken("operator")));
  }

  private static AttestationPreimage.Fact founderBinding(AttestationFounder founder) {
    return new AttestationPreimage.Fact(
        0x0002,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            present(AttestationBinaryFieldValue.hash(founder.keyId())),
            presentToken("enroll"),
            present(AttestationBinaryFieldValue.spki(founder.spki().bytes())),
            presentToken("operator"),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact founderGrantRequest(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0183,
        List.of(
            present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            presentToken(capability.token()),
            presentToken("grant")));
  }

  private static AttestationPreimage.Fact founderGrant(
      AttestationFounder founder, AttestationCapability capability) {
    return new AttestationPreimage.Fact(
        0x0003,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            present(AttestationBinaryFieldValue.uuid(founder.principalId())),
            presentToken(capability.token()),
            presentToken("grant")));
  }

  private static AttestationPreimage.Fact policyRequest(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0103,
        List.of(
            presentToken(capability.token()),
            present(AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }

  private static AttestationPreimage.Fact policyEffect(
      AttestationCapability capability, int founderCount) {
    return new AttestationPreimage.Fact(
        0x0005,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            presentToken(capability.token()),
            present(AttestationNumericFieldValue.unsigned16(capability.genesisQuorum(founderCount)))));
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField presentToken(String value) {
    return present(AttestationTextFieldValue.token(value));
  }

  private static AttestationField presentText(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  private static String identifierToken(String wireValue) {
    return Objects.requireNonNull(wireValue, "wireValue")
        .toLowerCase(java.util.Locale.ROOT)
        .replace('_', '-');
  }
}
