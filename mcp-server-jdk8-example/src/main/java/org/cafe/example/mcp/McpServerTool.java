package org.cafe.example.mcp;


import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 具体的tools功能类，通过`@McpServerEndpoint`注解来定义MCP Server属性、通过`@Tool`注解来定义tool方法
 */
@McpServerEndpoint(name = "示例MCP服务器", port = 9090/*, transport = "sse", type = "async"*/)
public class McpServerTool {

    // @Tool description的作用：供大模型是否调用该tool决策时使用、因此尽可能准确无误无歧义、不同的tool之间不要有重复或矛盾的描述
    // @ToolParam 不是必须的，但建议加上，因为大模型调用tool时，参数的描述也会被使用

    @Tool(name = "getWeatherRename", description = "获取天气信息")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        return String.format("%s: 晴天，温度25℃", city);
    }

    @Tool(description = "获取城市特产")
    public String getSpeciality(@ToolParam(description = "城市名称") String city, @ToolParam(description = "特产类型") String type) {
        return String.format("%s的%s特产是小笼包", type, city);
    }
}
