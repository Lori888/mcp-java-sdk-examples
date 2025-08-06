package org.cafe.example.mcp;

import lombok.extern.slf4j.Slf4j;
import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code McpServerEndpoint}处理器
 */
@Slf4j
public class McpServerEndpointProcessor {

    private McpServerEndpointProcessor() {
    }

    public static McpServerEndpoint resolveMcpServerEndpoint(String className) {
        log.info("Processing McpServer...");
        try {
            Class<?> beanClass = Class.forName(className);
            return beanClass.getAnnotation(McpServerEndpoint.class);
        } catch (Exception e) {
            log.error("Processing McpServerEndpoint error: {}", e.getMessage(), e);
            return null;
        }
    }

    public static List<McpToolInfo> resolveMcpServerTools(Object targetBean, McpServerEndpoint endpoint) {
        log.info("Processing McpServer tools...");

        McpServerEndpoint annotation = endpoint == null ?
                resolveMcpServerEndpoint(targetBean.getClass().getName()) : endpoint;
        if (annotation == null) {
            log.error("McpServerEndpoint annotation not found on: {}", targetBean.getClass().getName());
            return Collections.emptyList();
        }

        try {
            List<McpToolInfo> toolInfos = collectMcpFunctions(targetBean);
            log.debug("Found MCP tools on {}: {}", targetBean.getClass().getName(), toolInfos);
            return toolInfos;
        } catch (Exception e) {
            log.error("Processing McpServer tools error: {}", e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    private static List<McpToolInfo> collectMcpFunctions(Object targetBean) {
        Method[] methods = targetBean.getClass().getDeclaredMethods();
        List<McpToolInfo> functionInfos = new ArrayList<>();

        for (Method method : methods) {
            Tool mcpFunction = method.getAnnotation(Tool.class);
            if (mcpFunction != null) {
                McpToolInfo functionInfo = new McpToolInfo(
                        mcpFunction.name() == null || mcpFunction.name().isEmpty() ? method.getName() : mcpFunction.name(),
                        mcpFunction.description(), targetBean, method);
                functionInfos.add(functionInfo);
            }
        }
        return functionInfos;
    }
}