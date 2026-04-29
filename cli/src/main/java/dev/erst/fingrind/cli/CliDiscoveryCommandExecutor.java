package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes discovery and template commands that render contract-owned CLI metadata. */
final class CliDiscoveryCommandExecutor {
  private final CliResponseWriter responseWriter;
  private final CliMetadata metadata;
  private final Clock clock;

  CliDiscoveryCommandExecutor(CliResponseWriter responseWriter, CliMetadata metadata, Clock clock) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.metadata = Objects.requireNonNull(metadata, "metadata");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  int writeHelp(@Nullable OperationId commandTopic, OutputMode outputMode) {
    responseWriter.writeHelp(
        launcherAwareHelp(
            MachineContract.help(applicationIdentity(), environmentDescriptor(), commandTopic)),
        outputMode);
    return 0;
  }

  int writeCapabilities(OutputMode outputMode) {
    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            applicationIdentity(), environmentDescriptor(), Instant.now(clock)),
        outputMode);
    return 0;
  }

  int writeVersion(OutputMode outputMode) {
    responseWriter.writeVersion(MachineContract.version(applicationIdentity()), outputMode);
    return 0;
  }

  int writeRequestTemplate() {
    responseWriter.writeRequestTemplate(MachineContract.requestTemplate());
    return 0;
  }

  int writePlanTemplate() {
    responseWriter.writePlanTemplate(MachineContract.planTemplate());
    return 0;
  }

  private ApplicationIdentity applicationIdentity() {
    return CliRuntimeContractDescriptors.applicationIdentity(metadata);
  }

  private static HelpDescriptor launcherAwareHelp(HelpDescriptor helpDescriptor) {
    List<String> usage =
        helpDescriptor.usage().stream().map(CliInvocationText::rewriteInvocationPrefix).toList();
    return new HelpDescriptor(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        usage,
        helpDescriptor.bookModel(),
        helpDescriptor.commands(),
        helpDescriptor.quickStart(),
        helpDescriptor.exitCodes(),
        helpDescriptor.preflight(),
        helpDescriptor.currencyModel(),
        helpDescriptor.environment());
  }

  private EnvironmentDescriptor environmentDescriptor() {
    return CliRuntimeContractDescriptors.environmentDescriptor(
        SqliteRuntime.probe(), FinGrindCli.runtimeDistribution());
  }
}
