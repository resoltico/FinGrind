package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MachineContract}. */
class MachineContractTest {
  @Test
  void capabilities_areDerivedFromLiveEnumsAndRejectionCatalogs() {
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ApplicationIdentity("FinGrind", "0.9.0", "desc"),
            readyEnvironmentDescriptor(),
            Instant.parse("2026-04-13T12:00:00Z"));

    assertEquals("advisory", capabilities.preflight().semantics());
    assertEquals(
        ContractResponse.CommitGuarantee.NOT_GUARANTEED,
        capabilities.preflight().commitGuarantee());
    assertEquals("single-currency-per-entry", capabilities.currencyModel().scope());
    assertEquals("not-supported", capabilities.currencyModel().multiCurrencyStatus());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        capabilities.requestInput().bookPassphraseOptions());
    assertEquals("--output", capabilities.requestInput().outputOption());
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN),
        command(capabilities.commands().discovery(), OperationId.VERSION).outputModes());
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
        command(capabilities.commands().query(), OperationId.TRIAL_BALANCE).outputModes());
    assertEquals(
        List.of(),
        command(capabilities.commands().write(), OperationId.EXECUTE_PLAN).outputModes());
    assertTrue(
        capabilities
            .requestInput()
            .requestDocumentSemantics()
            .contains("duplicate JSON object keys are rejected"));

    assertEquals(
        enumValues(JournalLine.EntrySide.values()),
        vocabularyValues(capabilities.requestShapes().postEntry().enumVocabularies(), "lineSide"));
    assertEquals(
        enumValues(ActorType.values()),
        vocabularyValues(capabilities.requestShapes().postEntry().enumVocabularies(), "actorType"));
    assertEquals(
        enumValues(NormalBalance.values()),
        vocabularyValues(
            capabilities.requestShapes().declareAccount().enumVocabularies(), "normalBalance"));
    assertEquals(
        "https://json-schema.org/draft/2020-12/schema",
        capabilities.requestShapes().schemaDialect());
    assertEquals("object", capabilities.requestShapes().postEntry().schema().get("type"));
    assertEquals("object", capabilities.requestShapes().declareAccount().schema().get("type"));
    assertEquals(
        "array",
        nestedSchemaProperty(capabilities.requestShapes().ledgerPlan().schema(), "steps", "type"));
    assertEquals(
        RequestFieldPresence.CONDITIONAL,
        fieldPresence(capabilities.requestShapes().ledgerPlan().stepFields(), "posting"));
    assertEquals(
        RequestFieldPresence.CONDITIONAL,
        fieldPresence(capabilities.requestShapes().ledgerPlan().queryFields(), "accountCode"));

    List<String> rejectionCodes =
        capabilities.responseModel().rejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList();
    List<String> errorCodes =
        capabilities.responseModel().errorDescriptors().stream()
            .map(ContractResponse.ErrorDescriptor::code)
            .toList();
    assertTrue(rejectionCodes.contains("administration-book-not-initialized"));
    assertTrue(rejectionCodes.contains("query-book-not-initialized"));
    assertTrue(rejectionCodes.contains("posting-book-not-initialized"));
    assertTrue(rejectionCodes.contains("account-normal-balance-conflict"));
    assertTrue(rejectionCodes.contains("posting-not-found"));
    assertTrue(rejectionCodes.contains("reversal-does-not-negate-target"));
    assertEquals(rejectionCodes.size(), rejectionCodes.stream().distinct().count());
    assertTrue(errorCodes.contains("invalid-page-cursor"));
    assertTrue(errorCodes.contains("interactive-prompt-unavailable"));
    assertTrue(errorCodes.contains("book-authentication-failed"));
    assertTrue(errorCodes.contains("managed-runtime-failure"));
    assertTrue(errorCodes.contains("storage-runtime-failure"));
    assertTrue(errorCodes.contains("pdf-export-failure"));
    assertEquals(errorCodes.size(), errorCodes.stream().distinct().count());
  }

  @Test
  void rejectionCatalogs_coverEveryPermittedSubtype() {
    assertEquals(
        BookAdministrationRejection.class.getPermittedSubclasses().length,
        BookAdministrationRejection.descriptors().size());
    assertEquals(
        PostingRejection.class.getPermittedSubclasses().length,
        PostingRejection.descriptors().size());
    assertEquals(
        BookQueryRejection.class.getPermittedSubclasses().length,
        BookQueryRejection.descriptors().size());
  }

  @Test
  void helpVersionAndRequestTemplate_publishCanonicalDiscoveryMetadata() {
    ApplicationIdentity identity = new ApplicationIdentity("FinGrind", "0.9.0", "desc");
    EnvironmentDescriptor environment = readyEnvironmentDescriptor();

    HelpDescriptor help = MachineContract.help(identity, environment);
    VersionDescriptor version = MachineContract.version(identity);
    ContractTemplates.PostingRequestTemplateDescriptor template = MachineContract.requestTemplate();
    ContractTemplates.ReversalTemplateDescriptor reversalTemplate =
        new ContractTemplates.ReversalTemplateDescriptor("posting-1", "operator reversal");

    assertEquals("FinGrind", help.application());
    assertEquals("single-currency-per-entry", help.bookModel().currencyScope());
    assertEquals(20, help.commands().size());
    assertEquals(
        OperationId.GENERATE_BOOK_KEY_FILE,
        command(help.commands(), OperationId.GENERATE_BOOK_KEY_FILE).name());
    assertEquals(OperationId.OPEN_BOOK, command(help.commands(), OperationId.OPEN_BOOK).name());
    assertEquals(OperationId.REKEY_BOOK, command(help.commands(), OperationId.REKEY_BOOK).name());
    assertEquals(
        OperationId.INSPECT_BOOK, command(help.commands(), OperationId.INSPECT_BOOK).name());
    assertEquals(
        OperationId.ACCOUNT_BALANCE, command(help.commands(), OperationId.ACCOUNT_BALANCE).name());
    assertEquals(
        OperationId.TRIAL_BALANCE, command(help.commands(), OperationId.TRIAL_BALANCE).name());
    assertEquals(
        OperationId.ACCOUNT_LEDGER, command(help.commands(), OperationId.ACCOUNT_LEDGER).name());
    assertEquals(
        OperationId.PERIOD_SUMMARY, command(help.commands(), OperationId.PERIOD_SUMMARY).name());
    assertTrue(
        command(help.commands(), OperationId.REKEY_BOOK)
            .options()
            .get(2)
            .contains("--new-book-passphrase-prompt"));
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
        command(help.commands(), OperationId.TRIAL_BALANCE).outputModes());
    assertEquals(
        "pdf",
        command(help.commands(), OperationId.TRIAL_BALANCE).artifactOutputs().getFirst().format());
    assertEquals(
        "--pdf-out <path>",
        command(help.commands(), OperationId.TRIAL_BALANCE).artifactOutputs().getFirst().option());
    assertEquals(5, help.exitCodes().size());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(environment, help.environment());
    assertTrue(
        help.quickStart().stream().noneMatch(step -> step.text().contains("docs/examples/")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.kind() == WorkflowStepKind.EDIT
                        && step.text().contains("./declare-account-cash.json")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.kind() == WorkflowStepKind.COMMAND
                        && step.text().contains("./declare-account-cash.json")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.kind() == WorkflowStepKind.EDIT
                        && step.text().contains("./declare-account-revenue.json")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.kind() == WorkflowStepKind.COMMAND
                        && step.text().contains("./declare-account-revenue.json")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.kind() == WorkflowStepKind.EDIT && step.text().contains("effectiveDate")));
    assertFalse(
        help.quickStart().stream()
            .anyMatch(
                step ->
                    step.text().contains("./cash.json") || step.text().contains("./revenue.json")));

    assertEquals("0.9.0", version.version());
    assertEquals(
        environment.distribution().supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(
        environment.distribution().unsupportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliBundleTargets());
    assertEquals(ScaffoldPlaceholders.EFFECTIVE_DATE, template.effectiveDate());
    assertEquals("1000", template.lines().get(0).accountCode());
    assertEquals(ScaffoldPlaceholders.ACTOR_ID, template.provenance().actorId());
    assertEquals(ActorType.AGENT, template.provenance().actorType());
    assertEquals(ScaffoldPlaceholders.IDEMPOTENCY_KEY, template.provenance().idempotencyKey());
    assertEquals("posting-1", reversalTemplate.priorPostingId());
  }

  private static List<String> enumValues(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static List<String> vocabularyValues(
      List<ContractRequestShapes.EnumVocabularyDescriptor> vocabularies, String name) {
    return vocabularies.stream()
        .filter(vocabulary -> vocabulary.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing vocabulary: " + name))
        .values();
  }

  private static Object nestedSchemaProperty(
      Map<String, Object> schema, String propertyName, String nestedField) {
    Map<?, ?> properties = (Map<?, ?>) Objects.requireNonNull(schema.get("properties"));
    Map<?, ?> propertySchema = (Map<?, ?>) Objects.requireNonNull(properties.get(propertyName));
    return Objects.requireNonNull(propertySchema.get(nestedField));
  }

  private static RequestFieldPresence fieldPresence(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String fieldName) {
    return fields.stream()
        .filter(field -> field.name().equals(fieldName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing field: " + fieldName))
        .presence();
  }

  private static CommandDescriptor command(
      List<CommandDescriptor> commands, OperationId operationId) {
    return commands.stream()
        .filter(command -> command.name() == operationId)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing command: " + operationId.wireName()));
  }

  private static EnvironmentDescriptor readyEnvironmentDescriptor() {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            ProtocolCatalog.bundleRuntimeDistribution(),
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.defaultBookCipher()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            SqliteRuntimeStatus.READY,
            SqliteRuntimeProvenance.BUNDLE_MANAGED,
            "/tmp/libsqlite3.dylib",
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            null));
  }
}
