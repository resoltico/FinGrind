package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepKind;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.protocol.AccountingBaselineTarget;
import dev.erst.fingrind.contract.protocol.CapabilityStatus;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MachineContract}. */
class MachineContractTest {
  @Test
  void capabilities_areDerivedFromLiveEnumsAndRejectionCatalogs() {
    EnvironmentDescriptor environment = readyEnvironmentDescriptor();
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.9.0", "desc"));

    assertPreflightAndCurrencyCapabilities(capabilities, environment);
    assertAccountingBaselineCapabilities(capabilities);
    assertExtensionSurfaceCapabilities(capabilities);
    assertRequestInputCapabilities(capabilities);
    assertCommandOutputModes(capabilities);
    assertRequestShapes(capabilities);
    assertResponseModelCatalog(capabilities);
  }

  private static void assertPreflightAndCurrencyCapabilities(
      CapabilitiesDescriptor capabilities, EnvironmentDescriptor environment) {
    assertEquals("advisory", capabilities.preflight().semantics());
    assertEquals(
        ContractResponse.CommitGuarantee.NOT_GUARANTEED,
        capabilities.preflight().commitGuarantee());
    assertEquals("single-functional-currency-per-book", capabilities.currencyModel().scope());
    assertEquals("not-supported", capabilities.currencyModel().multiCurrencyStatus());
    assertEquals(
        SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
        ((EnvironmentSqliteDescriptor.ReadyRuntime) environment.sqlite().runtime())
            .runtimeTrustBasis());
  }

  private static void assertAccountingBaselineCapabilities(CapabilitiesDescriptor capabilities) {
    assertEquals(
        AccountingBaselineTarget.INTERNAL_MANAGEMENT_STATEMENTS,
        capabilities.accountingBaseline().currentTarget());
    assertEquals(
        AccountingBaselineTarget.BASIC_STANDARD_REPORTING_FOUNDATION,
        capabilities.accountingBaseline().nextTarget());
    assertTrue(
        capabilities
            .accountingBaseline()
            .reportingPosition()
            .contains("Built-in reporting stops at financial position"));
    assertTrue(capabilities.accountingBaseline().nonClaims().contains("IFRS for SMEs parity"));
    assertEquals(
        "internal-management-single-entity-v1",
        capabilities.accountingBaseline().defaultPolicyPack().policyPackId());
    assertTrue(
        capabilities
            .accountingBaseline()
            .chartModelPosition()
            .contains("supports explicit parent-child hierarchy"));
    assertTrue(
        capabilities
            .accountingBaseline()
            .smallEntityPosition()
            .contains("does not yet claim IFRS for SMEs parity"));
    assertTrue(
        capabilities
            .accountingBaseline()
            .operationalPosition()
            .contains("Operational contexts such as invoicing"));
    assertTrue(
        capabilities
            .accountingBaseline()
            .taxPosition()
            .contains("Tax is not a first-class domain"));
    assertTrue(
        capabilities
            .accountingBaseline()
            .organizationalPosition()
            .contains("does not yet claim multi-entity organizational accounting"));
  }

  private static void assertExtensionSurfaceCapabilities(CapabilitiesDescriptor capabilities) {
    assertEquals(
        List.of(
            "policy-profile-selection",
            "entry-recipe-policy",
            "retained-evidence-policy",
            "statement-comparative-policy",
            "chart-policy",
            "close-policy",
            "statement-presentation-policy"),
        capabilities.extensionSurface().implementedSeams());
    assertEquals(
        "internal-management-single-entity-v1",
        capabilities.extensionSurface().defaultPolicyPackId());
    assertTrue(
        capabilities.extensionSurface().policySeams().stream()
            .anyMatch(
                seam ->
                    "close-policy".equals(seam.seamId())
                        && seam.status() == CapabilityStatus.IMPLEMENTED));
  }

