package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.code_intelligence.jazzer.third_party.net.bytebuddy.agent.Installer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Covers the project-owned premain bridge used by active Jazzer runs. */
class JazzerPremainAgentTest {
  @Test
  void premain_registersInstrumentationWithByteBuddyInstaller() {
    Instrumentation instrumentation = instrumentationProxy();

    JazzerPremainAgent.premain("", instrumentation);

    assertSame(instrumentation, Installer.getInstrumentation());
  }

  private static Instrumentation instrumentationProxy() {
    return (Instrumentation)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {Instrumentation.class},
            (proxy, method, arguments) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> returnType) {
    if (Void.TYPE.equals(returnType)) {
      return null;
    }
    if (!returnType.isPrimitive()) {
      return returnType.isArray() ? Array.newInstance(returnType.componentType(), 0) : null;
    }
    if (Boolean.TYPE.equals(returnType)) {
      return false;
    }
    if (Character.TYPE.equals(returnType)) {
      return Character.valueOf('\0');
    }
    if (Float.TYPE.equals(returnType)) {
      return 0.0F;
    }
    if (Double.TYPE.equals(returnType)) {
      return 0.0D;
    }
    if (Long.TYPE.equals(returnType)) {
      return 0L;
    }
    return 0;
  }
}
