package org.cafe.example.mcp;


/**
 * 具体的tools功能类，无需添加`@Tool`注解、通过`tool-list.json`进行配置
 */
public class McpTool {

    public String sayHello(String message) {
        return String.format("Hello: %s", message);
    }

    public String getFamous(String city, String place) {
        return String.format("%s的地标是%s", city, place);
    }
}
