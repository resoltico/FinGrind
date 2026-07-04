package dev.erst.fingrind.contract.discovery;

import static dev.erst.fingrind.contract.discovery.MachineContractDiscoveryTestSupport.IDENTITY;
import static dev.erst.fingrind.contract.discovery.MachineContractDiscoveryTestSupport.environment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes.LedgerPlanRequestShapeDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Coverage and contract tests for the published machine-discovery surfaces. */
class MachineContractDiscoverySurfaceTest {
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
  void launcherNeutralPosixShellSurface_publishesCanonicalQuickStart() {
    QuickStartSteps steps =
        quickStartSteps(MachineContract.quickStart(WorkflowSurface.PATH_POSIX_SHELL));

    assertCommonQuickStartSteps(steps);
    assertPathPosixShellQuickStartSteps(steps);
  }

  @Test
  void helpForOperationTopicsPublishesOnlyRelevantTemplatesAndNoQuickStart() {
    assertPostingTopic(OperationId.POST_ENTRY);
    assertPostingTopic(OperationId.PREFLIGHT_ENTRY);
    assertPostingTopic(OperationId.RECORD_SALE_SETTLED);
    assertPostingTopic(OperationId.RECORD_EXPENSE_SETTLED);
    assertPostingTopic(OperationId.RECORD_OWNER_CONTRIBUTION);
    assertPostingTopic(OperationId.RECORD_OWNER_WITHDRAWAL);
    assertPostingTopic(OperationId.RECORD_OPENING_POSITION);
    assertPostingTopic(OperationId.RECORD_REVERSAL);

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
    assertEquals(MachineContract.protocolVersion(), version.protocolVersion());
    assertEquals(IDENTITY.description(), version.description());
    assertEquals(MachineContract.protocolVersion(), capabilities.protocolVersion());
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
  void capabilitiesRequestInputNotesDescribePdfArtifactPublicationContract() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);

