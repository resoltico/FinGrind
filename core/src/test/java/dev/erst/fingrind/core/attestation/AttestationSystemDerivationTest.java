package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Pins the deterministic system-close date derivation after the signer checks have succeeded. */
class AttestationSystemDerivationTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-12-31T03:00:00.009Z");

  @Test
  void acceptsTheCalendarDayBeforeTheSignedRecordedAtInstant() {
    CloseEvidence evidence = evidence(LocalDate.of(2026, 12, 30));

    assertDoesNotThrow(
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                evidence.request(),
                evidence.effect()));
  }

  @Test
  void rejectsAConsistentlySignedButNotWorkflowDerivedCloseDate() {
    CloseEvidence evidence = evidence(LocalDate.of(2026, 12, 29));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                evidence.request(),
                evidence.effect()));
  }

  @Test
  void rejectsACloseWhoseRequestedAccountDoesNotMatchTheSelectedWorkflow() {
    CloseEvidence evidence = evidence(LocalDate.of(2026, 12, 30), "3999");

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                evidence.request(),
                evidence.effect()));
  }

  @Test
  void validatesFiscalCloseAccountBindingsAndRejectsAnAlteredRetainedAccount() {
    CloseEvidence valid =
        evidence(
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            LocalDate.of(2026, 12, 31),
            "3000",
            "3100",
            "3200");
    CloseEvidence altered =
        evidence(
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            LocalDate.of(2026, 12, 31),
            "3000",
            "3100",
            "3299");
    CloseEvidence alteredCapital =
        evidence(
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            LocalDate.of(2026, 12, 31),
            "3000",
            "3199",
            "3200");

    assertDoesNotThrow(
        () ->
            AttestationSystemDerivation.requireValid(
                registry(AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE),
                valid.payload(),
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                valid.provenance(),
                valid.request(),
                valid.effect()));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE),
                altered.payload(),
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                altered.provenance(),
                altered.request(),
                altered.effect()));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE),
                alteredCapital.payload(),
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                alteredCapital.provenance(),
                alteredCapital.request(),
                alteredCapital.effect()));
  }

  @Test
  void excludesCliOperationsAndRejectsAbsentOrWrongSystemWorkflows() {
    AttestationPreimage cliRequest =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null);
    AttestationOperationPayload cliPayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.POST_ENTRY, cliRequest);
    AttestationVerifiedOperationProvenance cliProvenance =
        AttestationVerifiedOperationProvenance.verify(cliPayload, cliRequest);
    CloseEvidence evidence = evidence(LocalDate.of(2026, 12, 30));
    AttestationPreimage systemPostRequest =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.POST_ENTRY,
            AttestationSourceChannel.SYSTEM,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID);
    AttestationOperationPayload systemPostPayload =
        AttestationAuthorizationTestSupport.operationPayload(
            BigInteger.ONE, AttestationOperationKind.POST_ENTRY, systemPostRequest);

    assertDoesNotThrow(
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                cliPayload,
                AttestationOperationKind.POST_ENTRY,
                cliProvenance,
                cliRequest,
                AttestationPreimage.of(List.of())));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                AttestationRegistry.fromVerifierFacts(
                    List.of(), List.of(), List.of(), List.of(), List.of()),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                evidence.request(),
                evidence.effect()));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                systemPostPayload,
                AttestationOperationKind.POST_ENTRY,
                AttestationVerifiedOperationProvenance.verify(systemPostPayload, systemPostRequest),
                systemPostRequest,
                AttestationPreimage.of(List.of())));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                AttestationPreimage.of(List.of()),
                evidence.effect()));
  }

  @Test
  void rejectsAnInterimCloseThatPretendsToHaveAFiscalYearNumber() {
    CloseEvidence evidence = evidence(LocalDate.of(2026, 12, 30));
    AttestationPreimage requestWithFiscalYear =
        replacePeriodCloseField(
            evidence.request(),
            3,
            AttestationField.present(
                AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(2026))));
    AttestationPreimage requestWithCapitalAccount =
        replacePeriodCloseField(
            evidence.request(),
            5,
            AttestationField.present(AttestationTextFieldValue.text("3100")));
    AttestationPreimage requestWithRetainedResultAccount =
        replacePeriodCloseField(
            evidence.request(),
            6,
            AttestationField.present(AttestationTextFieldValue.text("3200")));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                requestWithFiscalYear,
                evidence.effect()));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                requestWithCapitalAccount,
                evidence.effect()));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationSystemDerivation.requireValid(
                registry(),
                evidence.payload(),
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                evidence.provenance(),
                requestWithRetainedResultAccount,
                evidence.effect()));
  }

  private static CloseEvidence evidence(LocalDate effectiveTo) {
    return evidence(effectiveTo, "3000");
  }

  private static CloseEvidence evidence(LocalDate effectiveTo, String resultHoldingAccountCode) {
    return evidence(
        AttestationOperationKind.INTERIM_RESULT_SWEEP,
        effectiveTo,
        resultHoldingAccountCode,
        null,
        null);
  }

  private static CloseEvidence evidence(
      AttestationOperationKind operationKind,
      LocalDate effectiveTo,
      String resultHoldingAccountCode,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode) {
    UUID workflowId = UUID.fromString("99887766-5544-3322-1100-ffeeddccbbaa");
    UUID postingId = UUID.fromString("00112233-4455-6677-8899-aabbccddee03");
    UUID commandId = UUID.fromString("00112233-4455-6677-8899-aabbccddee04");
    AttestationPreimage request =
        AttestationPreimage.of(
            List.of(
                command(operationKind),
                new AttestationPreimage.Fact(
                    0x0120,
                    List.of(
                        present(AttestationNumericFieldValue.unsigned32(BigInteger.ZERO)),
                        present(token(operationKind.wireToken())),
                        present(AttestationTextFieldValue.date(effectiveTo)),
                        present(token("period-close")),
                        AttestationField.absent(),
                        AttestationField.absent())),
                new AttestationPreimage.Fact(
                    0x0140,
                    List.of(
                        present(token(operationKind.wireToken())),
                        present(AttestationTextFieldValue.date(LocalDate.of(2026, 1, 1))),
                        present(AttestationTextFieldValue.date(effectiveTo)),
                        AttestationField.absent(),
                        present(AttestationTextFieldValue.text(resultHoldingAccountCode)),
                        optionalText(capitalAccountCode),
                        optionalText(retainedResultAccountCode))),
                new AttestationPreimage.Fact(
                    0x0141, List.of(present(AttestationBinaryFieldValue.uuid(workflowId))))));
    AttestationPreimage effect =
        AttestationPreimage.of(
            List.of(
                new AttestationPreimage.Fact(
                    0x0020,
                    List.of(
                        present(AttestationNumericFieldValue.mutation(0)),
                        present(AttestationBinaryFieldValue.uuid(postingId)),
                        present(AttestationNumericFieldValue.unsigned32(BigInteger.ZERO)),
                        present(token(operationKind.wireToken())),
                        present(token("period-close")),
                        present(token(operationKind.wireToken())),
                        present(AttestationTextFieldValue.date(effectiveTo)),
                        present(AttestationTextFieldValue.instant(RECORDED_AT)),
                        AttestationField.absent(),
                        present(AttestationBinaryFieldValue.uuid(commandId)),
                        AttestationField.absent(),
                        AttestationField.absent(),
                        present(token("system"))))));
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            operationKind.wireToken(),
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            RECORDED_AT,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return new CloseEvidence(
        payload, AttestationVerifiedOperationProvenance.verify(payload, request), request, effect);
  }

  private static AttestationRegistry registry() {
    return registry(AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP);
  }

  private static AttestationRegistry registry(AttestationSystemWorkflowKind workflowKind) {
    return AttestationRegistry.fromVerifierFacts(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                UUID.fromString("99887766-5544-3322-1100-ffeeddccbbaa"),
                workflowKind,
                "3000",
                workflowKind == AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE ? "3100" : null,
                workflowKind == AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE ? "3200" : null,
                true)));
  }

  private static AttestationPreimage.Fact command(AttestationOperationKind operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            present(token(operationKind.wireToken())),
            AttestationField.absent(),
            AttestationField.absent(),
            present(token("system"))));
  }

  private static AttestationPreimage replacePeriodCloseField(
      AttestationPreimage request, int fieldIndex, AttestationField replacement) {
    return AttestationPreimage.of(
        request.records().stream()
            .map(
                fact -> {
                  if (fact.recordTypeTag() != 0x0140) {
                    return fact;
                  }
                  List<AttestationField> fields = new java.util.ArrayList<>(fact.fields());
                  fields.set(fieldIndex, replacement);
                  return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
                })
            .toList());
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField optionalText(@Nullable String value) {
    return value == null
        ? AttestationField.absent()
        : present(AttestationTextFieldValue.text(value));
  }

  private static AttestationFieldValue token(String value) {
    return AttestationTextFieldValue.token(value);
  }

  private record CloseEvidence(
      AttestationOperationPayload payload,
      AttestationVerifiedOperationProvenance provenance,
      AttestationPreimage request,
      AttestationPreimage effect) {}
}
