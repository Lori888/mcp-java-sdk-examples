package org.cafe.example.mcp;


import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@McpServerEndpoint(name = "示例MCP服务器", port = 9090/*, transport = "sse", type = "async"*/)
public class McpServerTool {

    @Tool(name = "getWeather", description = "获取天气信息")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        return String.format("%s: 晴天，温度25℃", city);
    }

    @Tool(description = "获取城市特产")
    public String getSpeciality(@ToolParam(description = "城市名称") String city, @ToolParam(description = "特产类型") String type) {
        return String.format("%s的%s特产是小笼包", type, city);
    }
}
