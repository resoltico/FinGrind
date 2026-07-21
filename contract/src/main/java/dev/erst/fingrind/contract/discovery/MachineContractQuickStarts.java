package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.List;

/** Canonical quick-start workflow publication for the machine contract. */
final class MachineContractQuickStarts {
  private static final String QUICK_START_FOUNDER_PRINCIPAL_ID =
      "123e4567-e89b-12d3-a456-426614174000";

  private MachineContractQuickStarts() {}

  static List<WorkflowDescriptor> canonicalQuickStart(RuntimeDistribution runtimeDistribution) {
    return switch (runtimeDistribution) {
      case SELF_CONTAINED_BUNDLE -> List.of(workflow(WorkflowSurface.BUNDLE_POSIX_SHELL));
      case SOURCE_CHECKOUT_GRADLE ->
          List.of(
              workflow(WorkflowSurface.SOURCE_CHECKOUT_POSIX_SHELL),
              workflow(WorkflowSurface.SOURCE_CHECKOUT_WINDOWS_POWERSHELL));
      case DIRECT_JAVA_INVOCATION ->
          List.of(
              workflow(WorkflowSurface.DIRECT_JAVA_POSIX_SHELL),
              workflow(WorkflowSurface.DIRECT_JAVA_WINDOWS_POWERSHELL));
      case CONTAINER_IMAGE -> List.of(workflow(WorkflowSurface.CONTAINER_DOCKER));
    };
  }

