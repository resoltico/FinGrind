package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.absent;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.currency;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.date;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.money;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.mutation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.text;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.token;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u32;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u64;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.uuid;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.command;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.commandId;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.journalLine;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.postingFact;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.postingId;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.preimage;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.recordedAt;

import dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.Signer;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Builds signed book operations and the deterministic system-derived close operations. */
final class AttestationCorpusOperationBuilder {
  private AttestationCorpusOperationBuilder() {}

  static AttestationBookOperation systemClose(
      List<AttestationBookOperation> operations,
      AttestationOperationKind kind,
      UUID workflowId,
      LocalDate effectiveTo,
      List<Signer> signers) {
    return kind == AttestationOperationKind.FISCAL_YEAR_CLOSE
        ? fiscalClose(operations, workflowId, effectiveTo, signers)
        : interimClose(operations, workflowId, effectiveTo, signers);
  }

  static AttestationBookOperation operation(
      List<AttestationBookOperation> operations,
      AttestationOperationKind kind,
      AttestationPreimage request,
      AttestationPreimage effect,
      List<Signer> signers) {
    BigInteger order = BigInteger.valueOf(operations.size());
    AttestationHash previousHead = operations.getLast().envelope().head();
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationCorpusFixtures.BOOK_ID,
            order,
            kind.wireToken(),
            previousHead,
            recordedAt(operations.size()),
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return decodedOperation(payload, request, effect, signers);
  }

  static AttestationBookOperation decodedOperation(
      AttestationOperationPayload payload,
      AttestationPreimage request,
      AttestationPreimage effect,
      List<Signer> signers) {
    AttestationEnvelope<AttestationOperationPayload> envelope = envelope(payload, signers);
    return AttestationBookOperation.decode(envelope.encoded(), request.encoded(), effect.encoded());
  }

  private static AttestationBookOperation interimClose(
      List<AttestationBookOperation> operations,
      UUID workflowId,
      LocalDate effectiveTo,
      List<Signer> signers) {
    UUID postingId = postingId(3);
    AttestationPreimage request =
        closeRequest(
            AttestationOperationKind.INTERIM_RESULT_SWEEP,
            workflowId,
            effectiveTo,
            "interim-result-sweep",
            closeIdempotencyKey(AttestationOperationKind.INTERIM_RESULT_SWEEP, operations),
            absent(),
            absent());
    AttestationPreimage effect =
        preimage(
            postingFact(
                postingId,
                commandId(3),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                effectiveTo,
                recordedAt(operations.size()),
                closeIdempotencyKey(AttestationOperationKind.INTERIM_RESULT_SWEEP, operations),
                AttestationSourceChannel.SYSTEM),
            journalLine(postingId, 0, "4000", "debit"),
            journalLine(postingId, 1, "3000", "credit"),
            new AttestationPreimage.Fact(
                0x0040,
                List.of(
                    mutation(),
                    u64(1),
                    date(LocalDate.of(2026, 1, 1)),
                    date(effectiveTo),
                    text("3000"))),
            new AttestationPreimage.Fact(
                0x0041, List.of(mutation(), u64(1), currency(), money(10_000))),
            new AttestationPreimage.Fact(0x0042, List.of(mutation(), u64(1), uuid(postingId))));
    return operation(
        operations, AttestationOperationKind.INTERIM_RESULT_SWEEP, request, effect, signers);
  }

  private static AttestationBookOperation fiscalClose(
      List<AttestationBookOperation> operations,
      UUID workflowId,
      LocalDate effectiveTo,
      List<Signer> signers) {
    UUID postingId = postingId(5);
    AttestationPreimage request =
        closeRequest(
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            workflowId,
            effectiveTo,
            "fiscal-year-close",
            closeIdempotencyKey(AttestationOperationKind.FISCAL_YEAR_CLOSE, operations),
            u32(2026),
            text("3100"));
    AttestationPreimage effect =
        preimage(
            postingFact(
                postingId,
                commandId(5),
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                effectiveTo,
                recordedAt(operations.size()),
                closeIdempotencyKey(AttestationOperationKind.FISCAL_YEAR_CLOSE, operations),
                AttestationSourceChannel.SYSTEM),
            journalLine(postingId, 0, "3000", "debit"),
            journalLine(postingId, 1, "3200", "credit"),
            new AttestationPreimage.Fact(
                0x0043,
                List.of(
                    mutation(),
                    u64(1),
                    date(LocalDate.of(2026, 1, 1)),
                    date(effectiveTo),
                    text("3100"),
                    text("3000"),
                    text("3200"))),
            new AttestationPreimage.Fact(0x0044, List.of(mutation(), u64(1), uuid(postingId))));
    return operation(
        operations, AttestationOperationKind.FISCAL_YEAR_CLOSE, request, effect, signers);
  }

  private static AttestationPreimage closeRequest(
      AttestationOperationKind kind,
      UUID workflowId,
      LocalDate effectiveTo,
      String workflowKind,
      String idempotencyKey,
      AttestationField fiscalYear,
      AttestationField capitalAccount) {
    return preimage(
        command(kind, AttestationSourceChannel.SYSTEM, idempotencyKey),
        new AttestationPreimage.Fact(
            0x0120,
            List.of(
                u32(0),
                token(kind.wireToken()),
                date(effectiveTo),
                token("period-close"),
                absent(),
                absent())),
        new AttestationPreimage.Fact(
            0x0140,
            List.of(
                token(workflowKind),
                date(LocalDate.of(2026, 1, 1)),
                date(effectiveTo),
                fiscalYear,
                text("3000"),
                capitalAccount,
                "fiscal-year-close".equals(workflowKind) ? text("3200") : absent())),
        new AttestationPreimage.Fact(0x0141, List.of(uuid(workflowId))));
  }

  private static String closeIdempotencyKey(
      AttestationOperationKind kind, List<AttestationBookOperation> operations) {
    return "fixture-" + kind.wireToken() + "-" + operations.size();
  }

  static <P extends AttestationPayload> AttestationEnvelope<P> envelope(
      P payload, List<Signer> signers) {
    return AttestationEnvelope.of(
        payload,
        signers.stream()
            .map(
                signer ->
                    new AttestationSignatureEntry(
                        signer.principalId(),
                        signer.keyId(),
                        AttestationEd25519.sign(signer.keyPair().getPrivate(), payload.encoded())))
            .toList());
  }
}
