package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WireValue;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.json.JsonMapper;

/** Pins the local Jazzer JSON contract that Jackson 3 still honors the annotation namespace. */
class JazzerJsonTest {
  @TempDir Path tempDirectory;

  @Test
  void replayExpectation_roundTripsParsedReplayDetailsWithWireValues() throws IOException {
    ReplayExpectation expectation =
        new ReplayExpectation(
            ReplayOutcomeKind.SUCCESS,
            ReplayOutcome.SUCCESS_MESSAGE,
            new CliRequestReplayDetails(
                new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false),
                ActorType.AGENT,
                SourceChannel.CLI));
    Path jsonPath = tempDirectory.resolve("replay-expectation.json");

    JazzerJson.write(jsonPath, expectation);
    String json = Files.readString(jsonPath);
    ReplayExpectation reloaded = JazzerJson.read(jsonPath, ReplayExpectation.class);

    assertTrue(json.contains("\"outcomeKind\" : \"success\""));
    assertTrue(json.contains("\"type\" : \"CLI_REQUEST\""));
    assertTrue(json.contains("\"actorType\" : \"AGENT\""));
    assertEquals(expectation, reloaded);
    assertInstanceOf(CliRequestReplayDetails.class, reloaded.details());
  }

  @Test
  void replayExpectation_roundTripsUnparsedReplayDetails() throws IOException {
    ReplayExpectation expectation =
        new ReplayExpectation(
            ReplayOutcomeKind.EXPECTED_INVALID,
            "Missing required field: provenance",
            new UnparsedCliRequestReplayDetails());
    Path jsonPath = tempDirectory.resolve("unparsed-replay-expectation.json");

    JazzerJson.write(jsonPath, expectation);
    ReplayExpectation reloaded = JazzerJson.read(jsonPath, ReplayExpectation.class);

    assertEquals(expectation, reloaded);
    assertInstanceOf(UnparsedCliRequestReplayDetails.class, reloaded.details());
  }

  @Test
  void readResource_and_null_guards_fail_fast() {
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "write", new Class<?>[] {Path.class, Object.class}, new Object[] {null, "value"}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "write",
                new Class<?>[] {Path.class, Object.class},
                new Object[] {tempDirectory, null}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "read",
                new Class<?>[] {Path.class, Class.class},
                new Object[] {null, ReplayExpectation.class}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "read",
                new Class<?>[] {Path.class, Class.class},
                new Object[] {tempDirectory, null}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "readResource",
                new Class<?>[] {String.class, Class.class},
                new Object[] {null, ReplayOutcomeKind.class}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeJsonMethod(
                "readResource",
                new Class<?>[] {String.class, Class.class},
                new Object[] {"/missing", null}));
    assertThrows(
        NullPointerException.class,
        () -> invokeJsonMethod("toJson", new Class<?>[] {Object.class}, new Object[] {null}));
    IOException missingResource =
        assertThrows(
            IOException.class,
            () -> JazzerJson.readResource("/missing-resource.json", ReplayOutcomeKind.class));
    assertTrue(String.valueOf(missingResource.getMessage()).contains("Missing classpath resource"));
  }

  @Test
  void topLevelEnums_roundTrip_and_reject_invalid_shapes() throws IOException {
    Path successPath = tempDirectory.resolve("success.json");
    Files.writeString(successPath, "\"success\"");
    Path foreignPath = tempDirectory.resolve("foreign.json");
    Files.writeString(foreignPath, "\"APRIL\"");
    Path invalidTokenPath = tempDirectory.resolve("invalid-token.json");
    Files.writeString(invalidTokenPath, "5");
    Path unresolvedEnumPath = tempDirectory.resolve("unresolved-enum.json");
    Files.writeString(unresolvedEnumPath, "\"success\"");

    assertEquals(ReplayOutcomeKind.SUCCESS, JazzerJson.read(successPath, ReplayOutcomeKind.class));
    assertEquals(Month.APRIL, JazzerJson.read(foreignPath, Month.class));
    assertEquals("\"success\"", JazzerJson.toJson(ReplayOutcomeKind.SUCCESS).trim());
    assertEquals("\"APRIL\"", JazzerJson.toJson(Month.APRIL).trim());

    Exception invalidToken =
        assertThrows(
            Exception.class, () -> JazzerJson.read(invalidTokenPath, ReplayOutcomeKind.class));
    assertFalse(String.valueOf(rootCause(invalidToken).getMessage()).isBlank());
    Exception unresolvedEnum =
        assertThrows(Exception.class, () -> JazzerJson.read(unresolvedEnumPath, Enum.class));
    assertTrue(
        String.valueOf(rootCause(unresolvedEnum).getMessage())
            .contains("Unable to resolve enum target type"));
  }

  @Test
  void jazzerJson_enforces_FinGrind_wire_value_contracts() throws IOException {
    Path missingFactoryPath = tempDirectory.resolve("missing-factory.json");
    Files.writeString(missingFactoryPath, "{\"value\":\"alpha\"}");
    Path wrongReturnPath = tempDirectory.resolve("wrong-return.json");
    Files.writeString(wrongReturnPath, "{\"value\":\"alpha\"}");
    Path runtimeFailurePath = tempDirectory.resolve("runtime-failure.json");
    Files.writeString(runtimeFailurePath, "{\"value\":\"alpha\"}");
    Path checkedFailurePath = tempDirectory.resolve("checked-failure.json");
    Files.writeString(checkedFailurePath, "{\"value\":\"alpha\"}");
    Path foreignFailurePath = tempDirectory.resolve("foreign-failure.json");
    Files.writeString(foreignFailurePath, "{\"value\":\"OMEGA\"}");

    Exception missingWireValue =
        assertThrows(
            Exception.class,
            () -> JazzerJson.toJson(new FinGrindEnumHolder(FinGrindEnumWithoutWireValue.ALPHA)));
    assertTrue(
        String.valueOf(rootCause(missingWireValue).getMessage())
            .contains("must implement WireValue"));
    Exception blankWireValue =
        assertThrows(
            Exception.class,
            () -> JazzerJson.toJson(new BlankWireValueEnumHolder(BlankWireValueEnum.ALPHA)));
    assertTrue(String.valueOf(rootCause(blankWireValue).getMessage()).contains("non-blank String"));
    Exception nullWireValue =
        assertThrows(
            Exception.class,
            () -> JazzerJson.toJson(new NullWireValueEnumHolder(NullWireValueEnum.ALPHA)));
    assertTrue(String.valueOf(rootCause(nullWireValue).getMessage()).contains("non-blank String"));
    Exception missingFactory =
        assertThrows(
            Exception.class, () -> JazzerJson.read(missingFactoryPath, MissingFactoryHolder.class));
    assertTrue(
        String.valueOf(rootCause(missingFactory).getMessage()).contains("must declare static"));
    Exception wrongReturn =
        assertThrows(
            Exception.class, () -> JazzerJson.read(wrongReturnPath, WrongReturnHolder.class));
    assertTrue(
        String.valueOf(rootCause(wrongReturn).getMessage()).contains("must return an enum value"));
    Exception runtimeFailure =
        assertThrows(
            Exception.class, () -> JazzerJson.read(runtimeFailurePath, RuntimeFailureHolder.class));
    assertTrue(
        String.valueOf(rootCause(runtimeFailure).getMessage()).contains("runtime decode failure"));
    Exception checkedFailure =
        assertThrows(
            Exception.class, () -> JazzerJson.read(checkedFailurePath, CheckedFailureHolder.class));
    assertFalse(String.valueOf(rootCause(checkedFailure).getMessage()).isBlank());
    Exception foreignFailure =
        assertThrows(
            Exception.class, () -> JazzerJson.read(foreignFailurePath, ForeignEnumHolder.class));
    assertTrue(
        String.valueOf(rootCause(foreignFailure).getMessage()).contains("Unsupported enum value"));
    assertEquals(BlankWireValueEnum.ALPHA, BlankWireValueEnum.fromWireValue("anything"));
    assertEquals("alpha", WrongReturnTypeEnum.fromWireValue("alpha"));
    assertThrows(IllegalArgumentException.class, () -> RuntimeFailureEnum.fromWireValue("alpha"));
    assertThrows(IOException.class, () -> CheckedFailureEnum.fromWireValue("alpha"));
  }

  @Test
  void jazzerJson_private_enum_helpers_report_handled_types_and_illegal_access() throws Exception {
    assertEquals(
        Enum.class,
        invokePrivateInstanceMethod(
            constructPrivateNestedInstance(
                "dev.erst.fingrind.jazzer.tool.JazzerJson$FinGrindEnumSerializer"),
            "handledType"));
    assertEquals(
        Enum.class,
        invokePrivateInstanceMethod(
            constructPrivateNestedInstance(
                "dev.erst.fingrind.jazzer.tool.JazzerJson$FinGrindEnumDeserializer"),
            "handledType"));

    IllegalStateException inaccessibleFactory =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivateStaticMethod(
                    "parseWireValueEnum",
                    new Class<?>[] {Class.class, String.class, DeserializationContext.class},
                    new Object[] {
                      Class.forName(
                          "dev.erst.fingrind.jazzer.tool.external.InaccessibleWireValueEnum"),
                      "alpha",
                      null
                    }));
    assertTrue(
        String.valueOf(inaccessibleFactory.getMessage()).contains("Unable to access Jazzer JSON"));
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_contextual_resolution_and_unexpected_tokens()
      throws Exception {
    Object deserializer =
        constructPrivateNestedInstance(
            "dev.erst.fingrind.jazzer.tool.JazzerJson$FinGrindEnumDeserializer");
    DeserializationContext contextualEnumContext =
        deserializationContext("\"success\"", ReplayOutcomeKind.class);
    BeanProperty enumProperty = enumProperty(ReplayOutcomeKind.class);

    assertEquals(
        ReplayOutcomeKind.class,
        invokePrivateInstanceMethod(
            deserializer,
            "requireEnumType",
            new Class<?>[] {DeserializationContext.class},
            new Object[] {contextualEnumContext}));
    assertEquals(
        ReplayOutcomeKind.class,
        invokePrivateInstanceMethod(
            deserializer,
            "contextualEnumType",
            new Class<?>[] {DeserializationContext.class, BeanProperty.class},
            new Object[] {deserializationContext("\"success\"", null), enumProperty}));
    assertNull(
        invokePrivateInstanceMethod(
            deserializer,
            "contextualEnumType",
            new Class<?>[] {DeserializationContext.class, BeanProperty.class},
            new Object[] {deserializationContext("\"success\"", null), null}));
    assertNull(
        invokePrivateInstanceMethod(
            deserializer,
            "contextualEnumType",
            new Class<?>[] {DeserializationContext.class, BeanProperty.class},
            new Object[] {
              deserializationContext("\"success\"", null), enumProperty(String.class)
            }));

    DeserializationContext unexpectedTokenContext =
        new ReturnValueDeserializationContext(
            deserializationContext("{}", ReplayOutcomeKind.class));
    try (var parser = jsonMapper().createParser("{}")) {
      parser.nextToken();
      assertEquals(
          ReplayOutcomeKind.SUCCESS,
          invokePrivateInstanceMethod(
              deserializer,
              "deserialize",
              new Class<?>[] {
                Class.forName("tools.jackson.core.JsonParser"), DeserializationContext.class
              },
              new Object[] {parser, unexpectedTokenContext}));
    }
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_weird_string_branches() throws Exception {
    DeserializationContext context = deserializationContext("\"alpha\"", ReplayOutcomeKind.class);

    Exception missingWireValue =
        assertThrows(
            Exception.class,
            () ->
                invokePrivateStaticMethod(
                    "parseEnum",
                    new Class<?>[] {Class.class, String.class, DeserializationContext.class},
                    new Object[] {FinGrindEnumWithoutWireValue.class, "ALPHA", context}));
    assertTrue(
        String.valueOf(rootCause(missingWireValue).getMessage())
            .contains("must implement WireValue"));

    Exception unsupportedForeignValue =
        assertThrows(
            Exception.class,
            () ->
                invokePrivateStaticMethod(
                    "parseEnum",
                    new Class<?>[] {Class.class, String.class, DeserializationContext.class},
                    new Object[] {Month.class, "OMEGA", context}));
    assertTrue(
        String.valueOf(rootCause(unsupportedForeignValue).getMessage())
            .contains("Unsupported enum value"));

    Exception missingFactory =
        assertThrows(
            Exception.class,
            () ->
                invokePrivateStaticMethod(
                    "parseWireValueEnum",
                    new Class<?>[] {Class.class, String.class, DeserializationContext.class},
                    new Object[] {MissingFactoryEnum.class, "alpha", context}));
    assertTrue(
        String.valueOf(rootCause(missingFactory).getMessage()).contains("must declare static"));

    Exception runtimeFailure =
        assertThrows(
            Exception.class,
            () ->
                invokePrivateStaticMethod(
                    "parseWireValueEnum",
                    new Class<?>[] {Class.class, String.class, DeserializationContext.class},
                    new Object[] {RuntimeFailureEnum.class, "alpha", context}));
    assertTrue(
        String.valueOf(rootCause(runtimeFailure).getMessage()).contains("runtime decode failure"));
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_non_throwing_handler_paths() throws Exception {
    DeserializationContext context =
        new ReturnValueDeserializationContext(
            deserializationContext("\"alpha\"", ReplayOutcomeKind.class));

    assertEquals(
        FinGrindEnumWithoutWireValue.ALPHA,
        invokePrivateStaticMethod(
            "parseEnum",
            new Class<?>[] {Class.class, String.class, DeserializationContext.class},
            new Object[] {FinGrindEnumWithoutWireValue.class, "ALPHA", context}));
    assertEquals(
        Month.JANUARY,
        invokePrivateStaticMethod(
            "parseEnum",
            new Class<?>[] {Class.class, String.class, DeserializationContext.class},
            new Object[] {Month.class, "OMEGA", context}));
    assertEquals(
        MissingFactoryEnum.ALPHA,
        invokePrivateStaticMethod(
            "parseWireValueEnum",
            new Class<?>[] {Class.class, String.class, DeserializationContext.class},
            new Object[] {MissingFactoryEnum.class, "alpha", context}));
    assertEquals(
        RuntimeFailureEnum.ALPHA,
        invokePrivateStaticMethod(
            "parseWireValueEnum",
            new Class<?>[] {Class.class, String.class, DeserializationContext.class},
            new Object[] {RuntimeFailureEnum.class, "alpha", context}));
  }

  private enum FinGrindEnumWithoutWireValue {
    ALPHA
  }

  private enum BlankWireValueEnum implements WireValue {
    ALPHA;

    @Override
    public String wireValue() {
      return " ";
    }

    @SuppressWarnings("EffectivelyPrivate")
    public static BlankWireValueEnum fromWireValue(String wireValue) {
      java.util.Objects.requireNonNull(wireValue);
      return ALPHA;
    }
  }

  private enum NullWireValueEnum implements WireValue {
    ALPHA;

    @Override
    @SuppressWarnings("NullAway")
    public String wireValue() {
      return null;
    }
  }

  private enum MissingFactoryEnum implements WireValue {
    ALPHA("alpha");

    private final String wireValue;

    MissingFactoryEnum(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }

  private enum WrongReturnTypeEnum implements WireValue {
    ALPHA("alpha");

    private final String wireValue;

    WrongReturnTypeEnum(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    @SuppressWarnings("EffectivelyPrivate")
    public static Object fromWireValue(String wireValue) {
      java.util.Objects.requireNonNull(wireValue);
      return wireValue;
    }
  }

  private enum RuntimeFailureEnum implements WireValue {
    ALPHA("alpha");

    private final String wireValue;

    RuntimeFailureEnum(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    @SuppressWarnings({"DoNotCallSuggester", "EffectivelyPrivate"})
    public static RuntimeFailureEnum fromWireValue(String wireValue) {
      java.util.Objects.requireNonNull(wireValue);
      throw new IllegalArgumentException("runtime decode failure");
    }
  }

  private enum CheckedFailureEnum implements WireValue {
    ALPHA("alpha");

    private final String wireValue;

    CheckedFailureEnum(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    @SuppressWarnings({"DoNotCallSuggester", "EffectivelyPrivate"})
    public static CheckedFailureEnum fromWireValue(String wireValue) throws IOException {
      java.util.Objects.requireNonNull(wireValue);
      throw new IOException("checked decode failure");
    }
  }

  private record ForeignEnumHolder(Month value) {}

  private record FinGrindEnumHolder(FinGrindEnumWithoutWireValue value) {}

  private record BlankWireValueEnumHolder(BlankWireValueEnum value) {}

  private record NullWireValueEnumHolder(NullWireValueEnum value) {}

  private record MissingFactoryHolder(MissingFactoryEnum value) {}

  private record WrongReturnHolder(WrongReturnTypeEnum value) {}

  private record RuntimeFailureHolder(RuntimeFailureEnum value) {}

  private record CheckedFailureHolder(CheckedFailureEnum value) {}

  private static Object invokeJsonMethod(
      String methodName, Class<?>[] parameterTypes, Object[] arguments) throws Exception {
    Method method = JazzerJson.class.getDeclaredMethod(methodName, parameterTypes);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static Object constructPrivateNestedInstance(String binaryClassName) throws Exception {
    Constructor<?> constructor = Class.forName(binaryClassName).getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  private static JsonMapper jsonMapper() throws Exception {
    Field mapperField = JazzerJson.class.getDeclaredField("JSON_MAPPER");
    mapperField.setAccessible(true);
    return (JsonMapper) mapperField.get(null);
  }

  private static DeserializationContext deserializationContext(
      String json, @Nullable Class<?> contextualType) throws Exception {
    JsonMapper mapper = jsonMapper();
    try (var parser = mapper.createParser(json)) {
      parser.nextToken();
      Method contextFactory =
          JsonMapper.class
              .getSuperclass()
              .getDeclaredMethod(
                  "_deserializationContext", Class.forName("tools.jackson.core.JsonParser"));
      contextFactory.setAccessible(true);
      DeserializationContext context =
          (DeserializationContext) contextFactory.invoke(mapper, parser);
      if (contextualType == null) {
        return context;
      }
      Field currentTypeField = DeserializationContext.class.getDeclaredField("_currentType");
      currentTypeField.setAccessible(true);
      Class<?> linkedNodeClass = Class.forName("tools.jackson.databind.util.LinkedNode");
      Constructor<?> linkedNodeConstructor =
          linkedNodeClass.getDeclaredConstructor(Object.class, linkedNodeClass);
      linkedNodeConstructor.setAccessible(true);
      currentTypeField.set(
          context, linkedNodeConstructor.newInstance(mapper.constructType(contextualType), null));
      return context;
    }
  }

  private static BeanProperty enumProperty(Class<?> enumType) throws Exception {
    return (BeanProperty)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {BeanProperty.class},
            (proxy, method, arguments) -> {
              return switch (method.getName()) {
                case "getType" -> jsonMapper().constructType(enumType);
                case "isRequired", "isVirtual" -> false;
                case "findAliases" -> List.of();
                case "getMetadata",
                    "getWrapperName",
                    "getMember",
                    "findPropertyFormat",
                    "findFormatOverrides",
                    "findPropertyInclusion",
                    "getAnnotation",
                    "getContextAnnotation",
                    "depositSchemaProperty" ->
                    null;
                case "getFullName" -> "value";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" ->
                    arguments != null
                        && arguments.length == 1
                        && System.identityHashCode(proxy) == System.identityHashCode(arguments[0]);
                case "toString" -> "EnumProperty[" + enumType.getSimpleName() + "]";
                default -> defaultValue(method.getReturnType());
              };
            });
  }

  private static @Nullable Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    return switch (returnType.getName()) {
      case "boolean" -> false;
      case "byte" -> (byte) 0;
      case "short" -> (short) 0;
      case "int" -> 0;
      case "long" -> 0L;
      case "float" -> 0.0f;
      case "double" -> 0.0d;
      case "char" -> '\0';
      default -> null;
    };
  }

  private static Object invokePrivateInstanceMethod(Object target, String methodName)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    try {
      return method.invoke(target);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static Object invokePrivateInstanceMethod(
      Object target, String methodName, Class<?>[] parameterTypes, Object[] arguments)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static Object invokePrivateStaticMethod(
      String methodName, Class<?>[] parameterTypes, Object[] arguments) throws Exception {
    Method method = JazzerJson.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static final class ReturnValueDeserializationContext
      extends tools.jackson.databind.deser.DeserializationContextExt {
    private ReturnValueDeserializationContext(DeserializationContext base) throws Exception {
      super(
          (tools.jackson.core.TokenStreamFactory) reflectedField(base, "_streamFactory"),
          (tools.jackson.databind.deser.DeserializerFactory) reflectedField(base, "_factory"),
          (tools.jackson.databind.deser.DeserializerCache) reflectedField(base, "_cache"),
          (tools.jackson.databind.DeserializationConfig) reflectedField(base, "_config"),
          (tools.jackson.core.FormatSchema) reflectedField(base, "_schema"),
          (tools.jackson.databind.InjectableValues) reflectedField(base, "_injectableValues"));
      _currentType =
          (tools.jackson.databind.util.LinkedNode<tools.jackson.databind.JavaType>)
              reflectedField(base, "_currentType");
      _parser = (tools.jackson.core.JsonParser) reflectedField(base, "_parser");
    }

    @Override
    public Object handleWeirdStringValue(
        Class<?> targetType, String value, String message, Object... arguments) {
      java.util.Objects.requireNonNull(targetType);
      java.util.Objects.requireNonNull(value);
      java.util.Objects.requireNonNull(message);
      java.util.Objects.requireNonNull(arguments);
      return ((Enum<?>[]) targetType.getEnumConstants())[0];
    }

    @Override
    public Object handleUnexpectedToken(Class<?> targetType, tools.jackson.core.JsonParser parser) {
      java.util.Objects.requireNonNull(targetType);
      java.util.Objects.requireNonNull(parser);
      return ReplayOutcomeKind.SUCCESS;
    }
  }

  private static Object reflectedField(Object target, String fieldName) throws Exception {
    Class<?> currentType = target.getClass();
    while (currentType != null) {
      try {
        Field field = currentType.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException ignored) {
        currentType = currentType.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
