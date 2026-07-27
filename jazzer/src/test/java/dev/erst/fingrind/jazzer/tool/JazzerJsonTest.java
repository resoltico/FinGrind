package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WireValue;
import dev.erst.fingrind.jazzer.tool.external.InaccessibleWireValueFixtures;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.util.LinkedNode;

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
                SourceChannel.CLI));
    Path jsonPath = tempDirectory.resolve("replay-expectation.json");

    JazzerJson.write(jsonPath, expectation);
    String json = Files.readString(jsonPath);
    ReplayExpectation reloaded = JazzerJson.read(jsonPath, ReplayExpectation.class);

    assertTrue(json.contains("\"outcomeKind\" : \"success\""));
    assertTrue(json.contains("\"type\" : \"CLI_REQUEST\""));
    assertTrue(json.contains("\"sourceChannel\" : \"CLI\""));
    assertEquals(expectation, reloaded);
    assertInstanceOf(CliRequestReplayDetails.class, reloaded.details());
  }

  @Test
  void write_never_replaces_an_existing_json_artifact() throws IOException {
    Path jsonPath = tempDirectory.resolve("existing.json");
    Files.writeString(jsonPath, "{\"preserved\":true}");

    assertThrows(IOException.class, () -> JazzerJson.write(jsonPath, SourceChannel.CLI));

    assertEquals("{\"preserved\":true}", Files.readString(jsonPath));
  }

  @Test
  void write_refuses_a_symbolic_link_destination_without_touching_its_referent()
      throws IOException {
    Path referent = tempDirectory.resolve("referent.json");
    Files.writeString(referent, "{\"preserved\":true}");
    Path linkedDestination = tempDirectory.resolve("linked.json");
    createSymbolicLinkOrSkip(linkedDestination, referent);

    assertThrows(IOException.class, () -> JazzerJson.write(linkedDestination, SourceChannel.CLI));

    assertEquals("{\"preserved\":true}", Files.readString(referent));
  }

  @Test
  void sourceChannel_roundTrips_as_top_level_wire_value() throws IOException {
    Path sourceChannelPath = tempDirectory.resolve("source-channel.json");

    JazzerJson.write(sourceChannelPath, SourceChannel.CLI);
    SourceChannel reloaded = JazzerJson.read(sourceChannelPath, SourceChannel.class);

    assertEquals("\"CLI\"", Files.readString(sourceChannelPath).trim());
    assertEquals("\"CLI\"", JazzerJson.toJson(SourceChannel.CLI).trim());
    assertEquals(SourceChannel.CLI, reloaded);
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
    assertThrows(NullPointerException.class, () -> JazzerJson.write(nullValue(), "value"));
    assertThrows(NullPointerException.class, () -> JazzerJson.write(tempDirectory, nullValue()));
    assertThrows(
        NullPointerException.class, () -> JazzerJson.read(nullValue(), ReplayExpectation.class));
    assertThrows(NullPointerException.class, () -> JazzerJson.read(tempDirectory, nullValue()));
    assertThrows(
        NullPointerException.class,
        () -> JazzerJson.readResource(nullValue(), ReplayOutcomeKind.class));
    assertThrows(
        NullPointerException.class, () -> JazzerJson.readResource("/missing", nullValue()));
    assertThrows(NullPointerException.class, () -> JazzerJson.toJson(nullValue()));
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
    assertEquals(Enum.class, new JazzerJson.FinGrindEnumSerializer().handledType());
    assertEquals(Enum.class, new JazzerJson.FinGrindEnumDeserializer().handledType());

    IllegalStateException inaccessibleFactory =
        assertThrows(
            IllegalStateException.class,
            () ->
                JazzerJson.parseWireValueEnum(
                    InaccessibleWireValueFixtures.inaccessibleWireValueEnumClass(),
                    "alpha",
                    nullValue()));
    assertTrue(
        String.valueOf(inaccessibleFactory.getMessage()).contains("Unable to access Jazzer JSON"));
  }

  @Test
  void jazzerJson_private_concrete_wire_value_helpers_report_handled_types_and_unexpected_tokens()
      throws Exception {
    JazzerJson.FinGrindConcreteWireValueSerializer<SourceChannel> serializer =
        new JazzerJson.FinGrindConcreteWireValueSerializer<>(SourceChannel.class);
    assertEquals(SourceChannel.class, serializer.handledType());

    JazzerJson.FinGrindConcreteWireValueDeserializer<SourceChannel> deserializer =
        new JazzerJson.FinGrindConcreteWireValueDeserializer<>(SourceChannel.class);
    assertEquals(SourceChannel.class, deserializer.handledType());

    DeserializationContext unexpectedTokenContext =
        new WireValueReturnDeserializationContext(SourceChannel.class);
    try (var parser = JazzerJson.jsonMapper().createParser("{}")) {
      parser.nextToken();
      assertEquals(SourceChannel.CLI, deserializer.deserialize(parser, unexpectedTokenContext));
    }
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_contextual_resolution_and_unexpected_tokens()
      throws Exception {
    JazzerJson.FinGrindEnumDeserializer deserializer = new JazzerJson.FinGrindEnumDeserializer();
    DeserializationContext contextualEnumContext =
        new ThrowingDeserializationContext(ReplayOutcomeKind.class);
    BeanProperty enumProperty = enumProperty(ReplayOutcomeKind.class);

    assertEquals(ReplayOutcomeKind.class, deserializer.requireEnumType(contextualEnumContext));
    assertEquals(
        ReplayOutcomeKind.class,
        JazzerJson.FinGrindEnumDeserializer.contextualEnumType(
            new ThrowingDeserializationContext(null), enumProperty));
    assertNull(
        JazzerJson.FinGrindEnumDeserializer.contextualEnumType(
            new ThrowingDeserializationContext(null), null));
    assertNull(
        JazzerJson.FinGrindEnumDeserializer.contextualEnumType(
            new ThrowingDeserializationContext(null), enumProperty(String.class)));

    DeserializationContext unexpectedTokenContext =
        new ReturnValueDeserializationContext(ReplayOutcomeKind.class);
    try (var parser = JazzerJson.jsonMapper().createParser("{}")) {
      parser.nextToken();
      assertEquals(
          ReplayOutcomeKind.SUCCESS, deserializer.deserialize(parser, unexpectedTokenContext));
    }
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_weird_string_branches() throws Exception {
    DeserializationContext context = new ThrowingDeserializationContext(ReplayOutcomeKind.class);

    Exception missingWireValue =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseEnum(FinGrindEnumWithoutWireValue.class, "ALPHA", context));
    assertTrue(
        String.valueOf(rootCause(missingWireValue).getMessage())
            .contains("must implement WireValue"));

    Exception unsupportedForeignValue =
        assertThrows(Exception.class, () -> JazzerJson.parseEnum(Month.class, "OMEGA", context));
    assertTrue(
        String.valueOf(rootCause(unsupportedForeignValue).getMessage())
            .contains("Unsupported enum value"));

    Exception missingFactory =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueEnum(MissingFactoryEnum.class, "alpha", context));
    assertTrue(
        String.valueOf(rootCause(missingFactory).getMessage()).contains("must declare static"));

    Exception runtimeFailure =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueEnum(RuntimeFailureEnum.class, "alpha", context));
    assertTrue(
        String.valueOf(rootCause(runtimeFailure).getMessage()).contains("runtime decode failure"));
  }

  @Test
  void jazzerJson_private_enum_helpers_cover_non_throwing_handler_paths() throws Exception {
    DeserializationContext context = new ReturnValueDeserializationContext(ReplayOutcomeKind.class);

    assertEquals(
        FinGrindEnumWithoutWireValue.ALPHA,
        JazzerJson.parseEnum(FinGrindEnumWithoutWireValue.class, "ALPHA", context));
    assertEquals(Month.JANUARY, JazzerJson.parseEnum(Month.class, "OMEGA", context));
    assertEquals(
        MissingFactoryEnum.ALPHA,
        JazzerJson.parseWireValueEnum(MissingFactoryEnum.class, "alpha", context));
    assertEquals(
        RuntimeFailureEnum.ALPHA,
        JazzerJson.parseWireValueEnum(RuntimeFailureEnum.class, "alpha", context));
  }

  @Test
  void jazzerJson_private_concrete_wire_value_helpers_cover_weird_string_branches()
      throws Exception {
    DeserializationContext context = new ThrowingDeserializationContext(SourceChannel.class);

    Exception missingFactory =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueType(MissingFactoryWireValue.class, "alpha", context));
    assertTrue(
        String.valueOf(rootCause(missingFactory).getMessage()).contains("must declare static"));

    Exception wrongReturn =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueType(WrongReturnWireValue.class, "alpha", context));
    assertTrue(
        String.valueOf(rootCause(wrongReturn).getMessage())
            .contains("must return a WireValue assignable"));

    Exception runtimeFailure =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueType(RuntimeFailureWireValue.class, "alpha", context));
    assertTrue(
        String.valueOf(rootCause(runtimeFailure).getMessage()).contains("runtime decode failure"));

    Exception checkedFailure =
        assertThrows(
            Exception.class,
            () -> JazzerJson.parseWireValueType(CheckedFailureWireValue.class, "alpha", context));
    assertTrue(
        String.valueOf(checkedFailure.getMessage())
            .contains("Failed to decode Jazzer JSON wire value"));

    IllegalStateException inaccessibleFactory =
        assertThrows(
            IllegalStateException.class, () -> parseInaccessibleWireValueType("alpha", context));
    assertTrue(
        String.valueOf(inaccessibleFactory.getMessage()).contains("Unable to access Jazzer JSON"));

    Exception unsupportedValueCarrier =
        assertThrows(Exception.class, () -> JazzerJson.wireValue(new Object()));
    assertTrue(
        String.valueOf(rootCause(unsupportedValueCarrier).getMessage())
            .contains("must implement WireValue or be an enum"));
  }

  @Test
  void jazzerJson_private_concrete_wire_value_helpers_cover_non_throwing_handler_paths()
      throws Exception {
    DeserializationContext context = new WireValueReturnDeserializationContext(SourceChannel.class);

    assertEquals(
        MissingFactoryWireValue.ALPHA,
        JazzerJson.parseWireValueType(MissingFactoryWireValue.class, "alpha", context));
    assertEquals(
        RuntimeFailureWireValue.ALPHA,
        JazzerJson.parseWireValueType(RuntimeFailureWireValue.class, "alpha", context));
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

  private static final class MissingFactoryWireValue implements WireValue {
    private static final MissingFactoryWireValue ALPHA = new MissingFactoryWireValue("alpha");

    private final String wireValue;

    private MissingFactoryWireValue(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }

  @SuppressWarnings("UnusedMethod")
  private static final class WrongReturnWireValue implements WireValue {
    private final String wireValue;

    private WrongReturnWireValue(String wireValue) {
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

  @SuppressWarnings("UnusedMethod")
  private static final class RuntimeFailureWireValue implements WireValue {
    private static final RuntimeFailureWireValue ALPHA = new RuntimeFailureWireValue("alpha");

    private final String wireValue;

    private RuntimeFailureWireValue(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    @SuppressWarnings({"DoNotCallSuggester", "EffectivelyPrivate"})
    public static RuntimeFailureWireValue fromWireValue(String wireValue) {
      java.util.Objects.requireNonNull(wireValue);
      throw new IllegalArgumentException("runtime decode failure");
    }
  }

  @SuppressWarnings("UnusedMethod")
  private static final class CheckedFailureWireValue implements WireValue {
    private final String wireValue;

    private CheckedFailureWireValue(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    @SuppressWarnings({"DoNotCallSuggester", "EffectivelyPrivate"})
    public static CheckedFailureWireValue fromWireValue(String wireValue) throws IOException {
      java.util.Objects.requireNonNull(wireValue);
      throw new IOException("checked decode failure");
    }
  }

  private static BeanProperty enumProperty(Class<?> enumType) {
    return (BeanProperty)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {BeanProperty.class},
            (proxy, method, arguments) -> {
              return switch (method.getName()) {
                case "getType" -> JazzerJson.jsonMapper().constructType(enumType);
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

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private abstract static class TestDeserializationContext
      extends tools.jackson.databind.deser.DeserializationContextExt {
    private TestDeserializationContext(@Nullable Class<?> contextualType) {
      super(
          JazzerJson.jsonMapper().tokenStreamFactory(),
          tools.jackson.databind.deser.BeanDeserializerFactory.instance,
          new tools.jackson.databind.deser.DeserializerCache(),
          JazzerJson.jsonMapper().deserializationConfig(),
          null,
          JazzerJson.jsonMapper().getInjectableValues());
      if (contextualType != null) {
        _currentType =
            new LinkedNode<>(JazzerJson.jsonMapper().constructType(contextualType), null);
      }
    }

    @Override
    public tools.jackson.databind.deser.ReadableObjectId findObjectId(
        Object id,
        com.fasterxml.jackson.annotation.ObjectIdGenerator<?> generator,
        com.fasterxml.jackson.annotation.ObjectIdResolver resolver) {
      throw new UnsupportedOperationException(
          "Object id resolution is not used in JazzerJson tests.");
    }

    @Override
    public void checkUnresolvedObjectId() {}

    @Override
    public tools.jackson.databind.ValueDeserializer<Object> deserializerInstance(
        tools.jackson.databind.introspect.Annotated annotated, Object deserDef) {
      throw new UnsupportedOperationException(
          "Deserializer instantiation is not used in JazzerJson tests.");
    }
  }

  @SuppressWarnings("unchecked")
  private static class ReturnValueDeserializationContext extends TestDeserializationContext {
    private ReturnValueDeserializationContext(@Nullable Class<?> contextualType) {
      super(contextualType);
    }

    @Override
    public Object handleWeirdStringValue(
        Class<?> targetType, String value, String message, Object... arguments) {
      java.util.Objects.requireNonNull(targetType);
      java.util.Objects.requireNonNull(value);
      java.util.Objects.requireNonNull(message);
      java.util.Objects.requireNonNull(arguments);
      return (Enum<?>) targetType.getEnumConstants()[0];
    }

    @Override
    public Object handleUnexpectedToken(Class<?> targetType, tools.jackson.core.JsonParser parser) {
      java.util.Objects.requireNonNull(targetType);
      java.util.Objects.requireNonNull(parser);
      return ReplayOutcomeKind.SUCCESS;
    }
  }

  @SuppressWarnings("unchecked")
  private static final class WireValueReturnDeserializationContext
      extends ReturnValueDeserializationContext {
    private WireValueReturnDeserializationContext(@Nullable Class<?> contextualType) {
      super(contextualType);
    }

    @Override
    public Object handleWeirdStringValue(
        Class<?> targetType, String value, String message, Object... arguments) {
      java.util.Objects.requireNonNull(targetType);
      return switch (targetType.getName()) {
        case "dev.erst.fingrind.core.SourceChannel" -> SourceChannel.CLI;
        case "dev.erst.fingrind.jazzer.tool.JazzerJsonTest$MissingFactoryWireValue" ->
            MissingFactoryWireValue.ALPHA;
        case "dev.erst.fingrind.jazzer.tool.JazzerJsonTest$RuntimeFailureWireValue" ->
            RuntimeFailureWireValue.ALPHA;
        default -> super.handleWeirdStringValue(targetType, value, message, arguments);
      };
    }

    @Override
    public Object handleUnexpectedToken(Class<?> targetType, tools.jackson.core.JsonParser parser) {
      java.util.Objects.requireNonNull(targetType);
      if (targetType == SourceChannel.class) {
        return SourceChannel.CLI;
      }
      return super.handleUnexpectedToken(targetType, parser);
    }
  }

  @SuppressWarnings("unchecked")
  private static final class ThrowingDeserializationContext extends TestDeserializationContext {
    private ThrowingDeserializationContext(@Nullable Class<?> contextualType) {
      super(contextualType);
    }

    @Override
    @SuppressWarnings("AnnotateFormatMethod")
    public Object handleWeirdStringValue(
        Class<?> targetType, String value, String message, Object... arguments) {
      throw new IllegalArgumentException(message.formatted(arguments));
    }

    @Override
    public Object handleUnexpectedToken(Class<?> targetType, tools.jackson.core.JsonParser parser) {
      throw new IllegalStateException("Unexpected token for " + targetType.getName());
    }
  }

  private static WireValue parseInaccessibleWireValueType(
      String wireValue, DeserializationContext deserializationContext) {
    return parseWireValueFixtureType(
        InaccessibleWireValueFixtures.inaccessibleWireValueTypeClass(),
        wireValue,
        deserializationContext);
  }

  private static <T extends WireValue> T parseWireValueFixtureType(
      Class<T> wireValueType, String wireValue, DeserializationContext deserializationContext) {
    return JazzerJson.parseWireValueType(wireValueType, wireValue, deserializationContext);
  }

  @SuppressWarnings({"NullAway", "TypeParameterUnusedInFormals"})
  private static <T> T nullValue() {
    return null;
  }

  private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | IOException unsupported) {
      Assumptions.assumeTrue(
          false, "Symbolic-link refusal coverage requires local symbolic-link support.");
    }
  }
}
