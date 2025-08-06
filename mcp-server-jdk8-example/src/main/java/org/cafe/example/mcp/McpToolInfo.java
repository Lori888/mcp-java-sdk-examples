package org.cafe.example.mcp;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.cafe.example.mcp.util.JsonSchemaGenerator;

import java.lang.reflect.Method;

/**
 * MCP Tool 信息
 */
@NoArgsConstructor(force = true)
@Data
public class McpToolInfo {

    private String name;
    private String description;
    private Object targetBean;
    private Method method;
    private String inputSchema;

    public McpToolInfo(String name, String description, Object targetBean, Method method) {
        this.name = name;
        this.description = description;
        this.targetBean = targetBean;
        this.method = method;
        this.inputSchema = JsonSchemaGenerator.generateForMethodInput(method);
    }
}
