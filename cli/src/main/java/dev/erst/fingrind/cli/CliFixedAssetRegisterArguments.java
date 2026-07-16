package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses the fixed-asset register report surface. */
final class CliFixedAssetRegisterArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.AS_OF,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());

  private CliFixedAssetRegisterArguments() {}

  static CliCommand parseFixedAssetRegisterCommand(List<String> arguments) {
    var parsed = CliBookArgumentParser.parseBookAndCommandArguments(arguments, ARGUMENTS);
    @Nullable LocalDate asOf = null;
    @Nullable OutputMode mode = null;
    @Nullable Path pdf = null;
    ListIterator<String> it = parsed.commandArguments().listIterator();
    while (it.hasNext()) {
      String option = it.next();
      if (ProtocolOptions.DateRange.AS_OF.equals(option)) {
        asOf =
            CliReportOptionArguments.requireDateOption(asOf, it, ProtocolOptions.DateRange.AS_OF);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(option)) {
        mode = CliReportOptionArguments.requireReportOutputMode(mode, it);
        continue;
      }
      pdf = CliOptionModes.requirePdfOutPath(pdf, it);
    }
    return new FixedAssetRegister(
        parsed.bookAccess(),
        new FixedAssetRegisterQuery(Optional.ofNullable(asOf)),
        CliOptionModes.resolvedReportOutput(mode, pdf));
  }
}
