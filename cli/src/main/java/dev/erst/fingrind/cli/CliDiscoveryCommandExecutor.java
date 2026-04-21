package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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

  int writeHelp(OutputMode outputMode) {
    responseWriter.writeHelp(
        MachineContract.help(applicationIdentity(), environmentDescriptor()), outputMode);
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
    responseWriter.writeRequestTemplate(MachineContract.requestTemplate(clock));
    return 0;
  }

  int writePlanTemplate() {
    responseWriter.writePlanTemplate(MachineContract.planTemplate(clock));
    return 0;
  }

  private ContractDiscovery.ApplicationIdentity applicationIdentity() {
    return CliRuntimeContractSupport.applicationIdentity(metadata);
  }

  private ContractDiscovery.EnvironmentDescriptor environmentDescriptor() {
    return CliRuntimeContractSupport.environmentDescriptor(
        SqliteRuntime.probe(), FinGrindCli.runtimeDistribution());
  }
}
