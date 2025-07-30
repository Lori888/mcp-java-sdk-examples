package org.cafe.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.cafe.example.mcp.util.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
class PromptProvider {

    private static final String PROPERTY_PROMPT_DIR = "mcp.prompt.dir";
    private static final String DEFAULT_PROMPT_DIR = "prompt";
    private static final String PROMPT_DIR = System.getProperty(PROPERTY_PROMPT_DIR,
            Objects.requireNonNull(PromptProvider.class.getClassLoader().getResource(DEFAULT_PROMPT_DIR)).getPath()) + File.separator;

    private static final String PROMPT_TYPE_TEXT = "json";
    private static final String PROMPT_TYPE_IMAGE = "image";
    private static final String PROMPT_TYPE_TEXT_KEYWORD = "\"resource\":";
    private static final String PROMPT_ARGUMENT_PLACEHOLDER = "%s";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<McpServerFeatures.AsyncPromptSpecification> allAsyncPrompts() throws IOException {
        List<McpServerFeatures.AsyncPromptSpecification> asyncPromptSpecifications = new ArrayList<>();
        allSyncPrompts().forEach(syncPromptSpecification ->
                asyncPromptSpecifications.add(McpServerFeatures.AsyncPromptSpecification.fromSync(syncPromptSpecification)));
        return asyncPromptSpecifications;
    }

    public List<McpServerFeatures.SyncPromptSpecification> allSyncPrompts() throws IOException {
        List<McpServerFeatures.SyncPromptSpecification> list = new ArrayList<>();
        // Text Content
        list.add(buildTextPrompt());
        // Text Content with argument
        list.add(buildTextPromptWithArgument());
        // Embedded Resources
        list.add(buildEmbeddedResourcesPrompt());
        // Image Content
        list.add(buildImagePrompt());

        // add all from prompt-list.json
        addPromptsFromFile(list);

        return list;
    }

    @NotNull
    private static McpServerFeatures.SyncPromptSpecification buildTextPrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt("test-prompt", "Test Prompt", Collections.emptyList());
        return new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, req) -> new McpSchema.GetPromptResult("Test prompt description",
                        Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));
    }

    @NotNull
    private static McpServerFeatures.SyncPromptSpecification buildTextPromptWithArgument() {
        McpSchema.PromptArgument arg1 = new McpSchema.PromptArgument("arg1", "First argument", true);
        McpSchema.PromptArgument arg2 = new McpSchema.PromptArgument("arg2", "Second argument", false);
        McpSchema.Prompt promptWithArgument = new McpSchema.Prompt("test-prompt-argument", "A test prompt", Arrays.asList(arg1, arg2));
        return new McpServerFeatures.SyncPromptSpecification(promptWithArgument,
                (exchange, req) -> new McpSchema.GetPromptResult("Test prompt with argument description",
                        Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT,
                                new McpSchema.TextContent(String.format("Test prompt: arg1: %s, arg2: %s",
                                        req.getArguments().get("arg1") == null ? "" : req.getArguments().get("arg1"),
                                        req.getArguments().get("arg2") == null ? "" : req.getArguments().get("arg2")))))));
    }

    @NotNull
    private static McpServerFeatures.SyncPromptSpecification buildEmbeddedResourcesPrompt() {
        McpSchema.TextResourceContents resourceContents = new McpSchema.TextResourceContents("resource://embeddedResource",
                "text/plain", "Sample resource content");
        McpSchema.EmbeddedResource embeddedResource = new McpSchema.EmbeddedResource(Collections.singletonList(McpSchema.Role.USER), null, resourceContents);

        McpSchema.Prompt prompt = new McpSchema.Prompt("test-prompt-embeddedResource", "Test Embedded Resources Prompt", Collections.emptyList());
        return new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, req) -> new McpSchema.GetPromptResult("Test Embedded Resources prompt description",
                        Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.USER, embeddedResource))));
    }

    @NotNull
    private static McpServerFeatures.SyncPromptSpecification buildImagePrompt() {
        // 注意：image data 不要包含 data:image/png;base64,
        String filePath = Objects.requireNonNull(PromptProvider.class.getClassLoader().getResource("imageBase64.txt")).getPath();
        String imageDataBase64 = FileUtils.readFile(filePath);
        McpSchema.ImageContent imageContent = new McpSchema.ImageContent(Collections.singletonList(McpSchema.Role.USER), null, imageDataBase64, "image/png");
        McpSchema.Prompt prompt = new McpSchema.Prompt("test-prompt-image", "Test Image Prompt", Collections.emptyList());
        return new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, req) -> new McpSchema.GetPromptResult("Test Embedded Resources prompt description",
                        Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.USER, imageContent))));
    }

    private void addPromptsFromFile(List<McpServerFeatures.SyncPromptSpecification> list) throws JsonProcessingException {
        String promptListFile = PROMPT_DIR + "prompt-list.json";
        McpSchema.ListPromptsResult listPrompts = objectMapper.readValue(FileUtils.readFile(promptListFile),
                McpSchema.ListPromptsResult.class);
        listPrompts.getPrompts().forEach(prompt -> {
            // 根据文件名读取文件：判断文件类型、根据类型生成对应的PromptSpecification
            String[] promptContent = FileUtils.readFileByName(PROMPT_DIR, prompt.getName());
            if (promptContent.length == 2) {
                // TODO embedded resource
                if (promptContent[0].equals(PROMPT_TYPE_TEXT) && !promptContent[1].toLowerCase().contains(PROMPT_TYPE_TEXT_KEYWORD)) {
                    McpServerFeatures.SyncPromptSpecification promptSpec = new McpServerFeatures.SyncPromptSpecification(prompt,
                            (exchange, req) -> {
                                StringBuffer promptContentBuffer = new StringBuffer(promptContent[1]);
                                if (!prompt.getArguments().isEmpty()) {
                                    prompt.getArguments().forEach(argument -> {
                                        String value = req.getArguments().get(argument.getName()) == null ?
                                                "" : (String) req.getArguments().get(argument.getName());
                                        promptContentBuffer.replace(promptContentBuffer.indexOf(PROMPT_ARGUMENT_PLACEHOLDER),
                                                promptContentBuffer.indexOf(PROMPT_ARGUMENT_PLACEHOLDER) + PROMPT_ARGUMENT_PLACEHOLDER.length(),
                                                value);
                                    });
                                }
                                try {
                                    return objectMapper.readValue(promptContentBuffer.toString(), McpSchema.GetPromptResult.class);
                                } catch (JsonProcessingException e) {
                                    log.error("Error read prompt file: " + prompt.getName(), e);
                                }
                                return null;
                            });
                    list.add(promptSpec);
                } else if (promptContent[0].contains(PROMPT_TYPE_IMAGE)) {
                    McpSchema.ImageContent imageContent = new McpSchema.ImageContent(
                            Collections.singletonList(McpSchema.Role.USER), null, promptContent[1], promptContent[0]);
                    McpServerFeatures.SyncPromptSpecification promptSpec = new McpServerFeatures.SyncPromptSpecification(prompt,
                            (exchange, req) -> new McpSchema.GetPromptResult(prompt.getDescription(),
                                    Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.USER, imageContent))));
                    list.add(promptSpec);
                }
            } else {
                log.error("Not found prompt file: " + prompt.getName());
            }
        });
    }
}
