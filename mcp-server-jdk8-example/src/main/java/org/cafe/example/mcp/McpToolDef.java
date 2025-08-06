package org.cafe.example.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.*;

/**
 * MCP Tool 信息、通过json反序列化得到的对象
 */
@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpToolDef {

    String name;
    String description;
    McpSchema.JsonSchema inputSchema;
    String targetBeanClass;
    String targetMethodName;

}
