package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link CliBookPassphraseResolver}. */
class CliBookPassphraseResolverTest {
  @TempDir Path tempDirectory;

  @Test
  void resolve_readsUtf8PassphraseFromKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    Files.writeString(keyFile, "swordfish\n", StandardCharsets.UTF_8);
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]), prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(
                    Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(keyFile)));
        Arena arena = Arena.ofConfined()) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(
          "swordfish",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_readsUtf8PassphraseFromStandardInput() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream("stdin-passphrase\n".getBytes(StandardCharsets.UTF_8)),
            prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(
                    Path.of("book.sqlite"), BookAccess.PassphraseSource.StandardInput.INSTANCE));
        Arena arena = Arena.ofConfined()) {
      assertEquals("standard input", passphrase.sourceDescription());
      assertEquals(
          "stdin-passphrase",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_readsPromptPassphraseFromTerminal() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]), prompt -> "prompt-passphrase".toCharArray());
    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(bookPath, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));
        Arena arena = Arena.ofConfined()) {
      assertTrue(
          passphrase
              .sourceDescription()
              .contains(bookPath.toAbsolutePath().normalize().toString()));
      assertEquals(
          "prompt-passphrase",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_rejectsMissingPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(new ByteArrayInputStream(new byte[0]), prompt -> null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    new BookAccess(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE)));

    assertEquals(
        "FinGrind did not receive a book passphrase from the interactive console.",
        exception.getMessage());
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

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    new BookAccess(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "Failed to read the FinGrind book passphrase from standard input.", exception.getMessage());
  }

  @Test
  void systemConsoleReader_reportsNoInteractiveConsoleInTheGradleTestEnvironment() {
    assertTrue(CliBookPassphraseResolver.systemConsoleReader().isEmpty());
  }

  @Test
  void systemTerminal_rejectsWhenNoInteractiveConsoleIsAvailable() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CliBookPassphraseResolver.systemTerminal().readPassword("prompt"));

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void consoleBackedTerminal_readsPasswordFromProvidedReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(readerSupplier("secret"));
    char[] password = terminal.readPassword("book.sqlite");

    assertEquals("secret", new String(password));
  }

  @Test
  void consoleBackedTerminal_rejectsMissingConsoleReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(Optional::empty);
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> terminal.readPassword("prompt"));

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void consoleBackedTerminal_rejectsNullSupplierResult() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(() -> null);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> terminal.readPassword("prompt"));

    assertEquals("reader", exception.getMessage());
  }

  private static char[] failPrompt(String prompt) {
    throw new AssertionError("Unexpected prompt usage: " + prompt);
  }

  private static Supplier<Optional<CliBookPassphraseResolver.Terminal>> readerSupplier(
      String password) {
    return () ->
        Optional.of(
            prompt -> {
              assertEquals("book.sqlite", prompt);
              return password.toCharArray();
            });
  }
}
