package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.List;

/** Canonical quick-start workflow publication for the machine contract. */
final class MachineContractQuickStarts {
  private MachineContractQuickStarts() {}

  static List<WorkflowDescriptor> canonicalQuickStart(RuntimeDistribution runtimeDistribution) {
    return switch (runtimeDistribution) {
      case SELF_CONTAINED_BUNDLE ->
          List.of(
              workflow(WorkflowSurface.BUNDLE_POSIX_SHELL),
              workflow(WorkflowSurface.BUNDLE_WINDOWS_POWERSHELL));
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

  private static WorkflowDescriptor workflow(WorkflowSurface surface) {
    QuickStartPaths paths = quickStartPaths(surface);
    return new WorkflowDescriptor(
        surface,
        List.of(
            WorkflowStepDescriptor.note(introNote(surface)),
            WorkflowStepDescriptor.command(
                "%s %s --book-key-file %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE),
                        paths.bookKeyFile())),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --entity-name \"Acme Studio\" --business-activity-tag translation-services --functional-currency EUR --fiscal-year-start 01-01"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.OPEN_BOOK),
                        paths.bookFile(),
                        paths.bookKeyFile())),
            WorkflowStepDescriptor.edit(
                paths.declareCashFile(), MachineContractTemplatesCatalog.declareAccountCashJson()),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --request-file %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        paths.declareCashFile())),
            WorkflowStepDescriptor.edit(
                paths.declareRevenueFile(),
                MachineContractTemplatesCatalog.declareAccountRevenueJson()),
            WorkflowStepDescriptor.command(
                "%s %s --book-file %s --book-key-file %s --request-file %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        paths.declareRevenueFile())),
            WorkflowStepDescriptor.command(
                "%s %s > %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE),
                        paths.requestFile())),
            WorkflowStepDescriptor.note(
                "The emitted request document is a placeholder-first scaffold. Replace the evidence and provenance placeholders in "
                    + paths.requestFile()
                    + " before using it for real-world bookkeeping."),
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
                "%s %s --book-file %s --book-key-file %s --request-file %s"
                    .formatted(
                        launcherCommand(surface),
                        ProtocolCatalog.operationName(OperationId.POST_ENTRY),
                        paths.bookFile(),
                        paths.bookKeyFile(),
                        paths.requestFile())),
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
      case BUNDLE_POSIX_SHELL ->
          "Run commands from the extracted bundle root so the canonical launcher path resolves directly.";
      case BUNDLE_WINDOWS_POWERSHELL ->
          "Run commands from the extracted bundle root so the canonical PowerShell launcher path resolves directly.";
      case SOURCE_CHECKOUT_POSIX_SHELL ->
          "Run commands from the repository root after ./gradlew :cli:installShadowDist prepareManagedSqlite builds the local launcher and managed SQLite runtime.";
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
          "Run commands from the repository root after .\\gradlew.bat :cli:installShadowDist prepareManagedSqlite builds the local launcher and managed SQLite runtime.";
      case DIRECT_JAVA_POSIX_SHELL ->
          "Run commands from the repository root after ./gradlew :cli:shadowJar prepareManagedSqlite builds the developer raw JAR and managed SQLite runtime.";
      case DIRECT_JAVA_WINDOWS_POWERSHELL ->
          "Run commands from the repository root after .\\gradlew.bat :cli:shadowJar prepareManagedSqlite builds the developer raw JAR and managed SQLite runtime.";
      case CONTAINER_DOCKER ->
          "Replace <container-image> with the built or published FinGrind image reference and replace <host-workdir> with the host directory you want mounted at /workspace.";
    };
  }

  private static QuickStartPaths quickStartPaths(WorkflowSurface surface) {
    return switch (surface) {
      case BUNDLE_POSIX_SHELL,
          SOURCE_CHECKOUT_POSIX_SHELL,
          DIRECT_JAVA_POSIX_SHELL,
          CONTAINER_DOCKER ->
          new QuickStartPaths(
              "./secrets/acme.book-key",
              "./books/acme.sqlite",
              "./declare-account-cash.json",
              "./declare-account-revenue.json",
              "./request.json");
      case BUNDLE_WINDOWS_POWERSHELL,
          SOURCE_CHECKOUT_WINDOWS_POWERSHELL,
          DIRECT_JAVA_WINDOWS_POWERSHELL ->
          new QuickStartPaths(
              ".\\secrets\\acme.book-key",
              ".\\books\\acme.sqlite",
              ".\\declare-account-cash.json",
              ".\\declare-account-revenue.json",
              ".\\request.json");
    };
  }

  private static String launcherCommand(WorkflowSurface surface) {
    return switch (surface) {
      case BUNDLE_POSIX_SHELL ->
          ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64);
      case BUNDLE_WINDOWS_POWERSHELL ->
          ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.WINDOWS_X86_64);
      case SOURCE_CHECKOUT_POSIX_SHELL -> ProtocolCatalog.sourceCheckoutLauncherCommand(false);
      case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
          ProtocolCatalog.sourceCheckoutLauncherCommand(true);
      case DIRECT_JAVA_POSIX_SHELL -> ProtocolCatalog.directJavaLauncherCommand(false);
      case DIRECT_JAVA_WINDOWS_POWERSHELL -> ProtocolCatalog.directJavaLauncherCommand(true);
      case CONTAINER_DOCKER -> ProtocolCatalog.containerLauncherCommand();
    };
  }

  private record QuickStartPaths(
      String bookKeyFile,
      String bookFile,
      String declareCashFile,
      String declareRevenueFile,
      String requestFile) {}
}
