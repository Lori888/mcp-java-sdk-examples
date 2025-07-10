package org.cafe.example.mcp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.cafe.example.mcp.util.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.List;

/**
 * MCP Tool 信息
 */
@Getter
@ToString
public class McpToolInfo {

    private final String name;
    private final String description;
    private final Object targetBean;
    private final Method method;
    private final List<ParamInfo> params;
    private final String inputSchema;

    public McpToolInfo(String name, String description, Object targetBean, Method method, List<ParamInfo> params) {
        this.name = name;
        this.description = description;
        this.targetBean = targetBean;
        this.method = method;
        this.params = params;
        this.inputSchema = JsonSchemaGenerator.generateForMethodInput(method);
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class ParamInfo {

        private final String name;
        private final String description;
        private final String[] enums;
        private final boolean required;
    }
}
