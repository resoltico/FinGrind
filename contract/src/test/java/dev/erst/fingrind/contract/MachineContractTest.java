package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
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
    assertEquals("--output", capabilities.requestInput().queryOutputOption());
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
        capabilities.requestInput().queryOutputModes());
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
    ContractTemplates.PostingRequestTemplateDescriptor template =
        MachineContract.requestTemplate(
            Clock.fixed(Instant.parse("2026-04-13T12:00:00Z"), java.time.ZoneOffset.UTC));
    ContractTemplates.ReversalTemplateDescriptor reversalTemplate =
        new ContractTemplates.ReversalTemplateDescriptor("posting-1", "operator reversal");

    assertEquals("FinGrind", help.application());
    assertEquals("single-currency-per-entry", help.bookModel().currencyScope());
    assertEquals(20, help.commands().size());
    assertEquals(OperationId.GENERATE_BOOK_KEY_FILE, help.commands().get(5).name());
    assertEquals(OperationId.OPEN_BOOK, help.commands().get(6).name());
    assertEquals(OperationId.REKEY_BOOK, help.commands().get(7).name());
    assertEquals(OperationId.INSPECT_BOOK, help.commands().get(9).name());
    assertEquals(OperationId.ACCOUNT_BALANCE, help.commands().get(13).name());
    assertEquals(OperationId.TRIAL_BALANCE, help.commands().get(14).name());
    assertEquals(OperationId.ACCOUNT_LEDGER, help.commands().get(15).name());
    assertEquals(OperationId.PERIOD_SUMMARY, help.commands().get(16).name());
    assertTrue(help.commands().get(7).options().get(2).contains("--new-book-passphrase-prompt"));
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
        help.commands().get(14).outputModes());
    assertEquals("pdf", help.commands().get(14).artifactOutputs().getFirst().format());
    assertEquals("--pdf-out <path>", help.commands().get(14).artifactOutputs().getFirst().option());
    assertEquals(5, help.exitCodes().size());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(environment, help.environment());
    assertTrue(help.quickStart().stream().noneMatch(example -> example.contains("docs/examples/")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(example -> example.contains("./declare-account-cash.json")));
    assertTrue(
        help.quickStart().stream()
            .anyMatch(example -> example.contains("./declare-account-revenue.json")));

    assertEquals("0.9.0", version.version());
    assertEquals(
        environment.distribution().supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(
        environment.distribution().unsupportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliBundleTargets());
    assertEquals("2026-04-13", template.effectiveDate());
    assertEquals("1000", template.lines().get(0).accountCode());
    assertEquals(ActorType.HUMAN, template.provenance().actorType());
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
            List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            "3.53.0",
            "2.3.3",
            SqliteRuntimeStatus.READY,
            "3.53.0",
            "2.3.3",
            null));
  }
}
