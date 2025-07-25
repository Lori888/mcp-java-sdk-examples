package org.cafe.example.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PromptProvider {

    private static final String TEST_PROMPT_NAME = "test-prompt";

    public List<McpServerFeatures.SyncPromptSpecification> allSyncPrompts() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(TEST_PROMPT_NAME, "Test Prompt", Collections.emptyList());
        McpServerFeatures.SyncPromptSpecification specification = new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, req) -> new McpSchema.GetPromptResult("Test prompt description",
                        Collections.singletonList(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));
        return Collections.singletonList(specification);
    }

    public List<McpServerFeatures.AsyncPromptSpecification> allAsyncPrompts() {
        List<McpServerFeatures.AsyncPromptSpecification> asyncPromptSpecifications = new ArrayList<>();
        allSyncPrompts().forEach(syncPromptSpecification ->
                asyncPromptSpecifications.add(McpServerFeatures.AsyncPromptSpecification.fromSync(syncPromptSpecification)));
        return asyncPromptSpecifications;
    }
}