  private static void assertRequestInputCapabilities(CapabilitiesDescriptor capabilities) {
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        capabilities.requestInput().bookPassphraseOptions());
    assertTrue(
        capabilities.requestInput().bookPassphraseSemantics().stream()
            .anyMatch(semantic -> semantic.contains("owner-only parent directory")));
    assertTrue(
        capabilities.requestInput().bookPassphraseSemantics().stream()
            .anyMatch(semantic -> semantic.contains("4096-byte UTF-8 limit")));
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
  }

  private static void assertCommandOutputModes(CapabilitiesDescriptor capabilities) {
    assertNotNull(capabilities.requestShapes());
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
  }

  private static void assertRequestShapes(CapabilitiesDescriptor capabilities) {
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        Objects.requireNonNull(capabilities.requestShapes());
    ContractRequestShapes.PostEntryRequestShapeDescriptor postEntry =
        Objects.requireNonNull(requestShapes.postEntry());
    ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccount =
        Objects.requireNonNull(requestShapes.declareAccount());
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlan =
        Objects.requireNonNull(requestShapes.ledgerPlan());

    assertEquals(
        enumValues(JournalLine.EntrySide.values()),
        vocabularyValues(postEntry.enumVocabularies(), "lineSide"));
    assertEquals(
        enumValues(ActorType.values()),
        vocabularyValues(postEntry.enumVocabularies(), "actorType"));
    assertEquals(
        enumValues(AccountRole.values()),
        vocabularyValues(declareAccount.enumVocabularies(), "accountRole"));
    assertEquals("https://json-schema.org/draft/2020-12/schema", requestShapes.schemaDialect());
    assertEquals("https://json-schema.org/draft/2020-12/schema", postEntry.schema().get("$schema"));
    assertTrue(postEntry.schema().containsKey("oneOf"));
    assertEquals("object", declareAccount.schema().get("type"));
    assertEquals("array", nestedSchemaProperty(ledgerPlan.schema(), "steps", "type"));
    assertEquals(
        RequestFieldPresence.CONDITIONAL, fieldPresence(ledgerPlan.stepFields(), "posting"));
    assertEquals(
        RequestFieldPresence.CONDITIONAL, fieldPresence(ledgerPlan.queryFields(), "accountCode"));
  }

  private static void assertResponseModelCatalog(CapabilitiesDescriptor capabilities) {
    List<String> rejectionCodes =
        capabilities.responseModel().rejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList();
    List<String> errorCodes =
        capabilities.responseModel().errorDescriptors().stream()
            .map(ContractResponse.ErrorDescriptor::code)
            .toList();
    ContractResponse.ErrorDescriptor invalidRequestError =
        capabilities.responseModel().errorDescriptors().stream()
            .filter(descriptor -> "invalid-request".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    ContractResponse.ErrorDescriptor protectedBookVerificationError =
        capabilities.responseModel().errorDescriptors().stream()
            .filter(descriptor -> "protected-book-verification-failed".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertTrue(rejectionCodes.contains("administration-book-not-initialized"));
    assertTrue(rejectionCodes.contains("query-book-not-initialized"));
    assertTrue(rejectionCodes.contains("posting-book-not-initialized"));
    assertTrue(rejectionCodes.contains("account-role-conflict"));
    assertTrue(rejectionCodes.contains("posting-not-found"));
    assertTrue(rejectionCodes.contains("reversal-does-not-negate-target"));
    assertEquals(rejectionCodes.size(), rejectionCodes.stream().distinct().count());
    assertTrue(errorCodes.contains("invalid-page-cursor"));
    assertTrue(errorCodes.contains("interactive-prompt-unavailable"));
    assertTrue(errorCodes.contains("protected-book-verification-failed"));
    assertTrue(errorCodes.contains("managed-runtime-failure"));
    assertTrue(errorCodes.contains("storage-runtime-failure"));
    assertTrue(errorCodes.contains("pdf-export-failure"));
    assertEquals(1, invalidRequestError.exitCode());
    assertEquals(6, protectedBookVerificationError.exitCode());
    assertEquals(
        List.of("parseMessage", "line", "column", "violations"),
        invalidRequestError.detailFields().stream()
            .map(ContractResponse.FieldDescriptor::name)
            .toList());
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
    ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate =
        MachineContract.declareAccountTemplate();
    ContractTemplates.ReversalTemplateDescriptor reversalTemplate =
        new ContractTemplates.ReversalTemplateDescriptor("posting-1", "operator reversal");

    assertEquals("FinGrind", help.application());
    assertEquals("single-functional-currency-per-book", help.bookModel().currencyScope());
    assertEquals(ProtocolCatalog.operations().size(), help.commands().size());
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
            .contains("--replacement-book-passphrase-prompt"));
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV),
        command(help.commands(), OperationId.TRIAL_BALANCE).outputModes());
    assertEquals(
        "pdf",
        command(help.commands(), OperationId.TRIAL_BALANCE).artifactOutputs().getFirst().format());
    assertEquals(
        "--pdf-out <path>",
        command(help.commands(), OperationId.TRIAL_BALANCE).artifactOutputs().getFirst().option());
    assertEquals(
        List.of(0, 1, 2, 3, 4, 5, 6, 7),
        help.exitCodes().stream().map(ExitCodeDescriptor::code).toList());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(
        List.of(WorkflowSurface.BUNDLE_POSIX_SHELL, WorkflowSurface.BUNDLE_WINDOWS_POWERSHELL),
        help.quickStart().stream().map(WorkflowDescriptor::surface).toList());
    assertTrue(
        quickStartSteps(help)
            .noneMatch(
                step ->
                    switch (step) {
                      case WorkflowStepDescriptor.Command(var commandText) ->
                          commandText.contains("docs/examples/");
                      case WorkflowStepDescriptor.Note(var noteText) ->
                          noteText.contains("docs/examples/");
                      case WorkflowStepDescriptor.Edit ignored -> false;
                    }));
    assertTrue(
        quickStartSteps(help)
            .filter(step -> step.kind() == WorkflowStepKind.COMMAND)
            .allMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Command(String text)
                        && (text.startsWith("./bin/fingrind")
                            || text.startsWith(".\\bin\\fingrind.ps1"))));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Edit(String path, String content)
                        && "./declare-account-cash.json".equals(path)
                        && content.contains("\"accountCode\": \"1000\"")));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Command(String text)
                        && text.contains("./declare-account-cash.json")));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Edit(String path, String content)
                        && "./declare-account-revenue.json".equals(path)
                        && content.contains("\"accountCode\": \"2000\"")));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Command(String text)
                        && text.contains("./declare-account-revenue.json")));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Note(String text)
                        && text.contains("sample evidence and provenance values")));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Edit edit
                        && ".\\declare-account-cash.json".equals(edit.path())));
    assertTrue(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    step instanceof WorkflowStepDescriptor.Command(String text)
                        && text.contains(".\\request.json")));
    assertFalse(
        quickStartSteps(help)
            .anyMatch(
                step ->
                    switch (step) {
                      case WorkflowStepDescriptor.Command(String commandText) ->
                          commandText.contains("./cash.json")
                              || commandText.contains("./revenue.json");
                      case WorkflowStepDescriptor.Note(String noteText) ->
                          noteText.contains("./cash.json") || noteText.contains("./revenue.json");
                      case WorkflowStepDescriptor.Edit edit ->
                          edit.path().contains("./cash.json")
                              || edit.path().contains("./revenue.json");
                    }));

    assertEquals("0.9.0", version.version());
    assertEquals(
        environment.distribution().supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(
        environment.distribution().unsupportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliBundleTargets());
    assertEquals("2026-01-15", template.effectiveDate());
    assertEquals(dev.erst.fingrind.core.BookkeepingEntryKind.CASH_REVENUE, template.entryKind());
    assertEquals("1000", template.cashAccountCode());
    assertEquals("2000", template.revenueAccountCode());
    assertEquals(null, template.lines());
    assertEquals(null, template.postingKind());
    assertEquals("operator-demo-1", template.provenance().actorId());
    assertEquals(ActorType.AGENT, template.provenance().actorType());
    assertEquals("idem-demo-1", template.provenance().idempotencyKey());
    assertEquals("1000", declareAccountTemplate.accountCode());
    assertEquals(AccountRole.ORDINARY, declareAccountTemplate.accountRole());
    assertEquals("posting-1", reversalTemplate.priorPostingId());
  }

  @Test
  void help_quickStartTracksTheActiveRuntimeDistribution() {
    ApplicationIdentity identity = new ApplicationIdentity("FinGrind", "0.9.0", "desc");

    HelpDescriptor sourceCheckoutHelp =
        MachineContract.help(
            identity,
            environmentDescriptorFor(ProtocolCatalog.sourceCheckoutRuntimeDistribution()));
    HelpDescriptor directJavaHelp =
        MachineContract.help(
            identity, environmentDescriptorFor(ProtocolCatalog.directJavaRuntimeDistribution()));
    HelpDescriptor containerHelp =
        MachineContract.help(
            identity, environmentDescriptorFor(ProtocolCatalog.containerRuntimeDistribution()));

    assertEquals(
        List.of(
            WorkflowSurface.SOURCE_CHECKOUT_POSIX_SHELL,
            WorkflowSurface.SOURCE_CHECKOUT_WINDOWS_POWERSHELL),
        sourceCheckoutHelp.quickStart().stream().map(WorkflowDescriptor::surface).toList());
    assertEquals(
        List.of(
            WorkflowSurface.DIRECT_JAVA_POSIX_SHELL,
            WorkflowSurface.DIRECT_JAVA_WINDOWS_POWERSHELL),
        directJavaHelp.quickStart().stream().map(WorkflowDescriptor::surface).toList());
    assertEquals(
        List.of(WorkflowSurface.CONTAINER_DOCKER),
        containerHelp.quickStart().stream().map(WorkflowDescriptor::surface).toList());
  }

  @Test
  void bundleLayoutCommandsRemainSurfaceConsistent() {
    assertEquals(
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64),
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_X86_64));
    assertEquals(
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64),
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.LINUX_X86_64));
    assertEquals(
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64),
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.LINUX_AARCH64));
    assertEquals(
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.WINDOWS_X86_64),
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.WINDOWS_AARCH64));
  }

  @Test
  void help_canBeScopedToOneCommandTopic() {
    ApplicationIdentity identity =
        new ApplicationIdentity("FinGrind", "0.9.0", "Finance-grade bookkeeping kernel");
    EnvironmentDescriptor environment = ContractFixtures.environmentDescriptor();

    HelpDescriptor help = MachineContract.help(identity, environment, OperationId.POST_ENTRY);

    assertEquals(
        List.of(
            "fingrind post-entry --book-file <path> [--book-key-file <path> |"
                + " --book-passphrase-stdin | --book-passphrase-prompt] --request-file <path|->"
                + " [--output <json|human>]"),
        help.usage());
    assertEquals(1, help.commands().size());
    assertEquals(OperationId.POST_ENTRY, help.commands().getFirst().name());
    assertTrue(help.quickStart().isEmpty());
  }

  @Test
  void help_scopedToDeclareAccountPublishesOnlyDeclareAccountShapesAndTemplate() {
    ApplicationIdentity identity =
        new ApplicationIdentity("FinGrind", "0.9.0", "Finance-grade bookkeeping kernel");
    EnvironmentDescriptor environment = ContractFixtures.environmentDescriptor();

    HelpDescriptor help = MachineContract.help(identity, environment, OperationId.DECLARE_ACCOUNT);

    assertEquals(1, help.commands().size());
    assertEquals(OperationId.DECLARE_ACCOUNT, help.commands().getFirst().name());
    assertNotNull(help.requestShapes());
    assertEquals(
        "https://json-schema.org/draft/2020-12/schema",
        Objects.requireNonNull(help.requestShapes()).schemaDialect());
    assertNull(help.requestShapes().postEntry());
    assertNotNull(help.requestShapes().declareAccount());
    assertNull(help.requestShapes().ledgerPlan());
    assertNull(help.requestTemplate());
    assertNotNull(help.declareAccountTemplate());
    assertEquals("1000", Objects.requireNonNull(help.declareAccountTemplate()).accountCode());
    assertEquals(AccountRole.ORDINARY, help.declareAccountTemplate().accountRole());
    assertNull(help.planTemplate());
    assertTrue(help.quickStart().isEmpty());
  }

  @Test
  void help_scopedToExecutePlanPublishesOnlyLedgerPlanShapesAndTemplate() {
    ApplicationIdentity identity =
        new ApplicationIdentity("FinGrind", "0.9.0", "Finance-grade bookkeeping kernel");
    EnvironmentDescriptor environment = ContractFixtures.environmentDescriptor();

    HelpDescriptor help = MachineContract.help(identity, environment, OperationId.EXECUTE_PLAN);

    assertEquals(1, help.commands().size());
    assertEquals(OperationId.EXECUTE_PLAN, help.commands().getFirst().name());
    assertNotNull(help.requestShapes());
    assertNull(Objects.requireNonNull(help.requestShapes()).postEntry());
    assertNull(help.requestShapes().declareAccount());
    assertNotNull(help.requestShapes().ledgerPlan());
    assertNull(help.requestTemplate());
    assertNull(help.declareAccountTemplate());
    assertNotNull(help.planTemplate());
    assertEquals("plan-1", Objects.requireNonNull(help.planTemplate()).planId());
    assertTrue(help.quickStart().isEmpty());
  }

  @Test
  void help_scopedToNonRequestCommandPublishesNoRequestShapesOrTemplates() {
    ApplicationIdentity identity =
        new ApplicationIdentity("FinGrind", "0.9.0", "Finance-grade bookkeeping kernel");
    EnvironmentDescriptor environment = ContractFixtures.environmentDescriptor();

    HelpDescriptor help = MachineContract.help(identity, environment, OperationId.CLOSE_PERIOD);

    assertEquals(1, help.commands().size());
    assertEquals(OperationId.CLOSE_PERIOD, help.commands().getFirst().name());
    assertNull(help.requestShapes());
    assertNull(help.requestTemplate());
    assertNull(help.declareAccountTemplate());
    assertNull(help.planTemplate());
    assertTrue(help.quickStart().isEmpty());
  }

  private static List<String> enumValues(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static Stream<WorkflowStepDescriptor> quickStartSteps(HelpDescriptor help) {
    return help.quickStart().stream().flatMap(workflow -> workflow.steps().stream());
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
    return environmentDescriptorFor(ProtocolCatalog.bundleRuntimeDistribution());
  }

  private static EnvironmentDescriptor environmentDescriptorFor(
      dev.erst.fingrind.contract.protocol.RuntimeDistribution runtimeDistribution) {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            runtimeDistribution,
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            ProtocolCatalog.forbiddenSqliteCompileOptions(),
            ProtocolCatalog.requiresSecureMemorySupport(),
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntimeStatus.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
                "/tmp/libsqlite3.dylib",
                ProtocolCatalog.requiredMinimumSqliteVersion(),
                ProtocolCatalog.requiredSqlite3mcVersion(),
                ProtocolCatalog.requiredSqliteSourceId(),
                null),
            null));
  }
}
