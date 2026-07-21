package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
            presentToken(token(request.operationKind())),
            presentText(request.idempotencyKey()),
            presentText(request.causationId()),
            presentToken(token(request.sourceChannel()))));
  }

  private static AttestationPreimage.Fact postingRequest(
      AttestationPostingRequestSnapshot request) {
    return new AttestationPreimage.Fact(
        0x0120,
        List.of(
            order(),
            presentToken(token(request.operationKind())),
            present(AttestationTextFieldValue.date(request.effectiveDate())),
            presentToken(token(request.postingKind())),
            optionalUuid(request.priorPostingId()),
            optionalText(request.reversalReason())));
  }

  private static AttestationPreimage.Fact sourceDocumentRequest(
      AttestationPostingEvidenceDocument document) {
    return new AttestationPreimage.Fact(
        0x0124,
        List.of(
            order(),
            presentText(document.sourceDocumentId()),
            presentText(document.sourceDocumentType()),
            present(AttestationTextFieldValue.date(document.documentDate()))));
  }

  private static AttestationPreimage.Fact journalLineRequest(
      int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x012A,
        List.of(
            order(),
            lineOrder(lineOrder),
            presentText(line.accountCode()),
            presentToken(token(line.side())),
            money(line.currencyCode(), line.minorUnits()),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact postingEffect(
      AttestationPostingRequestSnapshot request, AttestationPostingEffectSnapshot effect) {
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            present(
                AttestationNumericFieldValue.mutation(
                    AttestationEffectMutation.CREATE.wireValue())),
            present(AttestationBinaryFieldValue.uuid(effect.postingId())),
            order(),
            presentToken(token(effect.operationKind())),
            presentToken(token(effect.postingKind())),
            presentToken(token(effect.postingOriginKind())),
            present(AttestationTextFieldValue.date(request.effectiveDate())),
            present(AttestationTextFieldValue.instant(effect.recordedAt())),
            optionalUuid(effect.priorPostingId()),
            present(AttestationBinaryFieldValue.uuid(effect.commandId())),
            presentText(request.idempotencyKey()),
            presentText(request.causationId()),
            presentToken(token(request.sourceChannel()))));
  }

  private static AttestationPreimage.Fact sourceDocumentEffect(
      UUID postingId, AttestationPostingEvidenceDocument document) {
    return new AttestationPreimage.Fact(
        0x0021,
        List.of(
            present(
                AttestationNumericFieldValue.mutation(
                    AttestationEffectMutation.CREATE.wireValue())),
            present(AttestationBinaryFieldValue.uuid(postingId)),
            presentText(document.sourceDocumentId()),
            presentText(document.sourceDocumentType()),
            present(AttestationTextFieldValue.date(document.documentDate()))));
  }

  private static AttestationPreimage.Fact journalLineEffect(
      UUID postingId, int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x0025,
        List.of(
            present(
                AttestationNumericFieldValue.mutation(
                    AttestationEffectMutation.CREATE.wireValue())),
            present(AttestationBinaryFieldValue.uuid(postingId)),
            lineOrder(lineOrder),
            presentText(line.accountCode()),
            presentToken(token(line.side())),
            money(line.currencyCode(), line.minorUnits()),
            AttestationField.absent()));
  }

  private static AttestationField order() {
    return present(
        AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(OPERATION_STEP_ORDER)));
  }

  private static AttestationField lineOrder(int lineOrder) {
    if (lineOrder < 0) {
      throw new IllegalArgumentException("lineOrder must not be negative.");
    }
    return present(AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(lineOrder)));
  }

  private static AttestationField money(String currencyCode, long minorUnits) {
    return present(
        AttestationNumericFieldValue.money(currencyCode, false, BigInteger.valueOf(minorUnits)));
  }

  private static AttestationField optionalUuid(@Nullable String uuidText) {
    return Optional.ofNullable(uuidText)
        .<AttestationField>map(
            value -> present(AttestationBinaryFieldValue.uuid(UUID.fromString(value))))
        .orElseGet(AttestationField::absent);
  }

  private static AttestationField optionalUuid(@Nullable UUID value) {
    return Optional.ofNullable(value)
        .<AttestationField>map(uuid -> present(AttestationBinaryFieldValue.uuid(uuid)))
        .orElseGet(AttestationField::absent);
  }

  private static AttestationField optionalText(@Nullable String value) {
    return Optional.ofNullable(value)
        .<AttestationField>map(AttestationPostingMutationProjection::presentText)
        .orElseGet(AttestationField::absent);
  }

  private static String token(String value) {
    return Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField presentText(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  private static AttestationField presentToken(String value) {
    return present(AttestationTextFieldValue.token(value));
  }
}
