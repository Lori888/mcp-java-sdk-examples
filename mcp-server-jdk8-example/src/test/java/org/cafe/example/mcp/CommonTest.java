package org.cafe.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.cafe.example.mcp.util.FileUtils;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class CommonTest {

    @Test
    void testGetPromptResultFromJsonFile() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String promptContent = FileUtils.readFileByName(
                Objects.requireNonNull(getClass().getClassLoader().getResource("prompt")).getPath(),
                "prompt-text-from-file.json")[1];
        McpSchema.GetPromptResult promptResult = objectMapper.readValue(promptContent, McpSchema.GetPromptResult.class);
        System.out.println(promptResult);
    }

    @Test
    void testGetPromptResultFromJsonFileAndReplaceArgs() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String promptContent = FileUtils.readFileByName(
                Objects.requireNonNull(getClass().getClassLoader().getResource("prompt")).getPath(),
                "prompt-text-argument-from-file.json")[1];
        System.out.println(promptContent);
        // 使用 String.format() 需要一次性传入所有参数，否则会报错
//        promptContent = String.format(promptContent, "111", "222");
        promptContent = promptContent.replaceFirst("%s", "111");
        promptContent = promptContent.replaceFirst("%s", "222");
        McpSchema.GetPromptResult promptResult = objectMapper.readValue(promptContent, McpSchema.GetPromptResult.class);
        System.out.println(promptResult);
    }
}
