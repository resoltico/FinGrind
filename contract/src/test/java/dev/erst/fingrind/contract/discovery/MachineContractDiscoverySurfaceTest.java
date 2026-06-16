package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Coverage and contract tests for the published machine-discovery surfaces. */
class MachineContractDiscoverySurfaceTest {
  private static final ApplicationIdentity IDENTITY =
      new ApplicationIdentity("FinGrind", "0.55.0", "Protected bookkeeping kernel");

  @Test
  void helpWithoutTopicPublishesCanonicalQuickStartForEveryRuntimeDistribution() {
    assertQuickStartSurface(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE, List.of(WorkflowSurface.BUNDLE_POSIX_SHELL));
    assertQuickStartSurface(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE,
        List.of(
            WorkflowSurface.SOURCE_CHECKOUT_POSIX_SHELL,
            WorkflowSurface.SOURCE_CHECKOUT_WINDOWS_POWERSHELL));
    assertQuickStartSurface(
        RuntimeDistribution.DIRECT_JAVA_INVOCATION,
        List.of(
            WorkflowSurface.DIRECT_JAVA_POSIX_SHELL,
            WorkflowSurface.DIRECT_JAVA_WINDOWS_POWERSHELL));
    assertQuickStartSurface(
        RuntimeDistribution.CONTAINER_IMAGE, List.of(WorkflowSurface.CONTAINER_DOCKER));
  }

  @Test
  void helpForOperationTopicsPublishesOnlyRelevantTemplatesAndNoQuickStart() {
    assertPostingTopic(OperationId.POST_ENTRY);
    assertPostingTopic(OperationId.PREFLIGHT_ENTRY);

    HelpDescriptor declareAccountHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.DECLARE_ACCOUNT);
    assertNotNull(declareAccountHelp.requestShapes());
    assertNull(declareAccountHelp.requestTemplate());
    assertNotNull(declareAccountHelp.declareAccountTemplate());
    assertNull(declareAccountHelp.planTemplate());
    assertTrue(declareAccountHelp.quickStart().isEmpty());

