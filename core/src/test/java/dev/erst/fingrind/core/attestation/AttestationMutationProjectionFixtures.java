package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Supplies deterministic direct-mutation inputs and preimage assertions for projection tests. */
final class AttestationMutationProjectionFixtures {
  static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-20");
  static final Instant RECORDED_AT = Instant.parse("2026-07-20T12:30:45Z");
  static final ReportingPeriod JULY_2026 =
      new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));

  private AttestationMutationProjectionFixtures() {}

  static AttestationPostingRequestSnapshot postingRequest() {
    return new AttestationPostingRequestSnapshot(
        "record-reversal",
        "idempotency-1",
        "causation-1",
        "CLI",
        EFFECTIVE_DATE,
        "RECORD_REVERSAL",
        "b2431ea7-bb0d-4677-bd1e-04cb7fcfd12f",
        "Correction",
        List.of(
            document(),
            new AttestationPostingEvidenceDocument("credit-note-9", "credit-note", EFFECTIVE_DATE)),
        List.of(line("1000", "DEBIT", 100), line("4000", "CREDIT", 100)));
  }

  static AttestationPostingRequestSnapshot planPostingRequest() {
    return new AttestationPostingRequestSnapshot(
        "post-entry",
        "plan-idempotency-1",
        "plan-causation-1",
        "CLI",
        EFFECTIVE_DATE,
        "STANDARD",
        null,
        null,
        List.of(document()),
        List.of(line("1000", "DEBIT", 100), line("4000", "CREDIT", 100)));
  }

  static AttestationPostingEffectSnapshot planPostingEffect() {
    return new AttestationPostingEffectSnapshot(
        UUID.fromString("9d41c173-b7ce-4ddb-ae0a-a3a9d0de7611"),
        "post-entry",
        "STANDARD",
        "DIRECT_JOURNAL",
        RECORDED_AT,
        null,
        UUID.fromString("6b551167-3d5a-4941-b310-162684a904ba"));
  }

  static AttestationPostingEffectSnapshot postingEffect(String operationKind) {
    return new AttestationPostingEffectSnapshot(
        UUID.fromString("42617efc-7425-4b42-b990-4b4eca2843ce"),
        operationKind,
        "RECORD_REVERSAL",
        "RECORD_REVERSAL",
        RECORDED_AT,
        UUID.fromString("d3d93f87-d85b-457c-b3e3-75b0f7bb6b9f"),
        UUID.fromString("927bf5f6-d6a0-4084-9365-7a7e375fc72b"));
  }

  static AttestationPostingEvidenceDocument document() {
    return new AttestationPostingEvidenceDocument("invoice-42", "invoice", EFFECTIVE_DATE);
  }

  static AttestationPostingLine line(String accountCode, String side, long minorUnits) {
    return new AttestationPostingLine(accountCode, side, "EUR", minorUnits);
  }

  static AttestationTaxRegistrationSnapshot taxRegistration(
      String registrationId, String registrationNumber) {
    return new AttestationTaxRegistrationSnapshot(
        registrationId,
        "Latvian VAT",
        "LV",
        registrationNumber,
        "5710",
        "1710",
        "MONTHLY",
        15,
        List.of(
            new AttestationTaxCodeSnapshot("VAT-21", "Standard VAT", 210_000, "EXCLUSIVE", "SALE"),
            new AttestationTaxCodeSnapshot("VAT-0", "Zero VAT", 0, "INCLUSIVE", "PURCHASE")));
  }

  static AttestationAccountSnapshot richAccount(boolean active) {
    return new AttestationAccountSnapshot(
        new AccountCode("1500"),
        new AccountName("Stock"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.of(new AccountCode("1000")),
            Optional.of(new AccountCode("1599")),
            Optional.of(FinancialPositionLineClassification.INVENTORY),
            Optional.of(ProfitAndLossLineClassification.OTHER_EXPENSE),
            Optional.of(CashFlowAssetClassification.NON_CASH)),
        new UnitOfMeasure("unit", 0),
        active);
  }

  static AttestationAccountSnapshot account(String accountCode) {
    return new AttestationAccountSnapshot(
        new AccountCode(accountCode),
        new AccountName("Account " + accountCode),
        AccountType.ASSET,
        AccountTaxonomy.empty(),
        null,
        true);
  }

  static AttestationBackupAcknowledgement acknowledgement() {
    return new AttestationBackupAcknowledgement(
        UUID.fromString("4527c01b-654b-499c-88d7-dc1a14969215"),
        AttestationHash.sha256(new byte[] {1}).bytes(),
        BigInteger.valueOf(17),
        AttestationHash.sha256(new byte[] {2}).bytes());
  }

  static AttestationClosePostingSnapshot closePosting(String operationKind) {
    boolean interimResultSweep = "interim-result-sweep".equals(operationKind);
    return new AttestationClosePostingSnapshot(
        UUID.fromString(
            interimResultSweep
                ? "0d566aee-13cc-429c-af01-b11dfd4687fe"
                : "1d566aee-13cc-429c-af01-b11dfd4687fe"),
        UUID.fromString(
            interimResultSweep
                ? "d324c4b5-503a-4e10-a9ef-699ffddc147e"
                : "e324c4b5-503a-4e10-a9ef-699ffddc147e"),
        interimResultSweep ? "interim-close-idempotency" : "fiscal-close-idempotency",
        interimResultSweep ? "interim-close-causation" : "fiscal-close-causation",
        operationKind,
        operationKind,
        JULY_2026.effectiveDateTo(),
        RECORDED_AT,
        "SYSTEM",
        List.of(line("4000", "DEBIT", 100), line("3200", "CREDIT", 100)));
  }

  static AttestationOperationRequest operationRequest(
      String operationKind, AttestationOperationPreimages preimages) {
    return new AttestationOperationRequest(
        UUID.fromString("a324c4b5-503a-4e10-a9ef-699ffddc147"),
        BigInteger.ONE,
        operationKind,
        AttestationHash.sha256(new byte[] {1}).bytes(),
        RECORDED_AT,
        preimages.request(),
        preimages.effect());
  }

  static AttestationPreimage decode(byte[] encoded) {
    return AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static List<Integer> tags(AttestationPreimage preimage) {
    return preimage.records().stream().map(AttestationPreimage.Fact::recordTypeTag).toList();
  }

  static String token(AttestationPreimage preimage, int recordTypeTag, int fieldIndex) {
    return AttestationPreimageValueReader.token(
        matchingFact(preimage, recordTypeTag),
        fieldIndex,
        AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static BigInteger unsigned32(AttestationPreimage preimage, int recordTypeTag, int fieldIndex) {
    return AttestationPreimageValueReader.unsigned32(
        matchingFact(preimage, recordTypeTag),
        fieldIndex,
        AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static int mutation(AttestationPreimage preimage, int recordTypeTag) {
    return AttestationPreimageValueReader.mutation(
        matchingFact(preimage, recordTypeTag), 0, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static boolean absent(AttestationPreimage preimage, int recordTypeTag, int fieldIndex) {
    return !AttestationPreimageFields.requireField(
            matchingFact(preimage, recordTypeTag), fieldIndex)
        .isPresent();
  }

  static List<Integer> qualifiedSourceSteps(
      AttestationPreimage preimage, int wrapperRecordTypeTag) {
    return preimage.records().stream()
        .filter(fact -> fact.recordTypeTag() == wrapperRecordTypeTag)
        .map(
            fact ->
                AttestationPreimageValueReader.unsigned32(
                        fact, 0, AttestationAuthorizationFailure.PREIMAGE_INVALID)
                    .intValueExact())
        .toList();
  }

  static AttestationPreimage replaceField(
      AttestationPreimage preimage,
      int recordTypeTag,
      int fieldIndex,
      AttestationField replacement) {
    List<AttestationPreimage.Fact> updated = new java.util.ArrayList<>();
    for (AttestationPreimage.Fact fact : preimage.records()) {
      if (fact.recordTypeTag() != recordTypeTag) {
        updated.add(fact);
        continue;
      }
      updated.add(withReplacement(fact, fieldIndex, replacement));
    }
    return AttestationPreimage.of(updated);
  }

  static AttestationPreimage replaceFirstField(
      AttestationPreimage preimage,
      int recordTypeTag,
      int fieldIndex,
      AttestationField replacement) {
    List<AttestationPreimage.Fact> updated = new java.util.ArrayList<>();
    boolean replaced = false;
    for (AttestationPreimage.Fact fact : preimage.records()) {
      if (replaced || fact.recordTypeTag() != recordTypeTag) {
        updated.add(fact);
        continue;
      }
      updated.add(withReplacement(fact, fieldIndex, replacement));
      replaced = true;
    }
    if (!replaced) {
      throw new IllegalArgumentException("No matching preimage fact was available to replace.");
    }
    return AttestationPreimage.of(updated);
  }

  private static AttestationPreimage.Fact matchingFact(
      AttestationPreimage preimage, int recordTypeTag) {
    return preimage.records().stream()
        .filter(record -> record.recordTypeTag() == recordTypeTag)
        .findFirst()
        .orElseThrow();
  }

  private static AttestationPreimage.Fact withReplacement(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationField replacement) {
    List<AttestationField> fields = new java.util.ArrayList<>(fact.fields());
    fields.set(fieldIndex, replacement);
    return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
  }
}