  static WorkflowDescriptor workflow(WorkflowSurface surface) {
    QuickStartPaths paths = quickStartPaths(surface);
    return new WorkflowDescriptor(
        surface,
        List.of(
            WorkflowStepDescriptor.note(introNote(surface)),
            WorkflowStepDescriptor.command(
                "%s %s %s %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE),
                        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                        paths.bookKeyFile())),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --entity-name \"Acme Studio\" --book-template-id OWNER_MANAGED_SERVICE --accounting-basis CASH --functional-currency EUR --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 %s %s %s %s %s %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.OPEN_BOOK),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                        QUICK_START_FOUNDER_PRINCIPAL_ID,
                        ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
                        paths.founderKeyFile(),
                        ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
                        paths.founderPassphraseFile())),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --limit 10"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS),
                        paths.bookFile(),
                        paths.bookKeyFile())),
            requestPreparationCommand(surface, paths),
            requestPreparationNote(surface, paths),
            WorkflowStepDescriptor.note(
                "Use a fresh provenance.idempotencyKey for each committed posting on the same book."),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --request-file %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        paths.requestFile())),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --request-file %s %s %s %s %s %s %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.RECORD_SALE_SETTLED),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        paths.requestFile(),
                        ProtocolOptions.Attestation.PRINCIPAL_ID,
                        QUICK_START_FOUNDER_PRINCIPAL_ID,
                        ProtocolOptions.Attestation.KEY_FILE,
                        paths.founderKeyFile(),
                        ProtocolOptions.Attestation.PASSPHRASE_FILE,
                        paths.founderPassphraseFile())),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --output text"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.TRIAL_BALANCE),
                        paths.bookFile(),
                        paths.bookKeyFile()))));
  }

  private static String introNote(WorkflowSurface surface) {
    return switch (surface) {
      case PATH_POSIX_SHELL -> "Run commands from a POSIX shell where fingrind is already on PATH.";
      case BUNDLE_POSIX_SHELL ->
          "Run commands from the extracted bundle root so the canonical launcher path resolves directly.";
      case SOURCE_CHECKOUT_POSIX_SHELL ->
          "Run commands from the repository root; the source-checkout wrapper refreshes the managed runtime, raw JAR, and Gradle-owned Java 26 toolchain manifest automatically when the checkout has moved.";
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
          "Run commands from the repository root; the source-checkout wrapper refreshes the managed runtime, raw JAR, and Gradle-owned Java 26 toolchain manifest automatically when the checkout has moved.";
      case DIRECT_JAVA_POSIX_SHELL ->
          "Run commands from the repository root; the direct-Java wrapper refreshes the managed runtime, raw JAR, and Gradle-owned Java 26 toolchain manifest automatically when the checkout has moved.";
      case DIRECT_JAVA_WINDOWS_POWERSHELL ->
          "Run commands from the repository root; the direct-Java wrapper refreshes the managed runtime, raw JAR, and Gradle-owned Java 26 toolchain manifest automatically when the checkout has moved.";
      case CONTAINER_DOCKER ->
          "Define a session-local fingrind wrapper backed by the published or locally built container image, then run this workflow through that logical launcher name.";
    }
        + " Before "
        + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
        + ", create a nonempty owner-only founder passphrase file at "
        + quickStartPaths(surface).founderPassphraseFile()
        + "; the founder key path is created no-clobber when absent.";
  }

  private static WorkflowStepDescriptor requestPreparationCommand(
      WorkflowSurface surface, QuickStartPaths paths) {
    return switch (surface) {
      case BUNDLE_POSIX_SHELL ->
          WorkflowStepDescriptor.command(
              "cp ./quick-start-request.json %s".formatted(paths.requestFile()));
      case PATH_POSIX_SHELL,
          SOURCE_CHECKOUT_POSIX_SHELL,
          SOURCE_CHECKOUT_WINDOWS_POWERSHELL,
          DIRECT_JAVA_POSIX_SHELL,
          DIRECT_JAVA_WINDOWS_POWERSHELL,
          CONTAINER_DOCKER ->
          WorkflowStepDescriptor.command(
              "%s %s > %s"
                  .formatted(
                      launcherCommand(surface),
                      ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE),
                      paths.requestFile()));
    };
  }

  private static WorkflowStepDescriptor requestPreparationNote(
      WorkflowSurface surface, QuickStartPaths paths) {
    return switch (surface) {
      case BUNDLE_POSIX_SHELL ->
          WorkflowStepDescriptor.note(
              "The bundled quick-start request is a concrete sample document for the first settled sale. Replace the sample evidence and provenance values in "
                  + paths.requestFile()
                  + " before using it for real-world bookkeeping.");
      case PATH_POSIX_SHELL,
          SOURCE_CHECKOUT_POSIX_SHELL,
          SOURCE_CHECKOUT_WINDOWS_POWERSHELL,
          DIRECT_JAVA_POSIX_SHELL,
          DIRECT_JAVA_WINDOWS_POWERSHELL,
          CONTAINER_DOCKER ->
          WorkflowStepDescriptor.note(
              "The emitted request document is a placeholder-first scaffold for a settled sale entry. Replace the evidence and provenance placeholders in "
                  + paths.requestFile()
                  + " before using it for real-world bookkeeping. Run "
                  + ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE)
                  + " "
                  + ProtocolCatalog.operationName(OperationId.POST_ENTRY)
                  + " when you explicitly need the raw direct-journal scaffold.");
    };
  }

  private static QuickStartPaths quickStartPaths(WorkflowSurface surface) {
    return switch (surface) {
      case PATH_POSIX_SHELL,
          BUNDLE_POSIX_SHELL,
          SOURCE_CHECKOUT_POSIX_SHELL,
          DIRECT_JAVA_POSIX_SHELL,
          CONTAINER_DOCKER ->
          new QuickStartPaths(
              "./secrets/acme.book-key",
              "./books/acme.sqlite",
              "./request.json",
              "./secrets/acme-founder.fgatk",
              "./secrets/acme-founder.passphrase");
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL, DIRECT_JAVA_WINDOWS_POWERSHELL ->
          new QuickStartPaths(
              ".\\secrets\\acme.book-key",
              ".\\books\\acme.sqlite",
              ".\\request.json",
              ".\\secrets\\acme-founder.fgatk",
              ".\\secrets\\acme-founder.passphrase");
    };
  }

  private static String launcherCommand(WorkflowSurface surface) {
    return switch (surface) {
      case PATH_POSIX_SHELL -> "fingrind";
      case BUNDLE_POSIX_SHELL ->
          ProtocolCatalog.distribution().bundleLauncherCommand(PublicCliBundleTarget.LINUX_X86_64);
      case SOURCE_CHECKOUT_POSIX_SHELL ->
          ProtocolCatalog.distribution().sourceCheckoutLauncherCommand(false);
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
          ProtocolCatalog.distribution().sourceCheckoutLauncherCommand(true);
      case DIRECT_JAVA_POSIX_SHELL ->
          ProtocolCatalog.distribution().directJavaLauncherCommand(false);
      case DIRECT_JAVA_WINDOWS_POWERSHELL ->
          ProtocolCatalog.distribution().directJavaLauncherCommand(true);
      case CONTAINER_DOCKER -> ProtocolCatalog.distribution().containerLauncherCommand();
    };
  }

  private record QuickStartPaths(
      String bookKeyFile,
      String bookFile,
      String requestFile,
      String founderKeyFile,
      String founderPassphraseFile) {}
}
