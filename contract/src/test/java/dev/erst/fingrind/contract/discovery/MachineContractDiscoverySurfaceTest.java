package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage and contract tests for the published machine-discovery surfaces. */
class MachineContractDiscoverySurfaceTest {
  private static final ApplicationIdentity IDENTITY =
      new ApplicationIdentity("FinGrind", "0.51.0", "Protected bookkeeping kernel");

  @Test
  void helpWithoutTopicPublishesCanonicalQuickStartForEveryRuntimeDistribution() {
    assertQuickStartSurface(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE,
        List.of(WorkflowSurface.BUNDLE_POSIX_SHELL, WorkflowSurface.BUNDLE_WINDOWS_POWERSHELL));
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
    assertEquals(8, MachineContractDomainDescriptors.exitCodes().size());
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

  private static void assertQuickStartSurface(
      RuntimeDistribution runtimeDistribution, List<WorkflowSurface> expectedSurfaces) {
    HelpDescriptor help = MachineContract.help(IDENTITY, environment(runtimeDistribution));

    assertEquals(ProtocolCatalog.operations().size(), help.commands().size());
    assertEquals(ProtocolCatalog.operations().size(), help.usage().size());
    assertEquals(
        expectedSurfaces, help.quickStart().stream().map(WorkflowDescriptor::surface).toList());

    for (WorkflowDescriptor workflow : help.quickStart()) {
      assertEquals(10, workflow.steps().size());
      assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(0));
      WorkflowStepDescriptor.Command generateKey =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(1));
      WorkflowStepDescriptor.Command openBook =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(2));
      WorkflowStepDescriptor.Command listAccounts =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(3));
      WorkflowStepDescriptor.Command printRequestTemplate =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(4));
      WorkflowStepDescriptor.Note placeholderNote =
          assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(5));
      WorkflowStepDescriptor.Note idempotencyNote =
          assertInstanceOf(WorkflowStepDescriptor.Note.class, workflow.steps().get(6));
      WorkflowStepDescriptor.Command preflight =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(7));
      WorkflowStepDescriptor.Command postEntry =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(8));
      WorkflowStepDescriptor.Command trialBalance =
          assertInstanceOf(WorkflowStepDescriptor.Command.class, workflow.steps().get(9));

      assertTrue(
          generateKey
              .text()
              .contains(ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE)));
      assertTrue(openBook.text().contains("--entity-name \"Acme Studio\""));
      assertTrue(
          listAccounts.text().contains(ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS)));
      assertTrue(printRequestTemplate.text().contains("print-request-template >"));
      assertTrue(placeholderNote.text().contains("placeholder-first scaffold"));
      assertTrue(idempotencyNote.text().contains("idempotencyKey"));
      assertTrue(
          preflight.text().contains(ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY)));
      assertTrue(postEntry.text().contains(ProtocolCatalog.operationName(OperationId.POST_ENTRY)));
      assertTrue(
          trialBalance
              .text()
              .contains(ProtocolCatalog.operationName(OperationId.TRIAL_BALANCE) + " --book-file"));
      assertTrue(trialBalance.text().endsWith("--output text"));

      switch (workflow.surface()) {
        case BUNDLE_POSIX_SHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution()
                              .bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64)
                          + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
          assertTrue(openBook.text().contains("--book-file ./books/acme.sqlite"));
        }
        case BUNDLE_WINDOWS_POWERSHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution()
                              .bundleLauncherCommand(PublicCliBundleTarget.WINDOWS_X86_64)
                          + " generate-book-key-file --book-key-file .\\secrets\\acme.book-key"));
          assertTrue(openBook.text().contains("--book-file .\\books\\acme.sqlite"));
        }
        case SOURCE_CHECKOUT_POSIX_SHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution().sourceCheckoutLauncherCommand(false)
                          + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
          assertTrue(
              ((WorkflowStepDescriptor.Note) workflow.steps().get(0))
                  .text()
                  .contains("./gradlew :cli:installShadowDist prepareManagedSqlite"));
        }
        case SOURCE_CHECKOUT_WINDOWS_POWERSHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution().sourceCheckoutLauncherCommand(true)
                          + " generate-book-key-file --book-key-file .\\secrets\\acme.book-key"));
          assertTrue(
              ((WorkflowStepDescriptor.Note) workflow.steps().get(0))
                  .text()
                  .contains(".\\gradlew.bat :cli:installShadowDist prepareManagedSqlite"));
        }
        case DIRECT_JAVA_POSIX_SHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution().directJavaLauncherCommand(false)
                          + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
          assertTrue(
              ((WorkflowStepDescriptor.Note) workflow.steps().get(0))
                  .text()
                  .contains("./gradlew :cli:shadowJar prepareManagedSqlite"));
        }
        case DIRECT_JAVA_WINDOWS_POWERSHELL -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution().directJavaLauncherCommand(true)
                          + " generate-book-key-file --book-key-file .\\secrets\\acme.book-key"));
          assertTrue(
              ((WorkflowStepDescriptor.Note) workflow.steps().get(0))
                  .text()
                  .contains(".\\gradlew.bat :cli:shadowJar prepareManagedSqlite"));
        }
        case CONTAINER_DOCKER -> {
          assertTrue(
              generateKey
                  .text()
                  .startsWith(
                      ProtocolCatalog.distribution().containerLauncherCommand()
                          + " generate-book-key-file --book-key-file ./secrets/acme.book-key"));
          assertTrue(
              ((WorkflowStepDescriptor.Note) workflow.steps().get(0))
                  .text()
                  .contains("<container-image>"));
        }
      }
    }
  }

  private static EnvironmentDescriptor environment(RuntimeDistribution runtimeDistribution) {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            runtimeDistribution,
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
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
}
