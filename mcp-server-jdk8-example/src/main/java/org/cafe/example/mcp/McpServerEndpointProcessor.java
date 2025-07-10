package org.cafe.example.mcp;

import lombok.extern.slf4j.Slf4j;
import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
            log.error("McpServer tools not found on: {}", targetBean.getClass().getName());
            return Collections.emptyList();
        }

        try {
            List<McpToolInfo> toolInfos = collectMcpFunctions(targetBean);
            log.info("MCP tools: {}", toolInfos);
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
                List<McpToolInfo.ParamInfo> paramInfos = collectFunctionParamInfos(method);
                McpToolInfo functionInfo = new McpToolInfo(
                        mcpFunction.name() == null || mcpFunction.name().isEmpty() ? method.getName() : mcpFunction.name(),
                        mcpFunction.description(), targetBean, method, paramInfos);
                functionInfos.add(functionInfo);
            }
        }
        return functionInfos;
    }

    private static List<McpToolInfo.ParamInfo> collectFunctionParamInfos(Method method) {
        Parameter[] parameters = method.getParameters();
        List<McpToolInfo.ParamInfo> paramInfos = new ArrayList<>();

        for (Parameter parameter : parameters) {
            ToolParam mcpParam = parameter.getAnnotation(ToolParam.class);
            if (mcpParam != null) {
                McpToolInfo.ParamInfo paramInfo = new McpToolInfo.ParamInfo(
                        parameter.getName(), mcpParam.description(), null, mcpParam.required());
                paramInfos.add(paramInfo);
            }
        }
        return paramInfos;
    }
}