package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers public parsing for every owned lifecycle register command. */
class CliLifecycleContextCommandParsingTest {
  private static final List<String> BOOK_ARGUMENTS =
      List.of("--book-file", "book.sqlite", "--book-key-file", "book.key");

  @Test
  void parse_registerCommands_acceptsTheirScopedQueryOptionsAndTextOutput() {
    assertInstanceOf(
        FixedAssetRegister.class,
        CliCommandParsingRegistry.parse(
            OperationId.FIXED_ASSET_REGISTER,
            commandArguments("fixed-asset-register", "--as-of", "2026-07-01", "--output", "text")));
    assertInstanceOf(
        FinancingRegister.class,
        CliCommandParsingRegistry.parse(
            OperationId.FINANCING_REGISTER,
            commandArguments("financing-register", "--output", "text")));
    assertInstanceOf(
        RealizedForeignExchangeRegister.class,
        CliCommandParsingRegistry.parse(
            OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
            commandArguments("realized-foreign-exchange-register", "--output", "text")));
  }

  @Test
  void parse_registerCommands_acceptsPdfArtifacts() {
    assertInstanceOf(
        FixedAssetRegister.class,
        CliFixedAssetRegisterArguments.parseFixedAssetRegisterCommand(
            commandArguments("fixed-asset-register", "--pdf-out", "fixed-assets.pdf")));
    assertInstanceOf(
        FinancingRegister.class,
        CliFinancingRegisterArguments.parseFinancingRegisterCommand(
            commandArguments("financing-register", "--pdf-out", "financing.pdf")));
    assertInstanceOf(
        RealizedForeignExchangeRegister.class,
        CliRealizedForeignExchangeRegisterArguments.parseRealizedForeignExchangeRegisterCommand(
            commandArguments(
                "realized-foreign-exchange-register", "--pdf-out", "realized-fx.pdf")));
  }

  @Test
  void parse_attestationRegistryCommands_acceptRequestFilesAndTextOutput() {
    assertInstanceOf(
        EnrollAttestationKey.class,
        CliCommandParsingRegistry.parse(
            OperationId.ENROLL_KEY,
            commandArguments(
                "enroll-key", "--request-file", "enroll-key.json", "--output", "text")));
    assertInstanceOf(
        RolloverAttestationKey.class,
        CliCommandParsingRegistry.parse(
            OperationId.ROLLOVER_KEY,
            commandArguments(
                "rollover-key", "--request-file", "rollover-key.json", "--output", "text")));
    assertInstanceOf(
        RevokeAttestationKey.class,
        CliCommandParsingRegistry.parse(
            OperationId.REVOKE_KEY,
            commandArguments(
                "revoke-key", "--request-file", "revoke-key.json", "--output", "text")));
    assertInstanceOf(
        AlterAttestationPolicy.class,
        CliCommandParsingRegistry.parse(
            OperationId.ALTER_POLICY,
            commandArguments(
                "alter-policy", "--request-file", "alter-policy.json", "--output", "text")));
  }

  private static List<String> commandArguments(String command, String... commandArguments) {
    List<String> arguments = new java.util.ArrayList<>();
    arguments.add(command);
    arguments.addAll(BOOK_ARGUMENTS);
    arguments.addAll(List.of(commandArguments));
    return List.copyOf(arguments);
  }
}
