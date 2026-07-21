package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises the complete immutable projection shapes admitted for each Slice 5 mutation family. */
class AttestationMutationProjectionCoverageTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-20");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-20T12:30:45Z");
  private static final ReportingPeriod JULY_2026 =
      new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));

  @Test
  void postingProjection_commitsEveryRequestAndEffectFact() {
    AttestationPostingRequestSnapshot request = postingRequest();
    AttestationPostingEffectSnapshot effect = postingEffect("record-reversal");

    AttestationOperationPreimages projected =
        AttestationPostingMutationProjection.project(request, effect);
    AttestationPreimage requestPreimage = decode(projected.request());
    AttestationPreimage effectPreimage = decode(projected.effect());

    assertEquals(List.of(0x0100, 0x0120, 0x0124, 0x0124, 0x012A, 0x012A), tags(requestPreimage));
    assertEquals(List.of(0x0020, 0x0021, 0x0021, 0x0025, 0x0025), tags(effectPreimage));
    assertEquals("record-reversal", token(requestPreimage, 0x0100, 0));
    assertEquals("record-reversal", token(effectPreimage, 0x0020, 3));
    assertEquals("CLI", request.sourceChannel());
    assertNotEquals(List.of(), requestPreimage.records());

    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPostingMutationProjection.project(request, postingEffect("post-entry")));
  }

  @Test
  void postingSnapshotValues_rejectIncompleteOrInvalidCallerFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                "requires-prior",
                List.of(document()),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                "b2431ea7-bb0d-4677-bd1e-04cb7fcfd12f",
                null,
                List.of(document()),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                null,
                List.of(),
                List.of(line("1000", "DEBIT", 100))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                "post-entry",
                "idempotency",
                "cause",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                null,
                List.of(document()),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPostingLine("1000", "DEBIT", "EUR", 0));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationPostingEvidenceDocument("document", "invoice", nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingEffectSnapshot(
                UUID.randomUUID(),
                " ",
                "post-entry",
                "post-entry",
                RECORDED_AT,
                null,
                UUID.randomUUID()));
  }

  @Test
  void taxRegistrationProjection_commitsTheFullCatalogAndValidatesEffectIdentity() {
    AttestationTaxRegistrationSnapshot requested = taxRegistration("registration-1", "LV-123");
    AttestationTaxRegistrationSnapshot persisted = taxRegistration("registration-1", nullOf());

    AttestationOperationPreimages created =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration", requested, persisted, AttestationEffectMutation.CREATE);
    AttestationOperationPreimages amended =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration", requested, requested, AttestationEffectMutation.AMEND);

    assertEquals(List.of(0x0100, 0x0113, 0x0114, 0x0114), tags(decode(created.request())));
    assertEquals(List.of(0x0013, 0x0014, 0x0014), tags(decode(created.effect())));
    assertNotEquals(
        java.util.Arrays.toString(created.effect()), java.util.Arrays.toString(amended.effect()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationTaxRegistrationMutationProjection.project(
                "declare-tax-registration",
                requested,
                persisted,
                AttestationEffectMutation.RETIRE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationTaxRegistrationMutationProjection.project(
                "declare-tax-registration",
                requested,
                taxRegistration("registration-2", "LV-123"),
                AttestationEffectMutation.CREATE));
    assertThrows(IllegalArgumentException.class, () -> taxRegistration("registration-1", " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationTaxCodeSnapshot("VAT", "Value added tax", -1, "EXCLUSIVE", "SALE"));
  }

  @Test
  void accountProjection_commitsRichTaxonomyAndEnforcesIntent() {
    AttestationAccountSnapshot rich = richAccount(true);
    AttestationAccountSnapshot persisted = richAccount(false);

    AttestationOperationPreimages projected =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            rich,
            persisted,
            AttestationEffectMutation.REACTIVATE);

    assertEquals(
        List.of(0x0100, 0x0110, 0x0111, 0x0111, 0x0111, 0x0112, 0x0112),
        tags(decode(projected.request())));
    assertEquals(
        List.of(0x0010, 0x0011, 0x0011, 0x0011, 0x0012, 0x0012), tags(decode(projected.effect())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationProjection.project(
                AttestationAccountMutationIntent.AMENDMENT,
                "amend-account",
                rich,
                persisted,
                AttestationEffectMutation.CREATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationProjection.project(
                AttestationAccountMutationIntent.RETIREMENT,
                "retire-account",
                rich,
                account("1001"),
                AttestationEffectMutation.RETIRE));
  }

  @Test
  void lifecycleProjection_commitsBackupRestoreAndRekeyWithoutSecretMaterial() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();

    assertEquals(
        List.of(0x0100, 0x0150),
        tags(
            decode(
                AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement)
                    .request())));
    assertEquals(
        List.of(0x0006),
        tags(
            decode(
                AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement)
                    .effect())));
    assertEquals(
        List.of(0x0100, 0x0160),
        tags(
            decode(
                AttestationLifecycleMutationProjection.restoreBook("restore-book", acknowledgement)
                    .request())));
    assertEquals(
        List.of(0x00A0),
        tags(
            decode(
                AttestationLifecycleMutationProjection.restoreBook("restore-book", acknowledgement)
                    .effect())));
    assertEquals(
        List.of(0x0100, 0x0170),
        tags(
            decode(
                AttestationLifecycleMutationProjection.rekeyBook(
                        "rekey-book", BigInteger.ONE, RECORDED_AT, Optional.of("scheduled"))
                    .request())));
    assertEquals(
        List.of(0x0100, 0x0170),
        tags(
            decode(
                AttestationLifecycleMutationProjection.rekeyBook(
                        "rekey-book", BigInteger.TWO, RECORDED_AT, Optional.empty())
                    .request())));
  }

  @Test
  void timeBearingSnapshots_canonicalizeLiveClockPrecisionBeforePreimageEncoding() {
    Instant liveClockInstant = Instant.parse("2026-07-20T12:30:45.123456789Z");
    AttestationPostingEffectSnapshot posting =
        new AttestationPostingEffectSnapshot(
            UUID.fromString("42617efc-7425-4b42-b990-4b4eca2843ce"),
            "record-reversal",
            "RECORD_REVERSAL",
            "RECORD_REVERSAL",
            liveClockInstant,
            UUID.fromString("d3d93f87-d85b-457c-b3e3-75b0f7bb6b9f"),
            UUID.fromString("927bf5f6-d6a0-4084-9365-7a7e375fc72b"));
    AttestationClosePostingSnapshot close =
        new AttestationClosePostingSnapshot(
            UUID.fromString("0d566aee-13cc-429c-af01-b11dfd4687fe"),
            UUID.fromString("d324c4b5-503a-4e10-a9ef-699ffddc147e"),
            "close-idempotency",
            "close-causation",
            "interim-result-sweep",
            "interim-result-sweep",
            EFFECTIVE_DATE,
            liveClockInstant,
            "SYSTEM",
            List.of(line("4000", "DEBIT", 100), line("3200", "CREDIT", 100)));

    assertEquals(Instant.parse("2026-07-20T12:30:45.123Z"), posting.recordedAt());
    assertEquals(Instant.parse("2026-07-20T12:30:45.123Z"), close.recordedAt());
    assertDoesNotThrow(
        () -> AttestationPostingMutationProjection.project(postingRequest(), posting));
    assertDoesNotThrow(
        () ->
            AttestationLifecycleMutationProjection.rekeyBook(
                "rekey-book", BigInteger.ONE, liveClockInstant, Optional.empty()));
  }

  @Test
  void periodCloseProjection_commitsGeneratedPostingsAndAggregateCloseEffects() {
    List<AttestationClosePostingSnapshot> postings = List.of(closePosting("interim-result-sweep"));
    List<CurrencyBalance> totals =
        List.of(CurrencyBalance.ofTotals(Money.parse("EUR", "23.00"), Money.parse("EUR", "5.00")));

    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep", JULY_2026, "3200", 1, totals, postings);
    AttestationOperationPreimages fiscal =
        AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
            "fiscal-year-close",
            JULY_2026,
            "3000",
            "3200",
            "3300",
            2,
            List.of(closePosting("fiscal-year-close")));

    assertEquals(List.of(0x0100, 0x0120, 0x0140), tags(decode(interim.request())));
    assertEquals(
        List.of(0x0020, 0x0025, 0x0025, 0x0040, 0x0041, 0x0042), tags(decode(interim.effect())));
    assertEquals(List.of(0x0020, 0x0025, 0x0025, 0x0043, 0x0044), tags(decode(fiscal.effect())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep", JULY_2026, "3200", 0, totals, postings));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
                "fiscal-year-close", JULY_2026, "3000", "3200", "3300", 1, List.of()));
  }

  @Test
  void planProjection_rewritesChildFactsAndRejectsMalformedChildCollections() {
    AttestationOperationPreimages posting =
        AttestationPostingMutationProjection.project(
            postingRequest(), postingEffect("record-reversal"));
    AttestationOperationPreimages account =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            account("1000"),
            account("1000"),
            AttestationEffectMutation.CREATE);
    AttestationOperationPreimages projected =
        AttestationPlanMutationProjection.project(
            "plan-1",
            List.of(
                new AttestationPlanOperationAuthorizer.ChildMutation(3, "record-reversal", posting),
                new AttestationPlanOperationAuthorizer.ChildMutation(
                    9, "declare-account", account)));

    assertEquals(
        1, tags(decode(projected.request())).stream().filter(tag -> tag == 0x0100).count());
    assertEquals(0, tags(decode(projected.effect())).stream().filter(tag -> tag == 0x0100).count());
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPlanMutationProjection.project("plan-1", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan-1",
                List.of(
                    new AttestationPlanOperationAuthorizer.ChildMutation(
                        2, "record-reversal", posting),
                    new AttestationPlanOperationAuthorizer.ChildMutation(
                        2, "declare-account", account))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan-1",
                List.of(
                    new AttestationPlanOperationAuthorizer.ChildMutation(
                        1, "declare-account", posting))));
  }

  @Test
  void planOperationAuthorizer_collectsOnlyOrderedChildMutationsAndSignsOnce() {
    AttestationEvidence expected =
        new AttestationEvidence(new byte[] {1}, new byte[] {2}, new byte[] {3});
    AttestationPlanOperationAuthorizer authorizer =
        new AttestationPlanOperationAuthorizer(request -> expected);
    AttestationOperationPreimages child =
        AttestationPostingMutationProjection.project(
            postingRequest(), postingEffect("record-reversal"));

    assertFalse(authorizer.hasChildMutations());
    assertThrows(
        IllegalStateException.class,
        () -> authorizer.collectChildMutation("record-reversal", child));
    assertThrows(IllegalArgumentException.class, () -> authorizer.enterStep(-1));
    authorizer.enterStep(4);
    authorizer.collectChildMutation("record-reversal", child);
    assertTrue(authorizer.hasChildMutations());
    assertNotEquals(0, authorizer.planPreimages("plan-2").request().length);
    assertThrows(IllegalStateException.class, () -> authorizer.enterStep(4));
    assertThrows(
        IllegalStateException.class,
        () -> authorizer.authorize(operationRequest("record-reversal", child)));
    assertSame(expected, authorizer.authorizePlan(operationRequest("record-reversal", child)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPlanOperationAuthorizer.ChildMutation(-1, "record-reversal", child));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPlanOperationAuthorizer.ChildMutation(1, " ", child));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationPlanOperationAuthorizer.ChildMutation(1, "record-reversal", nullOf()));
    assertThrows(
        NullPointerException.class, () -> new AttestationPlanOperationAuthorizer(nullOf()));
  }

  private static AttestationPostingRequestSnapshot postingRequest() {
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

  private static AttestationPostingEffectSnapshot postingEffect(String operationKind) {
    return new AttestationPostingEffectSnapshot(
        UUID.fromString("42617efc-7425-4b42-b990-4b4eca2843ce"),
        operationKind,
        "RECORD_REVERSAL",
        "RECORD_REVERSAL",
        RECORDED_AT,
        UUID.fromString("d3d93f87-d85b-457c-b3e3-75b0f7bb6b9f"),
        UUID.fromString("927bf5f6-d6a0-4084-9365-7a7e375fc72b"));
  }

  private static AttestationPostingEvidenceDocument document() {
    return new AttestationPostingEvidenceDocument("invoice-42", "invoice", EFFECTIVE_DATE);
  }

  private static AttestationPostingLine line(String accountCode, String side, long minorUnits) {
    return new AttestationPostingLine(accountCode, side, "EUR", minorUnits);
  }

  private static AttestationTaxRegistrationSnapshot taxRegistration(
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

  private static AttestationAccountSnapshot richAccount(boolean active) {
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

  private static AttestationAccountSnapshot account(String accountCode) {
    return new AttestationAccountSnapshot(
        new AccountCode(accountCode),
        new AccountName("Account " + accountCode),
        AccountType.ASSET,
        AccountTaxonomy.empty(),
        null,
        true);
  }

  private static AttestationBackupAcknowledgement acknowledgement() {
    return new AttestationBackupAcknowledgement(
        UUID.fromString("4527c01b-654b-499c-88d7-dc1a14969215"),
        AttestationHash.sha256(new byte[] {1}).bytes(),
        BigInteger.valueOf(17),
        AttestationHash.sha256(new byte[] {2}).bytes());
  }

  private static AttestationClosePostingSnapshot closePosting(String operationKind) {
    return new AttestationClosePostingSnapshot(
        UUID.fromString("0d566aee-13cc-429c-af01-b11dfd4687fe"),
        UUID.fromString("d324c4b5-503a-4e10-a9ef-699ffddc147e"),
        "close-idempotency",
        "close-causation",
        operationKind,
        operationKind,
        EFFECTIVE_DATE,
        RECORDED_AT,
        "SYSTEM",
        List.of(line("4000", "DEBIT", 100), line("3200", "CREDIT", 100)));
  }

  private static AttestationOperationRequest operationRequest(
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

  private static AttestationPreimage decode(byte[] encoded) {
    return AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  private static List<Integer> tags(AttestationPreimage preimage) {
    return preimage.records().stream().map(AttestationPreimage.Fact::recordTypeTag).toList();
  }

  private static String token(AttestationPreimage preimage, int recordTypeTag, int fieldIndex) {
    AttestationPreimage.Fact fact =
        preimage.records().stream()
            .filter(record -> record.recordTypeTag() == recordTypeTag)
            .findFirst()
            .orElseThrow();
    return AttestationPreimageValueReader.token(
        fact, fieldIndex, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }
}
