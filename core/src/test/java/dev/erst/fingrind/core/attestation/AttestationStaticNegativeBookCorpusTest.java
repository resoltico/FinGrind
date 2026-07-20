package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.date;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.spki;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.token;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u16;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u32;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.uuid;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.bindingEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.bindingRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.preimage;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.decodedOperation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.operation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.Signer;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Executes every complete-book Slice 4 negative from the exact mutated raw resource. */
class AttestationStaticNegativeBookCorpusTest {
  @Test
  void executesGenesisRowsN12aAndN12bFromMutatedB01Resources() {
    AttestationCorpusResources.Book base = AttestationCorpusFixtures.b01();
    AttestationBookOperation genesis = base.operations().getFirst();

    AttestationPreimage mismatchedEffect =
        replaceFactField(genesis.effectPreimage(), 0x0002, 4, spki(AttestationCorpusFixtures.B));
    AttestationBookOperation n12aGenesis =
        genesis(genesis.requestPreimage(), mismatchedEffect, List.of(AttestationCorpusFixtures.A));
    assertNegative(
        fixture(
            "N-12a",
            base,
            book("N-12a", List.of(n12aGenesis)),
            "B-01 founder A declares B's SPKI while retaining A's keyId",
            AttestationStaticCorpus.VerificationScope.GENESIS,
            AttestationAuthorizationFailure.GENESIS_INVALID));

    AttestationBookOperation n12bGenesis =
        AttestationBookOperation.decode(
            AttestationEnvelope.of(genesis.envelope().payload(), List.of()).encoded(),
            genesis.requestPreimage().encoded(),
            genesis.effectPreimage().encoded());
    assertNegative(
        fixture(
            "N-12b",
            base,
            book("N-12b", List.of(n12bGenesis)),
            "B-01 genesis with its sole signature entry removed",
            AttestationStaticCorpus.VerificationScope.GENESIS,
            AttestationAuthorizationFailure.GENESIS_INVALID));
  }

  @Test
  void executesN16ThroughN20FromMutatedCompleteBookResources() {
    AttestationCorpusResources.Book b04 = AttestationCorpusFixtures.b04();
    AttestationBookOperation enrollment = b04.operations().get(4);
    assertNegative(
        fixture(
            "N-16",
            b04,
            rewrite(
                b04,
                4,
                replaceFactField(enrollment.requestPreimage(), 0x0180, 4, token("operator")),
                replaceFactField(enrollment.effectPreimage(), 0x0002, 5, token("operator"))),
            "B-04 CLOSE_PERIOD M=1 with C changed from system to operator purpose",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.CAPABILITY_INVALID));

    AttestationCorpusResources.Book b02 = AttestationCorpusFixtures.b02();
    AttestationBookOperation commonPosting = b02.operations().get(3);
    AttestationPreimage fixedAssetEffect =
        append(commonPosting.effectPreimage(), fixedAssetEffect());
    assertNegative(
        fixture(
            "N-17",
            b02,
            rewrite(b02, 3, commonPosting.requestPreimage(), fixedAssetEffect),
            "B-02 POST M=2 with a fixed-asset effect but no matching request fact",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));

    List<AttestationBookOperation> n18Operations = new ArrayList<>(b02.operations());
    n18Operations.add(
        AttestationCorpusFixtures.bindingOperation(
            n18Operations,
            AttestationOperationKind.ENROLL_KEY,
            AttestationCorpusFixtures.C,
            "enroll",
            "operator",
            null,
            List.of(AttestationCorpusFixtures.A, AttestationCorpusFixtures.B)));
    n18Operations.add(
        AttestationCorpusFixtures.sale(
            n18Operations,
            "fixture-n-18-sale-5",
            "fixture-n-18-receipt-5",
            LocalDate.of(2026, 12, 31),
            AttestationCorpusFixtureValues.postingId(9),
            AttestationCorpusFixtureValues.commandId(9),
            List.of(AttestationCorpusFixtures.B, AttestationCorpusFixtures.C)));
    assertNegative(
        fixture(
            "N-18",
            b02,
            book("N-18", n18Operations),
            "B-02 POST M=2 with active operator C but no POST grant",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.CAPABILITY_INVALID));

    AttestationBookOperation interimClose = b04.operations().get(9);
    LocalDate invalidCloseDate = LocalDate.of(2026, 12, 29);
    AttestationPreimage n19Request =
        replaceFactField(
            replaceFactField(interimClose.requestPreimage(), 0x0120, 2, date(invalidCloseDate)),
            0x0140,
            2,
            date(invalidCloseDate));
    AttestationPreimage n19Effect =
        replaceFactField(interimClose.effectPreimage(), 0x0020, 6, date(invalidCloseDate));
    assertNegative(
        fixture(
            "N-19",
            b04,
            rewrite(b04, 9, n19Request, n19Effect),
            "B-04 interim close with a consistently signed date outside its workflow derivation",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID));

    AttestationBookOperation policy = b04.operations().get(5);
    assertNegative(
        fixture(
            "N-20",
            b04,
            rewrite(
                b04,
                5,
                replaceFactField(policy.requestPreimage(), 0x0182, 1, u16(2)),
                replaceFactField(policy.effectPreimage(), 0x0005, 2, u16(2))),
            "B-04 CLOSE_PERIOD M=2 with C remaining the only system-purpose principal",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.CAPABILITY_INVALID));
  }