    assertTrue(
        capabilities.requestInput().outputSemantics().stream()
            .anyMatch(
                note ->
                    note.contains(
                            "successful JSON exports publish the normalized artifact path under artifacts[]")
                        && note.contains(
                            "successful text exports replace the full report body with one artifact confirmation block on stdout")
                        && note.contains("--output csv cannot be combined with --pdf-out")));
  }

  @Test
  void
      capabilitiesResponseModelDescribesLiftedPlanOutcomePayloadsWithoutChangingPostingRejections() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);

    ContractResponse.FieldDescriptor rejectionPayload =
        fieldNamed(capabilities.responseModel().rejectionFields(), "payload");
    assertTrue(
        rejectionPayload
            .description()
            .contains(ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)));
    assertTrue(rejectionPayload.description().contains("assertion-failed"));

    ContractResponse.FieldDescriptor errorPayload =
        fieldNamed(capabilities.responseModel().errorFields(), "payload");
    assertTrue(
        errorPayload
            .description()
            .contains(ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)));
    assertTrue(errorPayload.description().contains("rejected"));

    assertTrue(
        capabilities.responseModel().postEntryRejectionFields().stream()
            .noneMatch(field -> "payload".equals(field.name())));
  }

  @Test
  void templateCatalogPublishesCanonicalScaffoldsForEverySupportedSelection() {
    assertTrue(
        MachineContractTemplatesCatalog.declareAccountCashJson().contains("\"cash-reserve\""));
    assertTrue(
        MachineContractTemplatesCatalog.declareAccountRevenueJson().contains("\"misc-revenue\""));
    assertEquals(BookkeepingEntryKind.SALE_SETTLED, MachineContract.requestTemplate().entryKind());
    assertEquals("cash", MachineContract.requestTemplate().cashAccountCode());
    assertNull(MachineContract.requestTemplate().lines());
    assertEquals(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY))
            .entryKind());
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED,
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.PREFLIGHT_ENTRY))
            .entryKind());
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED,
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED))
            .entryKind());
    assertEquals(
        BookkeepingEntryKind.REVERSAL,
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_REVERSAL))
            .entryKind());
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
  void typedPostingTemplateLookupRejectsOperationsOutsideTheTypedEntryFamily() {
    IllegalArgumentException cause =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MachineContractTemplatesCatalog.requiredPostingEntryKind(
                    ProtocolCatalog.operation(OperationId.HELP)));
    assertEquals("Operation help does not own a typed posting template.", cause.getMessage());
  }

  @Test
  void requestSurfaceFactsDrivePostingDiscoverySemantics() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);
    RequestSurfaceFacts requestSurface = ProtocolCatalog.domain().requestSurface();
    var postEntry = capabilities.requestShapes().bookkeepingEntry();
    assertNotNull(postEntry);
    assertEquals(
        requestSurface.bookkeepingEntryEvidence().minimumSourceDocuments(),
        postEntry.evidenceRequirement().minimumSourceDocuments());
    ContractRequestShapes.EntryKindSemanticsDescriptor sale =
        postEntry.entryKindSemantics().stream()
            .filter(descriptor -> descriptor.entryKind() == BookkeepingEntryKind.SALE_SETTLED)
            .findFirst()
            .orElseThrow();
    RequestSurfaceFacts.BookkeepingEntryKindFacts saleFacts =
        requestSurface.bookkeepingEntryKind(BookkeepingEntryKind.SALE_SETTLED);
    assertEquals(saleFacts.requiredTopLevelFields(), sale.requiredTopLevelFields());
    assertEquals(saleFacts.requiredSourceDocumentFields(), sale.requiredSourceDocumentFields());
    assertEquals(saleFacts.sourceDocumentTypes().mode().wireValue(), sale.sourceDocumentTypeMode());
    assertEquals(
        saleFacts.sourceDocumentTypes().acceptedValues(), sale.acceptedSourceDocumentTypes());
    assertEquals(saleFacts.sourceDocumentTypes().semantics(), sale.sourceDocumentTypeSemantics());
    assertEquals(
        saleFacts.sourceDocumentTypes().scaffoldValue(),
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED))
            .evidence()
            .sourceDocuments()
            .getFirst()
            .sourceDocumentType());
    ContractRequestShapes.EntryKindSemanticsDescriptor openingPosition =
        postEntry.entryKindSemantics().stream()
            .filter(descriptor -> descriptor.entryKind() == BookkeepingEntryKind.OPENING_POSITION)
            .findFirst()
            .orElseThrow();
    RequestSurfaceFacts.BookkeepingEntryKindFacts openingFacts =
        requestSurface.bookkeepingEntryKind(BookkeepingEntryKind.OPENING_POSITION);
    assertEquals(
        openingFacts.sourceDocumentTypes().mode().wireValue(),
        openingPosition.sourceDocumentTypeMode());
    assertTrue(openingPosition.acceptedSourceDocumentTypes().isEmpty());
    assertEquals(
        openingFacts.sourceDocumentTypes().scaffoldValue(),
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION))
            .evidence()
            .sourceDocuments()
            .getFirst()
            .sourceDocumentType());
  }

  @Test
  void narrowedBookkeepingEntryShapePublishesSourceDocumentTypePolicyDirectly() {
    HelpDescriptor saleHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.RECORD_SALE_SETTLED);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor saleShape =
        Objects.requireNonNull(Objects.requireNonNull(saleHelp.requestShapes()).bookkeepingEntry());
    RequestSurfaceFacts.BookkeepingEntryKindFacts saleFacts =
        ProtocolCatalog.domain()
            .requestSurface()
            .bookkeepingEntryKind(BookkeepingEntryKind.SALE_SETTLED);
    ContractRequestShapes.RequestFieldDescriptor sourceDocumentTypeField =
        saleShape.sourceDocumentFields().stream()
            .filter(field -> "sourceDocumentType".equals(field.name()))
            .findFirst()
            .orElseThrow();

    assertTrue(sourceDocumentTypeField.description().contains("Accepted values: cash-receipt"));
    assertFalse(sourceDocumentTypeField.description().contains("entryKindSemantics"));
    assertEquals(
        saleFacts.sourceDocumentTypes().acceptedValues(),
        saleShape.enumVocabularies().stream()
            .filter(vocabulary -> "sourceDocumentType".equals(vocabulary.name()))
            .findFirst()
            .orElseThrow()
            .values());
    assertEquals(
        saleFacts.sourceDocumentTypes().acceptedValues(),
        ContractSchemaTestSupport.stringList(
            ContractSchemaTestSupport.requiredValue(
                ContractSchemaTestSupport.sourceDocumentTypeSchema(saleShape), "enum")));

    HelpDescriptor openingPositionHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.RECORD_OPENING_POSITION);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor openingPositionShape =
        Objects.requireNonNull(
            Objects.requireNonNull(openingPositionHelp.requestShapes()).bookkeepingEntry());
    ContractRequestShapes.RequestFieldDescriptor openingSourceDocumentTypeField =
        openingPositionShape.sourceDocumentFields().stream()
            .filter(field -> "sourceDocumentType".equals(field.name()))
            .findFirst()
            .orElseThrow();

    assertTrue(openingSourceDocumentTypeField.description().contains("caller-authored"));
    assertFalse(openingSourceDocumentTypeField.description().contains("entryKindSemantics"));
    assertTrue(
        openingPositionShape.enumVocabularies().stream()
            .noneMatch(vocabulary -> "sourceDocumentType".equals(vocabulary.name())));
    assertNull(
        ContractSchemaTestSupport.sourceDocumentTypeSchema(openingPositionShape).get("enum"));
  }

  @Test
  void executePlanHelpPublishesExactlyOneTopLevelShapeAndAContractOwnedNestedPostingModel() {
    HelpDescriptor executePlanHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.EXECUTE_PLAN);

    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        Objects.requireNonNull(executePlanHelp.requestShapes());
    assertNull(requestShapes.bookkeepingEntry());
    assertNull(requestShapes.declareAccount());
    assertNotNull(requestShapes.ledgerPlan());
    assertNotNull(executePlanHelp.planTemplate());
    assertEquals(
        dev.erst.fingrind.core.BookkeepingEntryKind.SALE_SETTLED,
        Objects.requireNonNull(executePlanHelp.planTemplate())
            .canonicalPostingTemplate()
            .entryKind());
    assertTrue(
        recordComponentNames(LedgerPlanRequestShapeDescriptor.class).contains("postingModel"),
        () ->
            "Expected ledger-plan request discovery to own one nested postingModel descriptor for execute-plan help.");
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
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntry =
        Objects.requireNonNull(help.requestShapes()).bookkeepingEntry();
    assertNotNull(postEntry);
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      assertEquals(BookkeepingEntryKind.values().length, postEntry.entryKindSemantics().size());
      return;
    }
    if (operationId == OperationId.POST_ENTRY) {
      assertEquals(
          List.of(BookkeepingEntryKind.DIRECT_JOURNAL),
          postEntry.entryKindSemantics().stream()
              .map(ContractRequestShapes.EntryKindSemanticsDescriptor::entryKind)
              .toList());
      return;
    }
    BookkeepingEntryKind expectedEntryKind =
        ProtocolPostingRequestTopics.requiredEntryKind(operationId)
            .orElseThrow(() -> new AssertionError("Unexpected posting topic " + operationId));
    assertEquals(
        List.of(expectedEntryKind),
        postEntry.entryKindSemantics().stream()
            .map(ContractRequestShapes.EntryKindSemanticsDescriptor::entryKind)
            .toList());
    assertEquals(
        List.of(expectedEntryKind.wireValue()),
        postEntry.enumVocabularies().stream()
            .filter(vocabulary -> "entryKind".equals(vocabulary.name()))
            .findFirst()
            .orElseThrow()
            .values());
  }

  private static void assertExitCodes(OperationId operationId, List<Integer> expectedExitCodes) {
    HelpDescriptor help =
        MachineContract.help(
            IDENTITY, environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE), operationId);

    assertEquals(
        expectedExitCodes, help.exitCodes().stream().map(exitCode -> exitCode.code()).toList());
  }

  private static ContractResponse.FieldDescriptor fieldNamed(
      List<ContractResponse.FieldDescriptor> fields, String fieldName) {
    return fields.stream()
        .filter(field -> fieldName.equals(field.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing response-model field " + fieldName));
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
    assertTrue(steps.openBook().text().contains("--book-template-id OWNER_MANAGED_SERVICE"));
    assertTrue(steps.openBook().text().contains("--accounting-basis CASH"));
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
        steps
            .postEntry()
            .text()
            .contains(ProtocolCatalog.operationName(OperationId.RECORD_SALE_SETTLED)));
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
      case PATH_POSIX_SHELL -> assertPathPosixShellQuickStartSteps(steps);
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

  private static void assertPathPosixShellQuickStartSteps(QuickStartSteps steps) {
    assertTrue(
        steps
            .generateKey()
            .text()
            .startsWith("fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key"));
    assertTrue(steps.introNote().text().contains("fingrind is already on PATH"));
    assertPlaceholderRequestPreparation(steps);
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

  private static List<String> recordComponentNames(Class<?> recordType) {
    return Stream.of(recordType.getRecordComponents())
        .map(component -> component.getName())
        .toList();
  }
}
