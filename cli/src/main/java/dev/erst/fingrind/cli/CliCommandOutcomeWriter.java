package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Writes resolved CLI outcomes and deterministic failures with the canonical exit policy. */
final class CliCommandOutcomeWriter {
  private CliCommandOutcomeWriter() {}

  static <T> int writeResolvedResult(
      ContractDecision<T> decision,
      OutputMode outputMode,
      Consumer<T> successWriter,
      ToIntFunction<T> successExitCode,
      CliResponseWriter responseWriter) {
    return decision.fold(
        result -> {
          successWriter.accept(result);
          return successExitCode.applyAsInt(result);
        },
        failure -> writeDeterministicFailure(failure, outputMode, responseWriter));
  }

  static int writeDeterministicFailure(
      ContractFailure failure, OutputMode outputMode, CliResponseWriter responseWriter) {
    responseWriter.writeDeterministicFailure(CliFailureMapper.contractFailure(failure), outputMode);
    return CliExecutionPolicy.contractFailureExitCode(failure);
  }
}