    HelpDescriptor executePlanHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.EXECUTE_PLAN);
    assertNotNull(executePlanHelp.requestShapes());
    assertNull(executePlanHelp.requestTemplate());
    assertNull(executePlanHelp.declareAccountTemplate());
    assertNotNull(executePlanHelp.planTemplate());
    assertTrue(executePlanHelp.quickStart().isEmpty());

    HelpDescriptor helpTopic =
        MachineContract.help(
            IDENTITY, environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE), OperationId.HELP);
    assertNull(helpTopic.requestShapes());
    assertNull(helpTopic.requestTemplate());
    assertNull(helpTopic.declareAccountTemplate());
    assertNull(helpTopic.planTemplate());
    assertTrue(helpTopic.quickStart().isEmpty());
    assertEquals(List.of(ProtocolCatalog.operation(OperationId.HELP).usage()), helpTopic.usage());
  }

  @Test
  void capabilitiesVersionAndDomainDescriptorsPublishCanonicalContractFacts() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);
    VersionDescriptor version = MachineContract.version(IDENTITY);

    assertEquals(IDENTITY.application(), version.application());
    assertEquals(IDENTITY.version(), version.version());
    assertEquals(IDENTITY.description(), version.description());
    assertEquals("single-sqlite-file", capabilities.storage().bookBoundary());
    assertEquals(
        ProtocolCatalog.operations().size(),
        MachineContractDomainDescriptors.commandDescriptors().size());
    assertEquals(6, MachineContractDomainDescriptors.commandCatalog().discovery().size());
    assertEquals(6, MachineContractDomainDescriptors.audit().requestProvenanceFields().size());
    assertEquals(2, MachineContractDomainDescriptors.audit().committedFields().size());
    assertEquals(9, MachineContractDomainDescriptors.exitCodes().size());
    assertEquals("reversal-only", MachineContractDomainDescriptors.reversals().model());
    assertEquals(
        ProtocolCatalog.domain().currency().scope(),
        MachineContractDomainDescriptors.currencyModel().scope());
    assertEquals(
        ProtocolCatalog.domain().planExecution().journal(),
        MachineContractDomainDescriptors.planExecution().journal());
    assertEquals(
        ProtocolCatalog.domain().bookModel().boundary(),
        MachineContractDomainDescriptors.bookModel().boundary());
    assertEquals(
        ProtocolCatalog.domain().bookkeepingKernel().scope(),
        MachineContractDomainDescriptors.bookkeepingKernel().scope());
    assertEquals(
        ProtocolCatalog.domain().preflight().semantics(),
        MachineContractDomainDescriptors.preflight().semantics());
    assertTrue(
        MachineContractDomainDescriptors.accountRegistry().enumVocabularies().stream()
            .anyMatch(vocabulary -> "accountType".equals(vocabulary.name())));
  }

  @Test
  void templateCatalogPublishesCanonicalScaffoldsForEverySupportedSelection() {
    assertTrue(
        MachineContractTemplatesCatalog.declareAccountCashJson().contains("\"cash-reserve\""));
    assertTrue(
        MachineContractTemplatesCatalog.declareAccountRevenueJson().contains("\"misc-revenue\""));
    assertEquals(BookkeepingEntryKind.CASH_REVENUE, MachineContract.requestTemplate().entryKind());
    assertEquals("cash-reserve", MachineContract.declareAccountTemplate().accountCode());
    assertEquals("plan-1", MachineContract.planTemplate().planId());
    assertNull(MachineContractTemplatesCatalog.requestShapesFor(null));
    assertNull(
        MachineContractTemplatesCatalog.requestShapesFor(
            ProtocolCatalog.operation(OperationId.HELP)));
    assertNotNull(
        MachineContractTemplatesCatalog.requestShapesFor(
            ProtocolCatalog.operation(OperationId.POST_ENTRY)));
    assertNotNull(
        MachineContractTemplatesCatalog.requestShapesFor(
            ProtocolCatalog.operation(OperationId.DECLARE_ACCOUNT)));
    assertNotNull(
        MachineContractTemplatesCatalog.requestShapesFor(
            ProtocolCatalog.operation(OperationId.EXECUTE_PLAN)));
  }

  @Test
  void commandTopicsPublishTheExpectedExitCodeFamilies() {
    assertExitCodes(OperationId.PRINT_REQUEST_TEMPLATE, List.of(0, 1, 70));
    assertExitCodes(OperationId.HELP, List.of(0, 1, 2, 70));
    assertExitCodes(OperationId.GENERATE_BOOK_KEY_FILE, List.of(0, 1, 2, 6, 7, 70));
    assertExitCodes(OperationId.BACKUP_BOOK, List.of(0, 1, 2, 4, 5, 6, 7, 70));
    assertExitCodes(OperationId.EXECUTE_PLAN, List.of(0, 1, 2, 3, 4, 5, 6, 70));
    assertExitCodes(OperationId.OPEN_BOOK, List.of(0, 1, 2, 4, 5, 6, 70));
  }

  private static void assertPostingTopic(OperationId operationId) {
    HelpDescriptor help =
        MachineContract.help(
            IDENTITY, environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE), operationId);

    assertNotNull(help.requestShapes());
    assertNotNull(help.requestTemplate());
    assertNull(help.declareAccountTemplate());
    assertNull(help.planTemplate());
    assertTrue(help.quickStart().isEmpty());
    assertEquals(List.of(ProtocolCatalog.operation(operationId).usage()), help.usage());
    assertEquals(
        List.of(ProtocolCatalog.operation(operationId).id()),
        help.commands().stream().map(CommandDescriptor::name).toList());
  }

  private static void assertExitCodes(OperationId operationId, List<Integer> expectedExitCodes) {
    HelpDescriptor help =
        MachineContract.help(
            IDENTITY, environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE), operationId);

    assertEquals(
        expectedExitCodes, help.exitCodes().stream().map(exitCode -> exitCode.code()).toList());
  }

  private static void assertQuickStartSurface(
      RuntimeDistribution runtimeDistribution, List<WorkflowSurface> expectedSurfaces) {
    HelpDescriptor help = MachineContract.help(IDENTITY, environment(runtimeDistribution));

    assertEquals(ProtocolCatalog.operations().size(), help.commands().size());
    assertEquals(ProtocolCatalog.operations().size(), help.usage().size());
    assertEquals(
        expectedSurfaces, help.quickStart().stream().map(WorkflowDescriptor::surface).toList());

    for (WorkflowDescriptor workflow : help.quickStart()) {
      QuickStartSteps steps = quickStartSteps(workflow);
      assertCommonQuickStartSteps(steps);
      assertSurfaceSpecificQuickStartSteps(workflow.surface(), steps);
    }
  }

  private static QuickStartSteps quickStartSteps(WorkflowDescriptor workflow) {
    assertEquals(10, workflow.steps().size());
    return new QuickStartSteps(
        assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(0)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(1)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(2)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(3)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(4)),
        assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(5)),
        assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(6)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(7)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(8)),
        assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(9)));
  }

  private static void assertCommonQuickStartSteps(QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .contains(ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE)));
    assertTrue(steps.openBook().text().contains("--entity-name \"Acme Studio\""));
    assertTrue(
        steps
            .listAccounts()
            .text()
            .contains(ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS)));
    assertTrue(steps.idempotencyNote().text().contains("idempotencyKey"));
    assertTrue(
        steps
            .preflight()
            .text()
            .contains(ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY)));
    assertTrue(
        steps.postEntry().text().contains(ProtocolCatalog.operationName(OperationId.POST_ENTRY)));
    assertTrue(
        steps
            .trialBalance()
            .text()
            .contains(ProtocolCatalog.operationName(OperationId.TRIAL_BALANCE) + " --book-file"));
    assertTrue(steps.trialBalance().text().endsWith("--output text"));
  }

  private static void assertSurfaceSpecificQuickStartSteps(
      WorkflowSurface surface, QuickStartSteps steps) {
    switch (surface) {
      case BUNDLE_POSIX_SHELL -> assertBundlePosixQuickStartSteps(steps);
      case SOURCE_CHECKOUT_POSIX_SHELL ->
          assertSourceCheckoutQuickStartSteps(false, "./secrets/acme.book-key", steps);
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
          assertSourceCheckoutQuickStartSteps(true, ".\\secrets\\acme.book-key", steps);
      case DIRECT_JAVA_POSIX_SHELL ->
          assertDirectJavaQuickStartSteps(false, "./secrets/acme.book-key", steps);
      case DIRECT_JAVA_WINDOWS_POWERSHELL ->
          assertDirectJavaQuickStartSteps(true, ".\\secrets\\acme.book-key", steps);
      case CONTAINER_DOCKER -> assertContainerQuickStartSteps(steps);
    }
  }

  private static void assertBundlePosixQuickStartSteps(QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .startsWith(
                ProtocolCatalog.distribution()
                        .bundleLauncherCommand(PublicCliBundleTarget.LINUX_X86_64)
                    + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
    assertTrue(steps.openBook().text().contains("--book-file ./books/acme.sqlite"));
    assertEquals(
        "cp ./quick-start-request.json ./request.json", steps.requestPreparationCommand().text());
    assertTrue(steps.requestPreparationNote().text().contains("concrete sample document"));
    assertTrue(steps.requestPreparationNote().text().contains("./request.json"));
  }

  private static void assertSourceCheckoutQuickStartSteps(
      boolean windows, String bookKeyPath, QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .startsWith(
                ProtocolCatalog.distribution().sourceCheckoutLauncherCommand(windows)
                    + " generate-book-key-file --book-key-file "
                    + bookKeyPath));
    assertRuntimeManagedQuickStartSteps(steps);
  }

  private static void assertDirectJavaQuickStartSteps(
      boolean windows, String bookKeyPath, QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .startsWith(
                ProtocolCatalog.distribution().directJavaLauncherCommand(windows)
                    + " generate-book-key-file --book-key-file "
                    + bookKeyPath));
    assertRuntimeManagedQuickStartSteps(steps);
  }

  private static void assertContainerQuickStartSteps(QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .startsWith(
                ProtocolCatalog.distribution().containerLauncherCommand()
                    + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
    assertTrue(steps.introNote().text().contains("session-local fingrind wrapper"));
    assertPlaceholderRequestPreparation(steps);
  }

  private static void assertRuntimeManagedQuickStartSteps(QuickStartSteps steps) {
    assertTrue(
        steps.introNote().text().contains("Gradle-owned Java 26 toolchain manifest automatically"));
    assertPlaceholderRequestPreparation(steps);
  }

  private static void assertPlaceholderRequestPreparation(QuickStartSteps steps) {
    assertTrue(steps.requestPreparationCommand().text().contains("print-request-template >"));
    assertTrue(steps.requestPreparationNote().text().contains("placeholder-first scaffold"));
  }

  private static EnvironmentDescriptor environment(RuntimeDistribution runtimeDistribution) {
    return new EnvironmentDescriptor(
        new EnvironmentRuntimeDescriptor(runtimeDistribution, OutputMode.TEXT, null),
        new EnvironmentPublicationDescriptor(
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
            activeBundleTarget(runtimeDistribution),
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.runtime().storageDriver(),
            ProtocolCatalog.runtime().storageEngine(),
            ProtocolCatalog.runtime().bookProtectionMode(),
            ProtocolCatalog.runtime().protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.runtime().sqliteLibraryMode(),
            ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.managedSqlite().requiredCompileOptions(),
            ProtocolCatalog.managedSqlite().forbiddenCompileOptions(),
            ProtocolCatalog.managedSqlite().requiresSecureMemorySupport(),
            ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
            ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                "test fixture"),
            null));
  }

  private record QuickStartSteps(
      WorkflowStepDescriptor.Note introNote,
      WorkflowStepDescriptor.Command generateKey,
      WorkflowStepDescriptor.Command openBook,
      WorkflowStepDescriptor.Command listAccounts,
      WorkflowStepDescriptor.Command requestPreparationCommand,
      WorkflowStepDescriptor.Note requestPreparationNote,
      WorkflowStepDescriptor.Note idempotencyNote,
      WorkflowStepDescriptor.Command preflight,
      WorkflowStepDescriptor.Command postEntry,
      WorkflowStepDescriptor.Command trialBalance) {}

  private static @Nullable PublicCliBundleTarget activeBundleTarget(
      RuntimeDistribution runtimeDistribution) {
    return runtimeDistribution == RuntimeDistribution.SELF_CONTAINED_BUNDLE
        ? PublicCliBundleTarget.LINUX_X86_64
        : null;
  }
}
