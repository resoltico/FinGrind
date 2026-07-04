package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Discovery help and front-door command tests for {@link FinGrindCli}. */
class FinGrindCliDiscoveryHelpCommandTest extends FinGrindCliDiscoveryHelpCommandTestSupport {
  @Test
  void run_rendersTextHelpWhenExplicitlyRequested() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("FinGrind Help"));
    assertTrue(help.contains("open-book"));
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("list-accounts"));
    assertTrue(help.contains("Command Catalog"));
    assertTrue(help.contains("Quick Start"));
    assertTrue(help.contains("Generate a key file"));
    assertFalse(help.contains("Generate one key file"));
    assertTrue(help.contains("Review the seeded accounts"));
    assertTrue(help.contains("Create the first settled-sale request"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(dev.erst.fingrind.contract.protocol.OperationId.HELP)
                + " <command>"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(
                dev.erst.fingrind.contract.protocol.OperationId.PRINT_REQUEST_TEMPLATE)));
    assertFalse(help.contains("Guidance"));
    assertFalse(help.contains("declare-account-supplemental-cash-reserve.json"));
  }

  @Test
  void run_returnsScopedHelpForExplicitHelpTopic() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "post-entry", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("Examples"));
    assertTrue(help.contains("Preparation"));
    assertTrue(help.contains("Input Contract"));
    assertTrue(help.contains("Grammar"));
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("--request-file <path|->"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(
                    dev.erst.fingrind.contract.protocol.OperationId.PRINT_REQUEST_TEMPLATE)
                + " post-entry > request.json"));
    assertTrue(help.contains("Output Contract"));
  }

  @Test
  void run_postEntryHelpPublishesPostingModelForDirectPostingSurface() {
    String help = runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY);

    assertTrue(help.contains("Posting model"), help);
    assertTrue(help.contains("DIRECT_JOURNAL"), help);
    assertTrue(containsCollapsedText(help, "Canonical scaffold value: DIRECT_JOURNAL."), help);
    assertTrue(help.contains("lines"), help);
    assertTrue(help.contains("side"), help);
    assertFalse(help.contains("SALE"), help);
    assertFalse(help.contains("cashAccountCode"), help);
    assertTrue(help.contains("Input Contract"), help);
  }

  @Test
  void run_recordSaleHelpPublishesOnlySalePostingFields() {
    String help =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.RECORD_SALE_SETTLED);

    assertTrue(help.contains("Posting model"), help);
    assertTrue(containsCollapsedText(help, "Canonical scaffold value: SALE_SETTLED."), help);
    assertTrue(help.contains("cashAccountCode"), help);
    assertTrue(help.contains("revenueAccountCode"), help);
    assertTrue(help.contains("amount"), help);
    assertTrue(help.contains("inventoryRelief"), help);
    assertTrue(
        containsCollapsedText(
            help,
            "Trading-template sale requests require this object so one committed sale can carry both revenue recognition and cost-of-sales relief."),
        help);
    assertTrue(help.contains("sourceDocumentType"), help);
    assertTrue(
        containsCollapsedText(
            help, "Accepted values: cash-receipt, bank-deposit, card-settlement."),
        help);
    assertFalse(help.contains("expenseAccountCode"), help);
    assertFalse(help.contains("equityAccountCode"), help);
    assertFalse(help.contains("lines[].accountCode"), help);
    assertFalse(help.contains("openingBalances[].accountCode"), help);
    assertFalse(help.contains("reversal.priorPostingId"), help);
  }

  @Test
  void run_executePlanHelpPublishesLedgerPlanStructureInsteadOfFlatPostingSection() {
    String help = runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.EXECUTE_PLAN);

    HelpDescriptor executePlanHelpDescriptor =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            dev.erst.fingrind.contract.protocol.OperationId.EXECUTE_PLAN);
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        Objects.requireNonNull(
            Objects.requireNonNull(executePlanHelpDescriptor.requestShapes()).ledgerPlan());

    for (ContractRequestShapes.RequestFieldDescriptor topLevelField :
        ledgerPlanShape.topLevelFields()) {
      String renderedFieldName =
          "steps".equals(topLevelField.name()) ? "steps[]" : topLevelField.name();
      assertTrue(help.contains(renderedFieldName), help);
    }
    for (ContractRequestShapes.RequestFieldDescriptor stepField : ledgerPlanShape.stepFields()) {
      assertTrue(help.contains("steps[]." + stepField.name()), help);
    }
    for (ContractRequestShapes.RequestFieldDescriptor queryField : ledgerPlanShape.queryFields()) {
      assertTrue(help.contains("steps[].query." + queryField.name()), help);
    }
    for (ContractRequestShapes.RequestFieldDescriptor assertionField :
        ledgerPlanShape.assertionFields()) {
      assertTrue(help.contains("steps[].assertion." + assertionField.name()), help);
    }
    assertContainsNestedPostingModelPaths(help, ledgerPlanShape.postingModel());
    assertFalse(help.contains("Posting model\n-------------\nentryKind"), help);

    String postEntryHelp =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY);
    assertTrue(postEntryHelp.contains("Posting model"), postEntryHelp);
    assertTrue(postEntryHelp.contains("entryKind"), postEntryHelp);

    String preflightHelp =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.PREFLIGHT_ENTRY);
    assertTrue(preflightHelp.contains("Posting model"), preflightHelp);
    assertTrue(preflightHelp.contains("entryKind"), preflightHelp);

    String declareAccountHelp =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.DECLARE_ACCOUNT);
    assertFalse(declareAccountHelp.contains("Posting model"), declareAccountHelp);
  }

  @Test
  void run_helpSupportRendersCommandsAsLiteralShellBlocksAndLeavesNotesTabular() {
    for (dev.erst.fingrind.contract.protocol.ProtocolOperation operation :
        dev.erst.fingrind.contract.protocol.ProtocolCatalog.operations()) {
      String help = runCommandHelpText(operation.id());
      assertContainsShellCommandBlock(
          help,
          CliInvocationText.commandExample(dev.erst.fingrind.contract.protocol.OperationId.HELP)
              + " "
              + operation.id().wireName());
      assertContainsShellCommandBlock(
          help,
          CliInvocationText.commandExample(dev.erst.fingrind.contract.protocol.OperationId.HELP)
              + " "
              + operation.id().wireName()
              + " --output json --detail full");
      assertFalse(help.contains("Command help     :"), help);
      assertFalse(help.contains("Machine contract :"), help);
      assertFalse(help.contains("--detail\n"), help);

      Optional<String> expectedTemplateCommand =
          expectedRequestTemplateSupportCommand(operation.id());
      if (expectedTemplateCommand.isPresent()) {
        assertContainsShellCommandBlock(help, expectedTemplateCommand.orElseThrow());
        assertFalse(help.contains("Request template :"), help);
      } else {
        assertTrue(help.contains("Request template : (not applicable)"), help);
        assertFalse(help.contains("$ (not applicable)"), help);
      }
    }
  }

  @Test
  void run_declareAccountHelpDoesNotPublishPostingModel() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "declare-account", "--output", "text"});

    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertFalse(help.contains("Posting model"), help);
  }

  @Test
  void run_declareTaxRegistrationHelpPublishesStarterRequestGuidanceWithoutPostingModel() {
    String help =
        runCommandHelpText(
            dev.erst.fingrind.contract.protocol.OperationId.DECLARE_TAX_REGISTRATION);

    assertTrue(help.contains("Input Contract"), help);
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(
                    dev.erst.fingrind.contract.protocol.OperationId.PRINT_REQUEST_TEMPLATE)
                + " declare-tax-registration"),
        help);
    assertFalse(help.contains("Posting model"), help);
  }

  @Test
  void run_executePlanHelpFullJsonPublishesNestedPostingModelWithoutTopLevelPostingLeak()
      throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(new String[] {"help", "execute-plan", "--output", "json", "--detail", "full"});

    assertEquals(0, exitCode);
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    JsonNode requestShapes = payload.path("requestFile").path("requestShapes");
    assertTrue(requestShapes.isObject(), payload.toPrettyString());
    assertTrue(
        requestShapes.path("bookkeepingEntry").isMissingNode(), requestShapes.toPrettyString());
    assertTrue(
        requestShapes.path("declareAccount").isMissingNode(), requestShapes.toPrettyString());
    JsonNode ledgerPlan = requestShapes.path("ledgerPlan");
    assertTrue(ledgerPlan.isObject(), requestShapes.toPrettyString());
    JsonNode postingModel = ledgerPlan.path("postingModel");
    assertTrue(postingModel.isObject(), ledgerPlan.toPrettyString());
    assertTrue(
        hasNamedField(postingModel.path("topLevelFields"), "lines"), postingModel.toPrettyString());
    assertTrue(
        hasNamedField(postingModel.path("topLevelFields"), "cashAccountCode"),
        postingModel.toPrettyString());
    assertTrue(
        hasNamedField(postingModel.path("lineFields"), "side"), postingModel.toPrettyString());
  }

  @Test
  void run_textHelpOmitsForbiddenPostingFieldsWhileFullJsonRetainsForbiddenPresence()
      throws Exception {
    String postEntryTextHelp =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY);
    assertFalse(postEntryTextHelp.contains("provenance.recordedAt"), postEntryTextHelp);
    assertFalse(postEntryTextHelp.contains("provenance.sourceChannel"), postEntryTextHelp);
    assertFalse(postEntryTextHelp.contains("reversal.kind"), postEntryTextHelp);

    String executePlanTextHelp =
        runCommandHelpText(dev.erst.fingrind.contract.protocol.OperationId.EXECUTE_PLAN);
    assertFalse(
        executePlanTextHelp.contains("steps[].posting.provenance.recordedAt"), executePlanTextHelp);
    assertFalse(
        executePlanTextHelp.contains("steps[].posting.provenance.sourceChannel"),
        executePlanTextHelp);
    assertFalse(executePlanTextHelp.contains("steps[].posting.reversal.kind"), executePlanTextHelp);

    JsonNode postEntryRequestShape =
        runCommandHelpPayloadJson(dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY)
            .path("requestFile")
            .path("requestShapes")
            .path("bookkeepingEntry");
    assertForbiddenPresence(postEntryRequestShape.path("provenanceFields"), "recordedAt");
    assertForbiddenPresence(postEntryRequestShape.path("provenanceFields"), "sourceChannel");
    assertForbiddenPresence(postEntryRequestShape.path("reversalFields"), "kind");

    JsonNode executePlanPostingModel =
        runCommandHelpPayloadJson(dev.erst.fingrind.contract.protocol.OperationId.EXECUTE_PLAN)
            .path("requestFile")
            .path("requestShapes")
            .path("ledgerPlan")
            .path("postingModel");
    assertForbiddenPresence(executePlanPostingModel.path("provenanceFields"), "recordedAt");
    assertForbiddenPresence(executePlanPostingModel.path("provenanceFields"), "sourceChannel");
    assertForbiddenPresence(executePlanPostingModel.path("reversalFields"), "kind");
  }

  @Test
  void run_recordSaleHelpFullJsonPublishesConditionalInventoryRelief() {
    JsonNode postingShape =
        runCommandHelpPayloadJson(
                dev.erst.fingrind.contract.protocol.OperationId.RECORD_SALE_SETTLED)
            .path("requestFile")
            .path("requestShapes")
            .path("bookkeepingEntry");

    JsonNode inventoryRelief =
        descriptorByName(postingShape.path("topLevelFields"), "inventoryRelief");

    assertEquals(
        RequestFieldPresence.CONDITIONAL.wireValue(),
        inventoryRelief.path("presence").stringValue());
    assertTrue(
        inventoryRelief
            .path("description")
            .stringValue()
            .contains("Trading-template sale requests require this object"),
        inventoryRelief.toPrettyString());
  }

  @Test
  void renderRequestGuidance_rejectsMissingPostingModelFieldDescriptors() {
    HelpDescriptor baseHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY);
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        Objects.requireNonNull(baseHelp.requestShapes());
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        Objects.requireNonNull(requestShapes.bookkeepingEntry());
    HelpDescriptor mutatedHelp =
        new HelpDescriptor(
            baseHelp.application(),
            baseHelp.version(),
            baseHelp.protocolVersion(),
            baseHelp.description(),
            baseHelp.usage(),
            baseHelp.bookModel(),
            baseHelp.bookkeepingKernel(),
            new ContractRequestShapes.RequestShapesDescriptor(
                requestShapes.schemaDialect(),
                new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
                    postEntryShape.topLevelFields().stream()
                        .filter(field -> !"entryKind".equals(field.name()))
                        .toList(),
                    postEntryShape.lineFields(),
                    postEntryShape.openingBalanceFields(),
                    postEntryShape.foreignExchangeFields(),
                    postEntryShape.quotedRateFields(),
                    postEntryShape.taxFields(),
                    postEntryShape.evidenceFields(),
                    postEntryShape.sourceDocumentFields(),
                    postEntryShape.approvalFields(),
                    postEntryShape.provenanceFields(),
                    postEntryShape.reversalFields(),
                    postEntryShape.entryKindSemantics(),
                    postEntryShape.reachabilityMatrix(),
                    postEntryShape.evidenceRequirement(),
                    postEntryShape.enumVocabularies(),
                    postEntryShape.schema()),
                requestShapes.declareAccount(),
                requestShapes.declareTaxRegistration(),
                requestShapes.ledgerPlan()),
            baseHelp.requestTemplate(),
            baseHelp.declareAccountTemplate(),
            baseHelp.declareTaxRegistrationTemplate(),
            baseHelp.planTemplate(),
            baseHelp.commands(),
            baseHelp.quickStart(),
            baseHelp.exitCodes(),
            baseHelp.preflight(),
            baseHelp.currencyModel());

    IllegalStateException missingField =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliDiscoveryCommandGuidance.renderRequestGuidance(
                    mutatedHelp, dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY));

    assertTrue(
        Objects.requireNonNull(missingField.getMessage())
            .contains("posting-request field 'entryKind'"));
  }

  @Test
  void run_defaultsDiscoveryCommandsToTextUnlessJsonIsExplicitlyRequested() {
    ByteArrayOutputStream helpOutput = new ByteArrayOutputStream();
    FinGrindCli helpCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(helpOutput), fixedClock());
    int helpExitCode = helpCli.run(new String[] {"help"});
    assertEquals(0, helpExitCode);
    String helpText = helpOutput.toString(StandardCharsets.UTF_8);
    assertTrue(helpText.contains("FinGrind Help"));
    assertFalse(helpText.contains("\"status\""));

    ByteArrayOutputStream versionOutput = new ByteArrayOutputStream();
    FinGrindCli versionCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(versionOutput), fixedClock());
    int versionExitCode = versionCli.run(new String[] {"version"});
    assertEquals(0, versionExitCode);
    String versionText = versionOutput.toString(StandardCharsets.UTF_8);
    assertTrue(versionText.contains("FinGrind"));
    assertFalse(versionText.contains("\"status\""));

    ByteArrayOutputStream capabilitiesOutput = new ByteArrayOutputStream();
    FinGrindCli capabilitiesCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(capabilitiesOutput),
            fixedClock());
    int capabilitiesExitCode = capabilitiesCli.run(new String[] {"capabilities"});
    assertEquals(0, capabilitiesExitCode);
    String capabilitiesText = capabilitiesOutput.toString(StandardCharsets.UTF_8);
    assertTrue(capabilitiesText.contains("Capabilities"));
    assertFalse(capabilitiesText.contains("\"status\""));
  }

  @Test
  void run_helpFullJsonPublishesExpandedOverviewContract() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "--detail", "full", "--output", "json"});

    assertEquals(0, exitCode);
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    assertEquals("full", payload.path("detail").stringValue());
    JsonNode fullContract = payload.path("fullContract");
    assertTrue(fullContract.isObject());
    assertTrue(fullContract.path("bookModel").isObject());
    assertTrue(fullContract.path("bookkeepingKernel").isObject());
    assertTrue(fullContract.path("currencyModel").isObject());
    assertTrue(fullContract.path("extensionSurface").isMissingNode());
    assertTrue(fullContract.path("quickStart").isArray());
  }

  @Test
  void run_rejectsHelpDetailOnTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "--output", "text", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Error"), output);
    assertTrue(output.contains("invalid-request"), output);
    assertTrue(output.contains("Argument"), output);
    assertTrue(output.contains("--output"), output);
    assertTrue(output.contains("resolved output mode is json"), output);
  }

  @Test
  void run_rejectsCapabilitiesDetailOnTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities", "--output", "text", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Error"), output);
    assertTrue(output.contains("invalid-request"), output);
    assertTrue(output.contains("Argument"), output);
    assertTrue(output.contains("--output"), output);
    assertTrue(output.contains("resolved output mode is json"), output);
  }

  @Test
  void run_returnsScopedHelpForCommandHelpAlias() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"post-entry", "--help", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("Examples"));
    assertTrue(help.contains("Input Contract"));
  }

  @Test
  void run_returnsTemporalScopeGuidanceForReadHelpTopics() {
    assertTemporalScopeHelp(
        "account-ledger",
        "ranged-filter",
        "--effective-date-from, --effective-date-to",
        "Omit the lower boundary to start at book start");
    assertTemporalScopeHelp(
        "period-summary",
        "bounded-period",
        "--period-start, --period-end",
        "Both boundaries must be supplied");
    assertTemporalScopeHelp(
        "financial-position",
        "as-of-date",
        "--effective-date-as-of",
        "Supply --effective-date-as-of to pin that cutoff explicitly");
    assertTemporalScopeHelp(
        "tax-obligation",
        "bounded-period",
        "--period-start, --period-end",
        "Both boundaries must be supplied");
  }

  @Test
  void run_returnsTemplateHelpWithTemplateFamilySpecificOperatorNote() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "print-request-template", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("placeholder-first scaffold"));
    assertTrue(
        containsCollapsedText(
            help,
            "Replace every replace-before-commit token before submitting it to a live book."));
  }

  @Test
  void run_rewritesBundleHelpUsageAndHintsToTheBundleLauncher() throws Exception {
    String priorDistribution =
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "__missing__");
    try {
      System.setProperty(
          FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
      String bundleLauncher =
          CliInvocationText.launcherCommandFor(
              FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, System.getProperty("os.name", ""));
      ByteArrayOutputStream helpOutputStream = new ByteArrayOutputStream();
      FinGrindCli helpCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(helpOutputStream),
              fixedClock());
      ByteArrayOutputStream failureOutputStream = new ByteArrayOutputStream();
      FinGrindCli failureCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(failureOutputStream),
              fixedClock());
      int helpExitCode = helpCli.run(new String[] {"help", "post-entry", "--output", "json"});
      int failureExitCode = failureCli.run(jsonArguments("post-entry", "--bogus"));
      assertEquals(0, helpExitCode);
      assertEquals(1, failureExitCode);
      JsonNode helpPayload =
          new ObjectMapper()
              .readTree(helpOutputStream.toString(StandardCharsets.UTF_8))
              .path("payload");
      assertTrue(containsText(helpPayload, bundleLauncher + " post-entry"));
      JsonNode failurePayload =
          assertDoesNotThrow(
              () ->
                  new ObjectMapper()
                      .readTree(failureOutputStream.toString(StandardCharsets.UTF_8)));
      assertEquals("error", failurePayload.path("status").stringValue());
      assertEquals("Unsupported argument: --bogus", failurePayload.path("message").stringValue());
      assertTrue(
          failurePayload
              .path("hint")
              .stringValue()
              .contains(
                  "Run '"
                      + bundleLauncher
                      + " help post-entry' to inspect the supported command syntax."));
    } finally {
      if ("__missing__".equals(priorDistribution)) {
        System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      } else {
        System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, priorDistribution);
      }
    }
  }

  @Test
  void run_rewritesSourceCheckoutHelpToTheGeneratedLauncherSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_rewritesDirectJavaHelpToTheDeveloperJarSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_rewritesContainerHelpToTheDockerSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_invalidInvocationHonorsExplicitJsonOutputSelection() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"post-entry", "--bogus", "--output", "json"});

    assertEquals(1, exitCode);
    JsonNode failurePayload =
        new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
    assertEquals("error", failurePayload.path("status").stringValue());
    assertEquals("Unsupported argument: --bogus", failurePayload.path("message").stringValue());
  }

  @Test
  void run_doesNotTouchWorkflowForDiscoveryCommands() {
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(java.nio.file.Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(java.util.List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode = cli.run(new String[] {"capabilities", "--output", "json"});
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertFalse(workflow.workflowInvoked());
  }
}