  @Test
  void executesRegistryRowsN21ThroughN27FromMutatedCompleteBookResources() {
    AttestationCorpusResources.Book b03 = AttestationCorpusFixtures.b03();
    Signer mismatchedA2 =
        new Signer(
            AttestationCorpusFixtures.A.principalId(),
            AttestationCorpusFixtures.A2.keyPair(),
            AttestationHash.sha256(new byte[] {9}));
    assertNegative(
        fixture(
            "N-21",
            b03,
            rewrite(
                b03,
                6,
                bindingPreimage(
                    mismatchedA2, "rollover", AttestationCorpusFixtures.A.keyId(), true),
                bindingPreimage(
                    mismatchedA2, "rollover", AttestationCorpusFixtures.A.keyId(), false)),
            "B-03 rollover with an A2 keyId that does not match its declared SPKI",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
    assertNegative(
        fixture(
            "N-22",
            b03,
            rewrite(
                b03,
                6,
                bindingPreimage(AttestationCorpusFixtures.A2, "rollover", null, true),
                bindingPreimage(AttestationCorpusFixtures.A2, "rollover", null, false)),
            "B-03 rollover without its required predecessor key",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
    assertNegative(
        fixture(
            "N-23",
            b03,
            rewrite(
                b03,
                6,
                bindingPreimage(
                    AttestationCorpusFixtures.A2,
                    "rollover",
                    AttestationCorpusFixtures.B.keyId(),
                    true),
                bindingPreimage(
                    AttestationCorpusFixtures.A2,
                    "rollover",
                    AttestationCorpusFixtures.B.keyId(),
                    false)),
            "B-03 rollover that names B instead of A as predecessor",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
    assertNegative(
        fixture(
            "N-26",
            b03,
            rewrite(
                b03,
                6,
                bindingPreimage(
                    AttestationCorpusFixtures.A2,
                    "rollover",
                    AttestationCorpusFixtures.A2.keyId(),
                    true),
                bindingPreimage(
                    AttestationCorpusFixtures.A2,
                    "rollover",
                    AttestationCorpusFixtures.A2.keyId(),
                    false)),
            "B-03 rollover that names its new A2 key as predecessor",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));

    AttestationCorpusResources.Book b02 = AttestationCorpusFixtures.b02();
    Signer cWithBKey =
        new Signer(
            AttestationCorpusFixtures.C.principalId(),
            AttestationCorpusFixtures.B.keyPair(),
            AttestationCorpusFixtures.B.keyId());
    assertNegative(
        fixture(
            "N-24",
            b02,
            appendBinding(b02, cWithBKey, "enroll", null, AttestationOperationKind.ENROLL_KEY),
            "B-02 enrollment that reuses B's already bound key for C",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
    assertNegative(
        fixture(
            "N-25",
            b02,
            appendBinding(
                b02,
                AttestationCorpusFixtures.C,
                "enroll",
                AttestationCorpusFixtures.A.keyId(),
                AttestationOperationKind.ENROLL_KEY),
            "B-02 enrollment that incorrectly supplies an A predecessor",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));

    List<AttestationBookOperation> n27Operations = new ArrayList<>(b02.operations());
    n27Operations.add(
        AttestationCorpusFixtures.revoke(
            n27Operations,
            AttestationCorpusFixtures.C,
            List.of(AttestationCorpusFixtures.A, AttestationCorpusFixtures.B)));
    assertNegative(
        fixture(
            "N-27",
            b02,
            book("N-27", n27Operations),
            "B-02 revocation of C before C has any binding",
            AttestationStaticCorpus.VerificationScope.BOOK,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
  }

  private static AttestationStaticCorpus.Fixture fixture(
      String id,
      AttestationCorpusResources.Book base,
      AttestationCorpusResources.Book target,
      String policyFold,
      AttestationStaticCorpus.VerificationScope scope,
      AttestationAuthorizationFailure expectedFailure) {
    byte[] baseSource = base.encoded();
    byte[] targetSource = target.encoded();
    AttestationStaticCorpus.Mutation mutation =
        AttestationStaticCorpus.Mutation.between(baseSource, targetSource);
    AttestationStaticCorpus.Fixture fixture =
        AttestationStaticCorpus.fixture(
            id,
            baseSource,
            mutation,
            new AttestationStaticCorpus.PolicyFold(policyFold),
            scope,
            expectedFailure);
    assertTrue(mutation.leaves(baseSource, targetSource), id);
    assertFalse(java.util.Arrays.equals(baseSource, fixture.source()), id);
    return fixture;
  }

  private static void assertNegative(AttestationStaticCorpus.Fixture fixture) {
    assertFailure(
        fixture.expectedFirstFailure(),
        () ->
            AttestationBookVerifier.verify(
                AttestationCorpusFixtures.decodeBook(fixture.source()).decode()));
  }

  private static AttestationBookOperation genesis(
      AttestationPreimage request, AttestationPreimage effect, List<Signer> signers) {
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationCorpusFixtures.BOOK_ID,
            BigInteger.ZERO,
            AttestationOperationKind.BOOK_GENESIS.wireToken(),
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            AttestationCorpusFixtures.GENESIS_RECORDED_AT,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return decodedOperation(payload, request, effect, signers);
  }

  private static AttestationCorpusResources.Book rewrite(
      AttestationCorpusResources.Book base,
      int changedIndex,
      AttestationPreimage changedRequest,
      AttestationPreimage changedEffect) {
    List<AttestationBookOperation> source = base.operations();
    List<AttestationBookOperation> rewritten = new ArrayList<>(source.size());
    rewritten.add(source.getFirst());
    for (int index = 1; index < source.size(); index++) {
      AttestationBookOperation original = source.get(index);
      rewritten.add(
          operation(
              rewritten,
              AttestationOperationKind.forWireToken(original.envelope().payload().operationKind()),
              index == changedIndex ? changedRequest : original.requestPreimage(),
              index == changedIndex ? changedEffect : original.effectPreimage(),
              signers(original)));
    }
    return book("rewritten-corpus-book", rewritten);
  }

  private static AttestationCorpusResources.Book appendBinding(
      AttestationCorpusResources.Book base,
      Signer subject,
      String action,
      @Nullable AttestationHash predecessor,
      AttestationOperationKind kind) {
    List<AttestationBookOperation> operations = new ArrayList<>(base.operations());
    operations.add(
        AttestationCorpusFixtures.bindingOperation(
            operations,
            kind,
            subject,
            action,
            "operator",
            predecessor,
            List.of(AttestationCorpusFixtures.A, AttestationCorpusFixtures.B)));
    return book("appended-binding-corpus-book", operations);
  }

  private static List<Signer> signers(AttestationBookOperation operation) {
    return operation.envelope().authorizationEnvelope().entries().stream()
        .map(
            entry ->
                List.of(
                        AttestationCorpusFixtures.A,
                        AttestationCorpusFixtures.B,
                        AttestationCorpusFixtures.C,
                        AttestationCorpusFixtures.A2)
                    .stream()
                    .filter(
                        signer ->
                            signer.principalId().equals(entry.principalId())
                                && signer.keyId().equals(entry.keyId()))
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalArgumentException("Corpus signer is not declared.")))
        .toList();
  }

  private static AttestationPreimage bindingPreimage(
      Signer signer, String action, @Nullable AttestationHash predecessor, boolean request) {
    return request
        ? preimage(bindingRequest(signer, action, "operator", predecessor))
        : preimage(bindingEffect(signer, action, "operator", predecessor));
  }

  private static AttestationPreimage replaceFactField(
      AttestationPreimage source, int tag, int fieldIndex, AttestationField replacement) {
    boolean[] replaced = {false};
    List<AttestationPreimage.Fact> records =
        source.records().stream()
            .map(
                record -> {
                  if (record.recordTypeTag() != tag || replaced[0]) {
                    return record;
                  }
                  List<AttestationField> fields = new ArrayList<>(record.fields());
                  fields.set(fieldIndex, replacement);
                  replaced[0] = true;
                  return new AttestationPreimage.Fact(record.recordTypeTag(), fields);
                })
            .toList();
    if (!replaced[0]) {
      throw new IllegalArgumentException("Corpus record tag is absent from its declared base.");
    }
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage append(
      AttestationPreimage source, AttestationPreimage.Fact record) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(source.records());
    records.add(record);
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage.Fact fixedAssetEffect() {
    return new AttestationPreimage.Fact(
        0x0060,
        List.of(
            AttestationCorpusFixtureFields.mutation(),
            uuid(UUID.fromString("50000000-0000-7000-8000-000000000001")),
            uuid(AttestationCorpusFixtureValues.postingId(1)),
            AttestationCorpusFixtureFields.text("1600"),
            AttestationCorpusFixtureFields.text("1609"),
            AttestationCorpusFixtureFields.text("6800"),
            AttestationCorpusFixtureFields.text("7700"),
            AttestationCorpusFixtureFields.text("7800"),
            token("equipment"),
            AttestationCorpusFixtureFields.money(10_000),
            date(LocalDate.of(2026, 7, 17)),
            u32(60)));
  }

  private static AttestationCorpusResources.Book book(
      String id, List<AttestationBookOperation> operations) {
    return AttestationCorpusResources.book(id, operations);
  }
}
