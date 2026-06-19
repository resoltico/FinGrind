package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  private static final List<String> ENTRY_SEMANTICS_FACTORY_NAMES =
      List.of(
          "accountTypeMismatch",
          "distinctRoleAccountsRequired",
          "economicNullJournal",
          "financialPositionClassificationMismatch",
          "sourceDocumentTypeNotAccepted");

  private static final List<String> ENTRY_SEMANTICS_CANONICAL_CODES =
      List.of(
          "economic-null-journal",
          "distinct-role-accounts-required",
          "account-type-mismatch",
          "financial-position-classification-mismatch",
          "source-document-type-not-accepted");
  private static final List<String> ACCOUNT_STATE_CANONICAL_CODES =
      List.of("unknown-account", "inactive-account", "non-postable-account");

  private static final List<String> ENTRY_SEMANTICS_DETAIL_FIELD_NAMES =
      List.of("code", "field", "message", "category", "repair");

  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "duplicate-idempotency-key",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "open-accounting-position-window-closed",
            "open-accounting-position-touches-nominal-account",
            "result-holding-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.EntrySemanticsViolations(
                    List.of(
                        PostingRejectionSemantics.accountTypeMismatch(
                            "CASH_REVENUE",
                            "cashAccountCode",
                            new AccountCode("1000"),
                            AccountType.ASSET,
                            AccountType.REVENUE)))),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.DuplicateIdempotencyKey()),
            PostingRejection.wireCode(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    CurrencyUnit.of("EUR"), CurrencyUnit.of("USD"))),
            PostingRejection.wireCode(
                new PostingRejection.TransferredPeriodResultViolation(
                    java.time.LocalDate.parse("2026-04-30"),
                    java.time.LocalDate.parse("2026-05-01"))),
            PostingRejection.wireCode(
                new PostingRejection.OpenAccountingPositionWindowClosed(
                    PostingKind.STANDARD, java.time.LocalDate.parse("2026-05-02"))),
            PostingRejection.wireCode(
                new PostingRejection.OpenAccountingPositionTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE)),
            PostingRejection.wireCode(
                new PostingRejection.ResultHoldingAccountReserved(new AccountCode("3000"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of("unknown-account", "inactive-account", "non-postable-account"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
            PostingRejection.wireCode(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), dev.erst.fingrind.core.AccountNodeKind.HEADER))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "duplicate-idempotency-key",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "open-accounting-position-window-closed",
            "open-accounting-position-touches-nominal-account",
            "result-holding-account-reserved",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
        PostingRejection.bookNotInitializedCode());
  }

  @Test
  void entrySemanticsOwner_remainsExhaustiveAndPublishesCanonicalDetailDescriptors() {
    assertEquals(
        ENTRY_SEMANTICS_FACTORY_NAMES,
        Arrays.stream(PostingRejectionSemantics.class.getDeclaredMethods())
            .filter(
                method ->
                    Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == PostingRejection.EntrySemanticsViolation.class)
            .map(Method::getName)
            .sorted()
            .toList());

    ContractResponse.RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "entry-semantics-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        descriptor.detailRejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
    assertTrue(
        descriptor.detailFields().getFirst().description().contains("category"),
        descriptor.detailFields().toString());
    assertTrue(
        descriptor.detailFields().getFirst().description().contains("repair"),
        descriptor.detailFields().toString());
    assertEquals(
        ENTRY_SEMANTICS_DETAIL_FIELD_NAMES,
        recordComponentNames(PostingRejection.EntrySemanticsViolation.class));
    assertTrue(
        descriptor.detailRejections().stream()
            .allMatch(
                detail ->
                    detail.detailFields().stream()
                        .map(ContractResponse.FieldDescriptor::name)
                        .toList()
                        .equals(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES)),
        descriptor.toString());
  }

  @Test
  void accountStateOwner_isPreparedForTheUniformRepairableViolationCore() {
    ContractResponse.RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "account-state-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ACCOUNT_STATE_CANONICAL_CODES,
        descriptor.detailRejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
    for (ContractResponse.RejectionDescriptor detailDescriptor : descriptor.detailRejections()) {
      List<String> fieldNames =
          detailDescriptor.detailFields().stream()
              .map(ContractResponse.FieldDescriptor::name)
              .toList();
      assertTrue(fieldNames.containsAll(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES), fieldNames.toString());
      assertTrue(fieldNames.contains("accountCode"), fieldNames.toString());
    }
  }

  @Test
  void singletonPostingRejectionFamiliesRemainSingleIssueEnvelopes() {
    ContractResponse.RejectionDescriptor duplicateIdempotencyKey =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "duplicate-idempotency-key".equals(rejection.code()))
            .findFirst()
            .orElseThrow();
    ContractResponse.RejectionDescriptor functionalCurrencyMismatch =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "book-functional-currency-mismatch".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertTrue(duplicateIdempotencyKey.detailRejections().isEmpty());
    assertTrue(functionalCurrencyMismatch.detailRejections().isEmpty());
    assertTrue(
        duplicateIdempotencyKey.detailFields().stream()
            .noneMatch(field -> "violations".equals(field.name())));
    assertTrue(
        functionalCurrencyMismatch.detailFields().stream()
            .noneMatch(field -> "violations".equals(field.name())));
  }

  @Test
  void rejectionFactories_exposeStableEntrySemanticsDetails() {
    PostingRejection.EntrySemanticsViolation accountTypeViolation =
        PostingRejectionSemantics.accountTypeMismatch(
            "CASH_REVENUE",
            "cashAccountCode",
            new AccountCode("1000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    assertEquals("account-type-mismatch", accountTypeViolation.code());
    assertEquals("cashAccountCode", accountTypeViolation.field());

    PostingRejection.EntrySemanticsViolation classificationViolation =
        PostingRejectionSemantics.financialPositionClassificationMismatch(
            "EQUITY_CONTRIBUTION",
            "equityAccountCode",
            new AccountCode("3000"),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            null);
    assertEquals("financial-position-classification-mismatch", classificationViolation.code());
    assertEquals("equityAccountCode", classificationViolation.field());
    assertTrue(classificationViolation.message().contains("<absent>"));

    PostingRejection.EntrySemanticsViolation classifiedMismatch =
        PostingRejectionSemantics.financialPositionClassificationMismatch(
            "EQUITY_WITHDRAWAL",
            "equityAccountCode",
            new AccountCode("3100"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            FinancialPositionLineClassification.RESULT_HOLDING);
    assertTrue(classifiedMismatch.message().contains("RESULT_HOLDING"));

    PostingRejection.EntrySemanticsViolation evidenceViolation =
        PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
            "CASH_EXPENSE",
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));
    assertEquals("source-document-type-not-accepted", evidenceViolation.code());
    assertEquals("evidence.sourceDocuments[].sourceDocumentType", evidenceViolation.field());
    assertTrue(evidenceViolation.message().contains("expense-receipt, cash-disbursement"));

    PostingRejection.EntrySemanticsViolation distinctRoleAccountsViolation =
        PostingRejectionSemantics.distinctRoleAccountsRequired(
            "CASH_REVENUE", "cashAccountCode", "revenueAccountCode", new AccountCode("1000"));
    PostingRejection.EntrySemanticsViolation economicNullJournal =
        PostingRejectionSemantics.economicNullJournal("JOURNAL");
    assertEquals("distinct-role-accounts-required", distinctRoleAccountsViolation.code());
    assertEquals(null, distinctRoleAccountsViolation.field());
    assertTrue(distinctRoleAccountsViolation.message().contains("cashAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("revenueAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("1000"));
    assertEquals("economic-null-journal", economicNullJournal.code());
    assertEquals("lines", economicNullJournal.field());
    assertTrue(economicNullJournal.message().contains("reduces every referenced account to zero"));
    assertTrue(economicNullJournal.message().contains("JOURNAL"));

    assertIterableEquals(
        List.of(new AccountCode("1000"), new AccountCode("2000")),
        List.copyOf(
            PostingRejectionSemantics.referencedAccountSet(
                new AccountCode("1000"), new AccountCode("1000"), new AccountCode("2000"))));
    assertThrows(
        NullPointerException.class,
        () -> PostingRejectionSemantics.referencedAccountSet(new AccountCode("1000"), nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            PostingRejectionSemantics.referencedAccountSet(
                NullTestSupport.<AccountCode[]>nullOf()));
  }

  @Test
  void violationFamilies_copyInputsAndRejectEmptyCollections() {
    List<PostingRejection.AccountStateViolation> accountViolations = new ArrayList<>();
    accountViolations.add(
        new PostingRejection.NonPostableAccount(new AccountCode("3000"), AccountNodeKind.HEADER));
    PostingRejection.AccountStateViolations copiedAccountViolations =
        new PostingRejection.AccountStateViolations(accountViolations);
    accountViolations.clear();
    assertEquals(1, copiedAccountViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.AccountStateViolations(List.of()));

    List<PostingRejection.EntrySemanticsViolation> entryViolations = new ArrayList<>();
    entryViolations.add(PostingRejectionSemantics.economicNullJournal("JOURNAL"));
    PostingRejection.EntrySemanticsViolations copiedEntryViolations =
        new PostingRejection.EntrySemanticsViolations(entryViolations);
    entryViolations.clear();
    assertEquals(1, copiedEntryViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolations(List.of()));
  }

  @Test
  void entrySemanticsViolation_rejectsBlankStructuredFields() {
    assertEquals(
        ENTRY_SEMANTICS_DETAIL_FIELD_NAMES,
        recordComponentNames(PostingRejection.EntrySemanticsViolation.class));

    IllegalArgumentException blankCode =
        assertEntrySemanticsViolationValidationFailure(
            "", null, "message", "classification", "repair");
    IllegalArgumentException blankField =
        assertEntrySemanticsViolationValidationFailure(
            "code", " ", "message", "classification", "repair");
    IllegalArgumentException blankMessage =
        assertEntrySemanticsViolationValidationFailure(
            "code", null, "", "classification", "repair");
    IllegalArgumentException blankCategory =
        assertEntrySemanticsViolationValidationFailure("code", null, "message", " ", "repair");
    IllegalArgumentException blankRepair =
        assertEntrySemanticsViolationValidationFailure(
            "code", null, "message", "classification", "");

    assertEquals("code must not be blank.", blankCode.getMessage());
    assertEquals("field must not be blank.", blankField.getMessage());
    assertEquals("message must not be blank.", blankMessage.getMessage());
    assertEquals("category must not be blank.", blankCategory.getMessage());
    assertEquals("repair must not be blank.", blankRepair.getMessage());
  }

  @Test
  void entrySemanticsViolation_rejectsUnsupportedCodesAndKnownMetadataDrift() {
    IllegalArgumentException unsupportedCode =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation("unsupported-code", null, "message"));
    assertEquals(
        "Unsupported entry semantics violation code: 'unsupported-code'.",
        unsupportedCode.getMessage());

    assertDoesNotThrow(
        () ->
            new PostingRejection.EntrySemanticsViolation(
                "code", null, "message", "classification", "repair"));

    IllegalArgumentException wrongCategory =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation(
                    "economic-null-journal",
                    "lines",
                    "message",
                    "wrong-category",
                    "Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting."));
    IllegalArgumentException wrongRepair =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation(
                    "economic-null-journal", "lines", "message", "journal-lines", "wrong repair"));

    assertEquals(
        "Entry semantics violation category for code 'economic-null-journal' must be 'journal-lines'.",
        wrongCategory.getMessage());
    assertEquals(
        "Entry semantics violation repair for code 'economic-null-journal' must be 'Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting.'.",
        wrongRepair.getMessage());
  }

  private static List<String> recordComponentNames(Class<?> recordType) {
    return List.of(recordType.getRecordComponents()).stream()
        .map(RecordComponent::getName)
        .toList();
  }

  private static IllegalArgumentException assertEntrySemanticsViolationValidationFailure(
      String code,
      @org.jspecify.annotations.Nullable String field,
      String message,
      String category,
      String repair) {
    Constructor<PostingRejection.EntrySemanticsViolation> constructor =
        assertDoesNotThrow(
            () ->
                PostingRejection.EntrySemanticsViolation.class.getDeclaredConstructor(
                    String.class, String.class, String.class, String.class, String.class));
    InvocationTargetException invocationTargetException =
        assertThrows(
            InvocationTargetException.class,
            () -> constructor.newInstance(code, field, message, category, repair));
    return assertInstanceOf(IllegalArgumentException.class, invocationTargetException.getCause());
  }
}
