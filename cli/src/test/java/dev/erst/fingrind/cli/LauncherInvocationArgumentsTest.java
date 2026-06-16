package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for staged launcher argument resolution. */
class LauncherInvocationArgumentsTest {
  @TempDir Path tempDir;

  @Test
  void resolveFallsBackToProcessArgumentsWhenNoLauncherArgumentsFileIsConfigured() {
    String[] processArguments = {"help"};

    String[] resolved = new LauncherInvocationArguments(Map.of()).resolve(processArguments);

    assertArrayEquals(new String[] {"help"}, resolved);
  }

  @Test
  void resolveForCurrentProcessFallsBackToSuppliedArgumentsWhenNoBridgeEnvIsPresent() {
    assertArrayEquals(
        new String[] {"help"},
        LauncherInvocationArguments.resolveForCurrentProcess(new String[] {"help"}));
  }

  @Test
  void resolveReadsUtf8JsonLauncherArgumentsFromEnvironmentConfiguredFile() throws IOException {
    Path argumentsFile = tempDir.resolve("launcher-arguments.json");
    Files.writeString(
        argumentsFile,
        "[\"generate-book-key-file\",\"--book-key-file\",\""
            + tempDir.resolve("workspace odd").resolve("Rīga büro").resolve("--entity.key")
            + "\"]\n");

    String[] resolved =
        new LauncherInvocationArguments(
                Map.of(LauncherInvocationArguments.ARGUMENTS_FILE_ENV, argumentsFile.toString()))
            .resolve(new String[] {"help"});

    assertEquals("generate-book-key-file", resolved[0]);
    assertEquals("--book-key-file", resolved[1]);
    assertEquals(
        tempDir.resolve("workspace odd").resolve("Rīga büro").resolve("--entity.key").toString(),
        resolved[2]);
  }

  @Test
  void resolveRejectsInvalidArgumentsFilePathValuesBeforeAnyFileRead() {
    String malformedPath = "\uD800broken-launcher-arguments.json";
    LauncherInvocationArgumentsException exception =
        assertThrows(
            LauncherInvocationArgumentsException.class,
            () ->
                new LauncherInvocationArguments(
                        Map.of(LauncherInvocationArguments.ARGUMENTS_FILE_ENV, malformedPath))
                    .resolve(new String[] {"help"}));

    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue(
        message.startsWith(
            "Invalid staged launcher arguments file path in "
                + LauncherInvocationArguments.ARGUMENTS_FILE_ENV
                + ": "));
  }

  @Test
  void resolveRejectsMissingLauncherArgumentFiles() {
    Path missingFile = tempDir.resolve("missing-launcher-arguments.json");

    LauncherInvocationArgumentsException exception =
        assertThrows(
            LauncherInvocationArgumentsException.class,
            () ->
                new LauncherInvocationArguments(
                        Map.of(
                            LauncherInvocationArguments.ARGUMENTS_FILE_ENV, missingFile.toString()))
                    .resolve(new String[] {"help"}));

    assertEquals(
        "Unable to read staged launcher arguments file at " + missingFile + ".",
        exception.getMessage());
  }

  @Test
  void resolveRejectsNonArrayLauncherArgumentPayloads() throws IOException {
    Path argumentsFile = tempDir.resolve("launcher-arguments.json");
    Files.writeString(argumentsFile, "{\"arguments\":[\"help\"]}\n");

    LauncherInvocationArgumentsException exception =
        assertThrows(
            LauncherInvocationArgumentsException.class,
            () ->
                new LauncherInvocationArguments(
                        Map.of(
                            LauncherInvocationArguments.ARGUMENTS_FILE_ENV,
                            argumentsFile.toString()))
                    .resolve(new String[] {"help"}));

    assertEquals(
        "Staged launcher arguments file at "
            + argumentsFile
            + " must contain one JSON array of strings.",
        exception.getMessage());
  }

  @Test
  void resolveRejectsMalformedJsonLauncherArgumentPayloads() throws IOException {
    Path argumentsFile = tempDir.resolve("launcher-arguments.json");
    Files.writeString(argumentsFile, "[\"help\"\n");

    LauncherInvocationArgumentsException exception =
        assertThrows(
            LauncherInvocationArgumentsException.class,
            () ->
                new LauncherInvocationArguments(
                        Map.of(
                            LauncherInvocationArguments.ARGUMENTS_FILE_ENV,
                            argumentsFile.toString()))
                    .resolve(new String[] {"help"}));

    assertEquals(
        "Staged launcher arguments file at "
            + argumentsFile
            + " must contain one JSON array of strings.",
        exception.getMessage());
  }

  @Test
  void resolveRejectsNonStringLauncherArgumentElements() throws IOException {
    Path argumentsFile = tempDir.resolve("launcher-arguments.json");
    Files.writeString(argumentsFile, "[\"help\", 42]\n");

    LauncherInvocationArgumentsException exception =
        assertThrows(
            LauncherInvocationArgumentsException.class,
            () ->
                new LauncherInvocationArguments(
                        Map.of(
                            LauncherInvocationArguments.ARGUMENTS_FILE_ENV,
                            argumentsFile.toString()))
                    .resolve(new String[] {"help"}));

    assertEquals(
        "Staged launcher arguments file at "
            + argumentsFile
            + " must contain one JSON array of strings.",
        exception.getMessage());
  }
}
