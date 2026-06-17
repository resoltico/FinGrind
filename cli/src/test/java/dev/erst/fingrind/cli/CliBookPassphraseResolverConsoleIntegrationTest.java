package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Integration coverage for the real interactive-console branch under a pseudo-terminal. */
class CliBookPassphraseResolverConsoleIntegrationTest {
  private static final String PTY_SECRET = "console-secret";

  @Test
  void systemTerminal_readsPasswordWhenChildJvmOwnsInteractiveConsole() throws Exception {
    assumeFalse(isWindows(), "PTY-backed console coverage requires a Unix-like host.");
    assumeTrue(commandAvailable("python3"), "python3 is required for PTY-backed console coverage.");

    String output;
    int exitCode;
    try (Process process =
        new ProcessBuilder(childPtyCommand()).redirectErrorStream(true).start()) {
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }

    assertEquals(0, exitCode, output);
    assertTrue(!output.contains(PTY_SECRET), output);
    assertTrue(output.contains("accepted-length=" + PTY_SECRET.length()), output);
  }

  private static List<String> childPtyCommand() {
    List<String> command = new ArrayList<>();
    command.add("python3");
    command.add("-c");
    command.add(ptyDriverScript());
    command.addAll(childJavaCommand());
    return command;
  }

  private static List<String> childJavaCommand() {
    return CliChildJvmSupport.childJavaCommand(CliBookPassphraseResolverConsoleProbe.class);
  }

  private static boolean commandAvailable(String command) {
    try (Process process = new ProcessBuilder("sh", "-c", "command -v " + command).start()) {
      return process.waitFor() == 0;
    } catch (IOException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  private static String ptyDriverScript() {
    return """
        import os
        import pty
        import sys
        import termios

        secret = b'console-secret\\n'
        command = sys.argv[1:]
        pid, fd = pty.fork()
        if pid == 0:
            os.execvp(command[0], command)
        attrs = termios.tcgetattr(fd)
        attrs[3] = attrs[3] & ~termios.ECHO
        termios.tcsetattr(fd, termios.TCSANOW, attrs)
        os.write(fd, secret)
        chunks = []
        try:
            while True:
                chunk = os.read(fd, 4096)
                if not chunk:
                    break
                chunks.append(chunk)
        except OSError:
            pass
        _, status = os.waitpid(pid, 0)
        sys.stdout.buffer.write(b''.join(chunks))
        sys.exit(os.waitstatus_to_exitcode(status))
        """;
  }
}
