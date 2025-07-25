package org.cafe.example.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SyncMcpClient {

    private final McpClientTransport transport;
    private McpSyncClient syncClient;

    public SyncMcpClient(McpClientTransport transport) {
        this.transport = transport;

        init();
    }

    private void init() {
        syncClient = io.modelcontextprotocol.client.McpClient.sync(transport)
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build()).build();
    }

    public boolean connect() {
        McpSchema.InitializeResult initializeResult = syncClient.initialize();
        if (initializeResult != null) {
            log.info("connected to Mcp Server: {}, {}", initializeResult.getServerInfo(), initializeResult.getCapabilities());
            return true;
        } else {
            log.error("connect to Mcp Server fail");
            return false;
        }
    }

    public List<McpSchema.Tool> listTools() {
        List<McpSchema.Tool> tools = syncClient.listTools().getTools();
        if (tools != null && !tools.isEmpty()) {
            tools.forEach(tool -> log.info("{}", tool));
        } else {
            log.error("No tools available!");
        }
        return tools;
    }

    public Object callTool(McpSchema.Tool tool, Map<String, Object> params) {
        McpSchema.CallToolResult callToolResult = syncClient.callTool(new McpSchema.CallToolRequest(tool.getName(), params));
        log.info("{}", callToolResult);
        if (Boolean.TRUE.equals(callToolResult.getIsError())) {
            log.error("callTool error: {}", callToolResult);
            return null;
        }
        return callToolResult.getContent().get(0);
    }

    public List<McpSchema.Resource> listResources() {
        List<McpSchema.Resource> resources = syncClient.listResources().getResources();
        if (resources != null && !resources.isEmpty()) {
            resources.forEach(resource -> log.info("{}", resource));
        } else {
            log.error("No resources available!");
        }
        return resources;
    }

    public McpSchema.ReadResourceResult readResource(String uri) {
        return syncClient.readResource(new McpSchema.ReadResourceRequest(uri));
    }

    public McpSchema.ListPromptsResult listPrompts() {
        McpSchema.ListPromptsResult listPromptsResult = syncClient.listPrompts();
        if (listPromptsResult != null && listPromptsResult.getPrompts() != null && !listPromptsResult.getPrompts().isEmpty()) {
            listPromptsResult.getPrompts().forEach(prompt -> log.info("{}", prompt));
        } else {
            log.error("No prompts available!");
        }
        return listPromptsResult;
    }

    public McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> params) {
        McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest(name, params);
        McpSchema.GetPromptResult getPromptResult = syncClient.getPrompt(getPromptRequest);
        log.info("{}", getPromptResult);
        return getPromptResult;
    }

    public void disConnect() {
        transport.closeGracefully();
        syncClient.closeGracefully();
    }

    public static void main(String[] args) {
        String serverUrl = "http://localhost:9090";
        McpClientTransport transport = HttpClientStreamableHttpTransport.builder(serverUrl).build();
//        McpClientTransport transport = HttpClientSseClientTransport.builder(serverUrl).build();
        SyncMcpClient client = new SyncMcpClient(transport);
        if (!client.connect()) {
            return;
        }

        // tool
        List<McpSchema.Tool> tools = client.listTools();
        if (tools != null && !tools.isEmpty()) {
            McpSchema.Tool tool = tools.get(0);
            Map<String, Object> paramMap = new HashMap<>();
            // 不同的tool入参含义不同，通常都是由大模型调用tool，这里只是进行是否能调用成功测试（忽略入参实际作用与含义）
            tool.getInputSchema().getRequired().forEach(param -> paramMap.put(param, "北京"));
            Object result = client.callTool(tool, paramMap);
            log.info("call tool '{}' result: {}", tool.getName(), result);
        }

        // resource
        List<McpSchema.Resource> resources = client.listResources();
        if (resources != null && !resources.isEmpty()) {
            McpSchema.Resource resource = resources.get(0);
            McpSchema.ReadResourceResult readResourceResult = client.readResource(resource.getUri());
            log.info("readResourceResult: {}", readResourceResult);
        }

        // prompt
        McpSchema.ListPromptsResult listPromptsResult = client.listPrompts();
        if (listPromptsResult != null && listPromptsResult.getPrompts() != null && !listPromptsResult.getPrompts().isEmpty()) {
            McpSchema.Prompt prompt = listPromptsResult.getPrompts().get(0);
            Map<String, Object> paramMap = new HashMap<>();
            McpSchema.GetPromptResult getPromptResult = client.getPrompt(prompt.getName(), paramMap);
            log.info("getPromptResult: {}", getPromptResult);
        }

        client.disConnect();
    }
}