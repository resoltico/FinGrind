package dev.erst.fingrind.jazzer.tool;

import com.code_intelligence.jazzer.third_party.net.bytebuddy.agent.Installer;
import java.lang.instrument.Instrumentation;
import java.util.Objects;

/**
 * Preloads Byte Buddy instrumentation for active Jazzer runs so Java 26 never has to rely on a
 * late external attach before fuzzing begins.
 */
public final class JazzerPremainAgent {
  private JazzerPremainAgent() {}

  /** Publishes instrumentation through Byte Buddy's installer bridge during JVM startup. */
  public static void premain(String agentArgs, Instrumentation instrumentation) {
    Installer.premain(agentArgs, Objects.requireNonNull(instrumentation, "instrumentation"));
  }

  /** Mirrors premain wiring when the agent is attached explicitly for debugging. */
  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    Installer.agentmain(agentArgs, Objects.requireNonNull(instrumentation, "instrumentation"));
  }
}
