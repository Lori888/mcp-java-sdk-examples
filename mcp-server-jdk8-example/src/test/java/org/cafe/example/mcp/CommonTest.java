package org.cafe.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.cafe.example.mcp.util.FileUtils;
import org.cafe.example.mcp.util.JsonSchemaGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void testGetMcpToolDefFromJsonFile() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String toolContent = FileUtils.readFile(
                Objects.requireNonNull(getClass().getClassLoader().getResource("tool/tool-list.json")).getPath());
        System.out.println(toolContent);

//        List<McpSchema.Tool> mcpToolInfoList = objectMapper.readValue(toolContent,
//                objectMapper.getTypeFactory().constructCollectionType(List.class, McpSchema.Tool.class));
        List<McpToolDef> mcpToolInfoList = objectMapper.readValue(toolContent,
                objectMapper.getTypeFactory().constructCollectionType(List.class, McpToolDef.class));
        mcpToolInfoList.forEach(System.out::println);
    }

    @Test
    void testGenerateToolListJsonSchema() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        List<McpToolDef> toolDefList = new ArrayList<>();

        Class clazz = McpTool.class;
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            McpToolDef toolDef = new McpToolDef();
            toolDef.setName(method.getName());
            toolDef.setDescription("");
            toolDef.setTargetBeanClass(clazz.getName());
            toolDef.setTargetMethodName(method.getName());
            String inputSchema = JsonSchemaGenerator.generateForMethodInput(method);
            toolDef.setInputSchema(objectMapper.readValue(inputSchema, McpSchema.JsonSchema.class));
            System.out.println(toolDef);

            toolDefList.add(toolDef);
        }

        String json = objectMapper.writeValueAsString(toolDefList);
        System.out.println(json);

        // 将序列化内容保存为 tool-list.json 文件
        String absoluteFileDir = getClass().getClassLoader().getResource("").getPath();
        FileUtils.writeFile(absoluteFileDir + "tool/tool-list.json", json);
    }
}
