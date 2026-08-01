package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Documents the caller-controlled path semantics of request-file input. */
class CliRequestReaderExternalInputPathTest extends CliRequestReaderTestSupport {
  @Test
  void readPostEntryCommand_allowsACallerDirectedRequestFileAlias() throws Exception {
    Path target = writeNamedRequest("request-target.json", validRequestJson(false));
    Path alias = tempDirectory.resolve("request-alias.json");
    createSymbolicLinkOrSkip(alias, target.getFileName());

    PostEntryCommand command =
        new CliRequestReader(new ByteArrayInputStream(new byte[0])).readPostEntryCommand(alias);

    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_refusesAnAliasToANonRegularRequestTarget() throws Exception {
    Path alias = tempDirectory.resolve("request-directory-alias.json");
    createSymbolicLinkOrSkip(alias, Path.of("."));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class,
            () ->
                new CliRequestReader(new ByteArrayInputStream(new byte[0]))
                    .readPostEntryCommand(alias));

    assertEquals("Failed to read request file.", exception.getMessage());
  }

  private static void createSymbolicLinkOrSkip(Path alias, Path target) throws IOException {
    try {
      Files.createSymbolicLink(alias, target);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException unavailable) {
      assumeTrue(
          false, "The filesystem does not permit symbolic-link test fixtures: " + unavailable);
    }
  }
}
