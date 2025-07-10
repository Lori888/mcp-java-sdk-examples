package org.cafe.example.mcp.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * (partly copy from org.springframework.ai.util.json.schema.JsonSchemaGenerator in spring-ai-model-1.0.0-M7.jar
 * and changed a little)
 */
public final class JsonSchemaGenerator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    /**
     * To ensure consistency and robustness across different model providers, all
     * properties in the JSON Schema are considered required by default. This behavior can
     * be overridden by setting the {@link ToolParam#required()},
     * {@link JsonProperty#required()}} annotation.
     */
    private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

    private static final SchemaGenerator SUBTYPE_SCHEMA_GENERATOR;

    /*
     * Initialize JSON Schema generators.
     */
    static {
        SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .with(Option.PLAIN_DEFINITION_KEYS);

        SchemaGeneratorConfig subtypeSchemaGeneratorConfig = schemaGeneratorConfigBuilder
                .without(Option.SCHEMA_VERSION_INDICATOR)
                .build();
        SUBTYPE_SCHEMA_GENERATOR = new SchemaGenerator(subtypeSchemaGeneratorConfig);
    }

    private JsonSchemaGenerator() {
    }

    /**
     * Generate a JSON Schema for a method's input parameters.
     */
    public static String generateForMethodInput(Method method, SchemaOption... schemaOptions) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();

        for (int i = 0; i < method.getParameterCount(); i++) {
            String parameterName = method.getParameters()[i].getName();
            Type parameterType = method.getGenericParameterTypes()[i];
            if (isMethodParameterRequired(method, i)) {
                required.add(parameterName);
            }
            ObjectNode parameterNode = SUBTYPE_SCHEMA_GENERATOR.generateSchema(parameterType);
            String parameterDescription = getMethodParameterDescription(method, i);
            if (StringUtils.hasText(parameterDescription)) {
                parameterNode.put("description", parameterDescription);
            }
            properties.set(parameterName, parameterNode);
        }

        ArrayNode requiredArray = schema.putArray("required");
        required.forEach(requiredArray::add);

        processSchemaOptions(schemaOptions, schema);

        return schema.toPrettyString();
    }

    /**
     * Determines whether a property is required based on the presence of a series of *
     * annotations.
     *
     * <p>
     * <ul>
     * <li>{@code @ToolParam(required = ...)}</li>
     * <li>{@code @JsonProperty(required = ...)}</li>
     * <li>{@code @Schema(required = ...)}</li>
     * <li>{@code @Nullable}</li>
     * </ul>
     * <p>
     *
     * If none of these annotations are present, the default behavior is to consider the *
     * property as required.
     */
    private static boolean isMethodParameterRequired(Method method, int index) {
        Parameter parameter = method.getParameters()[index];

        ToolParam toolParamAnnotation = parameter.getAnnotation(ToolParam.class);
        if (toolParamAnnotation != null) {
            return toolParamAnnotation.required();
        }

        JsonProperty propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
        if (propertyAnnotation != null) {
            return propertyAnnotation.required();
        }

        return PROPERTY_REQUIRED_BY_DEFAULT;
    }

    /**
     * Determines a property description based on the presence of a series of annotations.
     *
     * <p>
     * <ul>
     * <li>{@code @ToolParam(description = ...)}</li>
     * <li>{@code @JsonPropertyDescription(...)}</li>
     * <li>{@code @Schema(description = ...)}</li>
     * </ul>
     * <p>
     */
    private static String getMethodParameterDescription(Method method, int index) {
        Parameter parameter = method.getParameters()[index];

        ToolParam toolParamAnnotation = parameter.getAnnotation(ToolParam.class);
        if (toolParamAnnotation != null && StringUtils.hasText(toolParamAnnotation.description())) {
            return toolParamAnnotation.description();
        }

        JsonPropertyDescription jacksonAnnotation = parameter.getAnnotation(JsonPropertyDescription.class);
        if (jacksonAnnotation != null && StringUtils.hasText(jacksonAnnotation.value())) {
            return jacksonAnnotation.value();
        }

        return null;
    }

    private static void processSchemaOptions(SchemaOption[] schemaOptions, ObjectNode schema) {
        if (Stream.of(schemaOptions)
                .noneMatch(option -> option == SchemaOption.ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT)) {
            schema.put("additionalProperties", false);
        }
        if (Stream.of(schemaOptions).anyMatch(option -> option == SchemaOption.UPPER_CASE_TYPE_VALUES)) {
            convertTypeValuesToUpperCase(schema);
        }
    }

    // Based on the method in ModelOptionsUtils.
    public static void convertTypeValuesToUpperCase(ObjectNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isObject()) {
                    convertTypeValuesToUpperCase((ObjectNode) value);
                }
                else if (value.isArray()) {
                    value.elements().forEachRemaining(element -> {
                        if (element.isObject() || element.isArray()) {
                            convertTypeValuesToUpperCase((ObjectNode) element);
                        }
                    });
                }
                else if (value.isTextual() && entry.getKey().equals("type")) {
                    String oldValue = node.get("type").asText();
                    node.put("type", oldValue.toUpperCase());
                }
            });
        }
        else if (node.isArray()) {
            node.elements().forEachRemaining(element -> {
                if (element.isObject() || element.isArray()) {
                    convertTypeValuesToUpperCase((ObjectNode) element);
                }
            });
        }
    }

    /**
     * Options for generating JSON Schemas.
     */
    public enum SchemaOption {

        /**
         * Allow an object to contain additional key/values not defined in the schema.
         */
        ALLOW_ADDITIONAL_PROPERTIES_BY_DEFAULT,

        /**
         * Convert all "type" values to upper case.
         */
        UPPER_CASE_TYPE_VALUES;

    }
}
