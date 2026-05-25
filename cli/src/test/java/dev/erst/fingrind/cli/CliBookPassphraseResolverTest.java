package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqliteBookPassphraseTestSupport;
import java.io.ByteArrayInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link CliBookPassphraseResolver}. */
class CliBookPassphraseResolverTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void resolve_readsUtf8PassphraseFromKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    writeSecureString(keyFile, "swordfish\n");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]), prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                new BookAccess(
                    Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(keyFile)))
            .requireAccepted()) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals("swordfish", SqliteBookPassphraseTestSupport.utf8String(passphrase));
    }
  }

  @Test
  void resolve_readsUtf8PassphraseFromStandardInput() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream("stdin-passphrase\n".getBytes(StandardCharsets.UTF_8)),
            prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                new BookAccess(
                    Path.of("book.sqlite"), BookAccess.PassphraseSource.StandardInput.INSTANCE))
            .requireAccepted()) {
      assertEquals("standard input", passphrase.sourceDescription());
      assertEquals("stdin-passphrase", SqliteBookPassphraseTestSupport.utf8String(passphrase));
    }
  }

  @Test
  void resolve_rejectsOversizedStandardInputPassphrases() {
    byte[] oversizedPassphrase =
        "x".repeat(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1).getBytes(StandardCharsets.UTF_8);
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(oversizedPassphrase), prompt -> failPrompt(prompt));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE))
                    .requireAccepted());

    assertEquals(
        "FinGrind book passphrase input from standard input exceeded the 4096-byte limit.",
        exception.getMessage());
  }

  @Test
  void resolve_readsPromptPassphraseFromTerminal() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> ContractDecision.accepted("prompt-passphrase".toCharArray()));
    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                new BookAccess(bookPath, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
            .requireAccepted()) {
      assertTrue(
          passphrase
              .sourceDescription()
              .contains(bookPath.toAbsolutePath().normalize().toString()));
      assertEquals("prompt-passphrase", SqliteBookPassphraseTestSupport.utf8String(passphrase));
    }
  }

  @Test
  void resolve_rejectsOversizedPromptPassphrases() {
    char[] enteredPassword =
        "x".repeat(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1).toCharArray();
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> ContractDecision.accepted(enteredPassword));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("exceeded the 4096-byte UTF-8 limit"));
    assertArrayEquals(new char[enteredPassword.length], enteredPassword);
  }

  @Test
  void resolve_rejectsMalformedUtf16PromptPassphrase() {
    char[] enteredPassword = new char[] {'A', '\uD800', 'B'};
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> ContractDecision.accepted(enteredPassword));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("must contain a UTF-8 passphrase"));
    assertArrayEquals(new char[enteredPassword.length], enteredPassword);
  }

  @Test
  void resolve_rejectsMissingPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt ->
                missingPrompt(
                    "FinGrind did not receive a book passphrase from the interactive console."));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                    .requireAccepted());

    assertEquals(
        "FinGrind did not receive a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void resolve_readsConfirmedPromptPassphraseFromTerminal() throws Exception {
    Path bookPath = tempDirectory.resolve("confirmed.sqlite");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                if (readCount == 1) {
                  assertTrue(prompt.startsWith("New FinGrind book passphrase for "));
                  return ContractDecision.accepted("confirmed-secret".toCharArray());
                }
                assertTrue(prompt.startsWith("Confirm new FinGrind book passphrase for "));
                return ContractDecision.accepted("confirmed-secret".toCharArray());
              }
            });

    try (SqliteBookPassphrase passphrase =
        resolver
            .resolve(
                bookPath,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
            .requireAccepted()) {
      assertTrue(
          passphrase
              .sourceDescription()
              .contains(bookPath.toAbsolutePath().normalize().toString()));
      assertEquals("confirmed-secret", SqliteBookPassphraseTestSupport.utf8String(passphrase));
    }
  }

  @Test
  void resolve_rejectsMissingConfirmedPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                return readCount == 1
                    ? ContractDecision.accepted("secret".toCharArray())
                    : missingPrompt(
                        "FinGrind did not receive a confirmed book passphrase from the interactive console.");
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                        CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                    .requireAccepted());

    assertEquals(
        "FinGrind did not receive a confirmed book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void resolve_rejectsMismatchedConfirmedPromptPassphrases() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                return ContractDecision.accepted(
                    readCount == 1 ? "first".toCharArray() : "second".toCharArray());
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                        CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                    .requireAccepted());

    assertEquals(
        "FinGrind did not receive matching book passphrases from the interactive console.",
        exception.getMessage());
    String message = Objects.requireNonNull(exception.getMessage());
    assertFalse(message.contains("first"));
    assertFalse(message.contains("second"));
  }

  @Test
  void resolve_wrapsStandardInputReadFailure() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("boom");
              }

              @Override
              public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("boom");
              }
            },
            prompt -> failPrompt(prompt));

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE))
                    .requireAccepted());

    assertEquals(
        "Failed to read the FinGrind book passphrase from standard input.", exception.getMessage());
    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(),
        exception.failure().code());
    assertFalse(Objects.requireNonNull(exception.getMessage()).contains("boom"));
  }

  @Test
  void resolve_rejectsControlCharactersFromStandardInput() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream("line-1\nline-2\n".getBytes(StandardCharsets.UTF_8)),
            prompt -> failPrompt(prompt));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE))
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
    assertFalse(exception.getMessage().contains("line-1"));
    assertFalse(exception.getMessage().contains("line-2"));
  }

  @Test
  void promptingConsoleTerminal_readsPasswordFromTypedPromptingConsole() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            new CliBookPassphraseResolver.PromptingConsole() {
              @Override
              public char @Nullable [] readPassword(String prompt) {
                assertEquals("book.sqlite", prompt);
                return "console-secret".toCharArray();
              }
            });

    assertEquals(
        "console-secret", new String(terminal.readPassword("book.sqlite").requireAccepted()));
  }

  @Test
  void promptingConsoleTerminal_passesPromptToPasswordRead() {
    StringBuilder promptCapture = new StringBuilder();
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            new CliBookPassphraseResolver.PromptingConsole() {
              @Override
              public char @Nullable [] readPassword(String prompt) {
                promptCapture.append(prompt);
                return "console-secret".toCharArray();
              }
            });

    assertEquals(
        "console-secret", new String(terminal.readPassword("book.sqlite").requireAccepted()));
    assertEquals("book.sqlite", promptCapture.toString());
  }

  @Test
  void promptingConsoleTerminal_rejectsNullPasswordReads() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            new CliBookPassphraseResolver.PromptingConsole() {
              @Override
              @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
              public char @Nullable [] readPassword(String prompt) {
                assertEquals("book.sqlite", prompt);
                return null;
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> terminal.readPassword("book.sqlite").requireAccepted());

    assertEquals(
        "FinGrind did not receive a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void promptingConsoleTerminal_wrapsReadPasswordFailures() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            new CliBookPassphraseResolver.PromptingConsole() {
              @Override
              public char @Nullable [] readPassword(String prompt) {
                assertEquals("book.sqlite", prompt);
                throw new IllegalStateException("boom");
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> terminal.readPassword("book.sqlite").requireAccepted());

    assertEquals(
        "Failed to prompt for a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void promptingConsoleTerminal_wrapsConsoleIoErrors() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            new CliBookPassphraseResolver.PromptingConsole() {
              @Override
              public char @Nullable [] readPassword(String prompt) {
                assertEquals("book.sqlite", prompt);
                throw new IOError(new IOException("console boom"));
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> terminal.readPassword("book.sqlite").requireAccepted());

    assertEquals(
        "Failed to prompt for a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void promptStyle_singlePromptDoesNotExposeConfirmationPrompt() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CliBookPassphraseResolver.PromptStyle.SINGLE.confirmationPrompt("book.sqlite"));

    assertEquals("This prompt style does not support confirmation.", exception.getMessage());
  }

  @Test
  void promptingConsoleLookupTerminal_rejectsUnavailableConsole() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new CliBookPassphraseResolver.PromptingConsoleLookupTerminal(() -> null)
                    .readPassword("prompt")
                    .requireAccepted());

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void promptingConsoleLookupTerminal_readsPasswordFromProvidedReader() {
    CliBookPassphraseResolver.PromptingConsoleLookupTerminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleLookupTerminal(
            promptingConsoleSupplier("secret"));
    char[] password = terminal.readPassword("book.sqlite").requireAccepted();

    assertEquals("secret", new String(password));
  }

  @Test
  void promptingConsoleLookupTerminal_rejectsMissingConsoleReader() {
    CliBookPassphraseResolver.PromptingConsoleLookupTerminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleLookupTerminal(() -> null);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> terminal.readPassword("prompt").requireAccepted());

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void systemTerminal_rejectsWhenNoInteractiveConsoleIsAvailable() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliBookPassphraseResolver.systemTerminal()
                    .readPassword("prompt")
                    .requireAccepted());

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void systemPromptingConsole_reportsUnavailableWhenMissing() {
    assertNull(
        CliBookPassphraseResolver.systemPromptingConsole(
            null,
            source -> {
              throw new AssertionError("Missing sources must not inspect terminal state.");
            },
            (source, promptFormat, prompt) -> {
              throw new AssertionError("Missing sources must not read passwords.");
            }));
  }

  @Test
  void systemPromptingConsole_reportsUnavailableWhenNotInteractive() {
    assertNull(
        CliBookPassphraseResolver.systemPromptingConsole(
            "console-source",
            source -> false,
            (source, promptFormat, prompt) -> {
              throw new AssertionError("Non-interactive consoles must not prompt.");
            }));
  }

  @Test
  void systemPromptingConsole_preservesInteractivePromptReads() {
    StringBuilder promptCapture = new StringBuilder();
    CliBookPassphraseResolver.PromptingConsole promptingConsole =
        CliBookPassphraseResolver.systemPromptingConsole(
            "console-source",
            source -> true,
            (source, promptFormat, prompt) -> {
              assertEquals("%s", promptFormat);
              promptCapture.append(source).append(':').append(prompt);
              return "console-secret".toCharArray();
            });

    assertEquals(
        "console-secret",
        new String(
            Objects.requireNonNull(promptingConsole, "promptingConsole")
                .readPassword("book.sqlite")));
    assertEquals("console-source:book.sqlite", promptCapture.toString());
  }

  @Test
  void availableSystemPromptingConsole_reportsUnavailableWhenMissing() {
    assertNull(CliBookPassphraseResolver.availableSystemPromptingConsole(null));
  }

  @Test
  void interactiveSystemPromptingConsole_reportsUnavailableWhenNotInteractive() {
    assertNull(
        CliBookPassphraseResolver.interactiveSystemPromptingConsole(
            () -> false,
            prompt -> {
              throw new AssertionError("Non-interactive consoles must not prompt.");
            }));
  }

  @Test
  void interactiveSystemPromptingConsole_preservesInteractivePromptReads() {
    StringBuilder promptCapture = new StringBuilder();
    CliBookPassphraseResolver.PromptingConsole promptingConsole =
        CliBookPassphraseResolver.interactiveSystemPromptingConsole(
            () -> true,
            prompt -> {
              promptCapture.append(prompt);
              return "bridge-secret".toCharArray();
            });

    assertEquals(
        "bridge-secret",
        new String(
            Objects.requireNonNull(promptingConsole, "promptingConsole")
                .readPassword("book.sqlite")));
    assertEquals("book.sqlite", promptCapture.toString());
  }

  @Test
  void wrappedSystemConsole_delegatesTerminalStateAndPromptReads() {
    StringBuilder promptCapture = new StringBuilder();
    CliBookPassphraseResolver.SystemPromptingConsole systemConsole =
        CliBookPassphraseResolver.wrap(
            () -> true,
            prompt -> {
              promptCapture.append(prompt);
              return "wrapped-secret".toCharArray();
            });

    assertTrue(systemConsole.isTerminal());
    assertEquals("wrapped-secret", new String(systemConsole.readPassword("book.sqlite")));
    assertEquals("book.sqlite", promptCapture.toString());
  }

  private static ContractDecision<char[]> failPrompt(String prompt) {
    throw new AssertionError("Unexpected prompt usage: " + prompt);
  }

  private static ContractDecision<char[]> missingPrompt(String message) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INTERACTIVE_PROMPT_FAILED.failure(message, null, null));
  }

  private static void writeSecureString(Path keyFile, String content) throws IOException {
    SqliteBookKeyFileGenerator.generate(keyFile);
    Files.writeString(keyFile, content, StandardCharsets.UTF_8);
  }

  private static Supplier<CliBookPassphraseResolver.@Nullable PromptingConsole>
      promptingConsoleSupplier(String password) {
    StringBuilder promptCapture = new StringBuilder();
    return () ->
        new CliBookPassphraseResolver.PromptingConsole() {
          @Override
          public char @Nullable [] readPassword(String prompt) {
            promptCapture.append(prompt);
            assertEquals("book.sqlite", promptCapture.toString());
            return password.toCharArray();
          }
        };
  }
}
