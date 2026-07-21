package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Projects the complete direct-posting request and durable posting effect into fixed preimages. */
public final class AttestationPostingMutationProjection {
  private static final int OPERATION_STEP_ORDER = 0;

  private AttestationPostingMutationProjection() {}

  /** Creates immutable preimages for one newly committed direct posting operation. */
  public static AttestationOperationPreimages project(
      AttestationPostingRequestSnapshot request, AttestationPostingEffectSnapshot effect) {
    AttestationPostingRequestSnapshot checkedRequest = Objects.requireNonNull(request, "request");
    AttestationPostingEffectSnapshot checkedEffect = Objects.requireNonNull(effect, "effect");
    if (!checkedRequest.operationKind().equals(checkedEffect.operationKind())) {
      throw new IllegalArgumentException("Posting request and effect must retain operationKind.");
    }
    return new AttestationOperationPreimages(
        requestPreimage(checkedRequest).encoded(),
        effectPreimage(checkedRequest, checkedEffect).encoded());
  }

  private static AttestationPreimage requestPreimage(AttestationPostingRequestSnapshot request) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(command(request));
    facts.add(postingRequest(request));
    request.sourceDocuments().forEach(document -> facts.add(sourceDocumentRequest(document)));
    for (int lineOrder = 0; lineOrder < request.journalLines().size(); lineOrder++) {
      facts.add(journalLineRequest(lineOrder, request.journalLines().get(lineOrder)));
    }
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage effectPreimage(
      AttestationPostingRequestSnapshot request, AttestationPostingEffectSnapshot effect) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(postingEffect(request, effect));
    request
        .sourceDocuments()
        .forEach(document -> facts.add(sourceDocumentEffect(effect.postingId(), document)));
    for (int lineOrder = 0; lineOrder < request.journalLines().size(); lineOrder++) {
      facts.add(
          journalLineEffect(effect.postingId(), lineOrder, request.journalLines().get(lineOrder)));
    }
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact command(AttestationPostingRequestSnapshot request) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationPreimageProjectionFields.token(token(request.operationKind())),
            AttestationPreimageProjectionFields.text(request.idempotencyKey()),
            AttestationPreimageProjectionFields.text(request.causationId()),
            AttestationPreimageProjectionFields.token(token(request.sourceChannel()))));
  }

  private static AttestationPreimage.Fact postingRequest(
      AttestationPostingRequestSnapshot request) {
    return new AttestationPreimage.Fact(
        0x0120,
        List.of(
            AttestationPreimageProjectionFields.unsigned32(OPERATION_STEP_ORDER),
            AttestationPreimageProjectionFields.token(token(request.operationKind())),
            AttestationPreimageProjectionFields.date(request.effectiveDate()),
            AttestationPreimageProjectionFields.token(token(request.postingKind())),
            AttestationPreimageProjectionFields.optionalUuid(request.priorPostingId()),
            AttestationPreimageProjectionFields.optionalText(request.reversalReason())));
  }

  private static AttestationPreimage.Fact sourceDocumentRequest(
      AttestationPostingEvidenceDocument document) {
    return new AttestationPreimage.Fact(
        0x0124,
        List.of(
            AttestationPreimageProjectionFields.unsigned32(OPERATION_STEP_ORDER),
            AttestationPreimageProjectionFields.text(document.sourceDocumentId()),
            AttestationPreimageProjectionFields.text(document.sourceDocumentType()),
            AttestationPreimageProjectionFields.date(document.documentDate())));
  }

  private static AttestationPreimage.Fact journalLineRequest(
      int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x012A,
        List.of(
            AttestationPreimageProjectionFields.unsigned32(OPERATION_STEP_ORDER),
            AttestationPreimageProjectionFields.unsigned32(lineOrder),
            AttestationPreimageProjectionFields.text(line.accountCode()),
            AttestationPreimageProjectionFields.token(token(line.side())),
            AttestationPreimageProjectionFields.money(
                line.currencyCode(), line.minorUnits(), false),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact postingEffect(
      AttestationPostingRequestSnapshot request, AttestationPostingEffectSnapshot effect) {
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(effect.postingId()),
            AttestationPreimageProjectionFields.unsigned32(OPERATION_STEP_ORDER),
            AttestationPreimageProjectionFields.token(token(effect.operationKind())),
            AttestationPreimageProjectionFields.token(token(effect.postingKind())),
            AttestationPreimageProjectionFields.token(token(effect.postingOriginKind())),
            AttestationPreimageProjectionFields.date(request.effectiveDate()),
            AttestationPreimageProjectionFields.instant(effect.recordedAt()),
            AttestationPreimageProjectionFields.optionalUuid(effect.priorPostingId()),
            AttestationPreimageProjectionFields.uuid(effect.commandId()),
            AttestationPreimageProjectionFields.text(request.idempotencyKey()),
            AttestationPreimageProjectionFields.text(request.causationId()),
            AttestationPreimageProjectionFields.token(token(request.sourceChannel()))));
  }

  private static AttestationPreimage.Fact sourceDocumentEffect(
      UUID postingId, AttestationPostingEvidenceDocument document) {
    return new AttestationPreimage.Fact(
        0x0021,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(postingId),
            AttestationPreimageProjectionFields.text(document.sourceDocumentId()),
            AttestationPreimageProjectionFields.text(document.sourceDocumentType()),
            AttestationPreimageProjectionFields.date(document.documentDate())));
  }

  private static AttestationPreimage.Fact journalLineEffect(
      UUID postingId, int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x0025,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(postingId),
            AttestationPreimageProjectionFields.unsigned32(lineOrder),
            AttestationPreimageProjectionFields.text(line.accountCode()),
            AttestationPreimageProjectionFields.token(token(line.side())),
            AttestationPreimageProjectionFields.money(
                line.currencyCode(), line.minorUnits(), false),
            AttestationField.absent()));
  }

  private static String token(String value) {
    return Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
