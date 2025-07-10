package org.cafe.example.mcp.annotation;

import java.lang.annotation.*;

/**
 * MCP服务端点注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpServerEndpoint {

    /**
     * MCP服务端口
     */
    int port() default 9000;

    /**
     * MCP服务名称
     */
    String name() default "MCP Server";

    /**
     * MCP服务版本
     */
    String version() default "1.0.0";

    /**
     * MCP服务根路径
     */
    String baseUrl() default "";

    /**
     * SSE服务端点
     */
    String sseEndpoint() default "/sse";

    /**
     * SSE消息服务端点
     */
    String sseMessageEndpoint() default "/mcp/message";

    /**
     * MCP服务端点
     */
    String mcpEndpoint() default "/mcp";

    /**
     * 服务器同步、异步类型（目前streamableHttp仅支持异步）
     * sync(default) or async
     */
    String type() default "sync";

    /**
     * streamableHttp(default) or sse or stdio
     */
    String transport() default "streamableHttp";

    /**
     * 是否通知tools变更
     */
    boolean toolChangeNotification() default true;
}
