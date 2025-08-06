package org.cafe.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.cafe.example.mcp.util.FileUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ToolProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Object targetBean = new McpServerTool();

    @Getter
    private final McpServerEndpoint endpoint;

    public ToolProvider() {
        this.endpoint = McpServerEndpointProcessor.resolveMcpServerEndpoint(targetBean.getClass().getName());
    }

    public List<McpServerFeatures.AsyncToolSpecification> allAsyncTools() throws JsonProcessingException {
        List<McpServerFeatures.AsyncToolSpecification> asyncToolSpecifications = new ArrayList<>();
        List<McpServerFeatures.SyncToolSpecification> syncToolSpecifications = allSyncTools();
        syncToolSpecifications.forEach(syncToolSpecification -> {
            McpServerFeatures.AsyncToolSpecification asyncToolSpecification =
                    new McpServerFeatures.AsyncToolSpecification(syncToolSpecification.getTool(),
                            (exchange, map) -> Mono
                                    .fromCallable(() -> syncToolSpecification.getCall().apply(new McpSyncServerExchange(exchange), map))
                                    .subscribeOn(Schedulers.boundedElastic()));
            asyncToolSpecifications.add(asyncToolSpecification);
        });
        return asyncToolSpecifications;
    }

    public List<McpServerFeatures.SyncToolSpecification> allSyncTools() throws JsonProcessingException {
        List<McpServerFeatures.SyncToolSpecification> toolSpecifications = new ArrayList<>();

        buildFromMcpToolDef(toolSpecifications);

        buildFromMcpToolInfo(toolSpecifications);

        return toolSpecifications;
    }

    private void buildFromMcpToolDef(List<McpServerFeatures.SyncToolSpecification> toolSpecifications) throws JsonProcessingException {
        URL toolListFile = ToolProvider.class.getClassLoader().getResource("tool/tool-list.json");
        if (toolListFile == null) {
            log.info("Not found tool/tool-list.json");
            return;
        }

        String toolListFilePath = toolListFile.getPath();
        String toolContent = FileUtils.readFile(toolListFilePath);
        if (toolContent.isEmpty()) {
            log.debug("Not found MCP tools on {}", toolListFilePath);
            return;
        }

        List<McpToolDef> toolDefList = objectMapper.readValue(toolContent,
                objectMapper.getTypeFactory().constructCollectionType(List.class, McpToolDef.class));
        log.debug("Found MCP tools on {}: {}", toolListFilePath, toolDefList);

        toolDefList.forEach(toolDef -> {
            McpSchema.Tool tool = new McpSchema.Tool(toolDef.getName(), toolDef.getDescription(), toolDef.getInputSchema());
            McpServerFeatures.SyncToolSpecification toolSpecification =
                    new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        try {
                            String callResult = callMethod(toolDef.getTargetBeanClass(), toolDef.getTargetMethodName(), request.values().toArray());
                            // TODO mimeType
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(callResult)), false);
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(e.getMessage())), true);
                        }
                    });
            toolSpecifications.add(toolSpecification);
        });
    }

    private String callMethod(String targetBeanClass, String targetMethodName, Object... args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        // FIXME 每次调用时都创建实例似乎不妥，Spring环境下可通过容器获取实例、非Spring环境可考虑缓存等优化方式
        Class<?> clazz = Class.forName(targetBeanClass);
        Object bean = clazz.newInstance();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (method.getName().equals(targetMethodName)) {
                return method.invoke(bean, args).toString();
            }
        }
        return null;
    }

    private void buildFromMcpToolInfo(List<McpServerFeatures.SyncToolSpecification> toolSpecifications) {
        List<McpToolInfo> toolInfos = McpServerEndpointProcessor.resolveMcpServerTools(targetBean, endpoint);
        toolInfos.forEach(toolInfo -> {
            McpSchema.Tool tool = new McpSchema.Tool(toolInfo.getName(), toolInfo.getDescription(), toolInfo.getInputSchema());
            McpServerFeatures.SyncToolSpecification toolSpecification =
                    new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        try {
                            String callResult = toolInfo.getMethod().invoke(toolInfo.getTargetBean(), request.values().toArray()).toString();
                            // TODO mimeType
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(callResult)), false);
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(e.getMessage())), true);
                        }
                    });
            toolSpecifications.add(toolSpecification);
        });
    }
}
