package org.cafe.example.mcp;

import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * (almost all copy from org.springframework.ai.mcp.server.autoconfigure.McpServerProperties in spring-ai-autoconfigure-mcp-server-1.0.0-M7.jar)
 * Configuration properties for the Model Context Protocol (MCP) server.
 * <p>
 * These properties control the behavior and configuration of the MCP server, including:
 * <ul>
 * <li>Server identification (name and version)</li>
 * <li>Change notification settings for tools, resources, and prompts</li>
 * <li>Web transport endpoint configuration</li>
 * </ul>
 * <p>
 * All properties are prefixed with {@code spring.ai.mcp.server}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@Getter
@ToString
public class McpServerProperties {

    /**
     * Enable/disable the MCP server.
     * <p>
     * When set to false, the MCP server and all its components will not be initialized.
     */
    private boolean enabled = true;

    /**
     * Enable/disable the standard input/output (stdio) transport.
     * <p>
     * When enabled, the server will listen for incoming messages on the standard input
     * and write responses to the standard output.
     */
    private boolean stdio = false;

    /**
     * The name of the MCP server instance.
     * <p>
     * This name is used to identify the server in logs and monitoring.
     */
    private String name = "mcp-server";

    /**
     * The version of the MCP server instance.
     * <p>
     * This version is reported to clients and used for compatibility checks.
     */
    private String version = "1.0.0";

    /**
     * Enable/disable notifications for resource changes. Only relevant for MCP servers
     * with resource capabilities.
     * <p>
     * When enabled, the server will notify clients when resources are added, updated, or
     * removed.
     */
    private boolean resourceChangeNotification = true;

    /**
     * Enable/disable notifications for tool changes. Only relevant for MCP servers with
     * tool capabilities.
     * <p>
     * When enabled, the server will notify clients when tools are registered or
     * unregistered.
     */
    private boolean toolChangeNotification = true;

    /**
     * Enable/disable notifications for prompt changes. Only relevant for MCP servers with
     * prompt capabilities.
     * <p>
     * When enabled, the server will notify clients when prompt templates are modified.
     */
    private boolean promptChangeNotification = true;

    /**
     */
    private String baseUrl = "";

    /**
     */
    private String sseEndpoint = "/sse";

    /**
     * The endpoint path for Server-Sent Events (SSE) when using web transports.
     * <p>
     * This property is only used when transport is set to WEBMVC or WEBFLUX.
     */
    private String sseMessageEndpoint = "/mcp/message";

    private String mcpEndpoint = "/mcp";

    private int port = 8080;

    private TransportType transport = TransportType.STREAMABLE_HTTP;

    /**
     * The type of server to use for MCP server communication.
     * <p>
     * Supported types are:
     * <ul>
     * <li>SYNC - Standard synchronous server (default)</li>
     * <li>ASYNC - Asynchronous server</li>
     * </ul>
     */
    private ServerType type = ServerType.SYNC;

    /**
     * Server types supported by the MCP server.
     */
    public enum ServerType {

        /**
         * Synchronous (McpSyncServer) server
         */
        SYNC,

        /**
         * Asynchronous (McpAsyncServer) server
         */
        ASYNC

    }

    /**
     * (Optional) response MIME type per tool name.
     */
    private final Map<String, String> toolResponseMimeType = new HashMap<>();

    public void setStdio(boolean stdio) {
        this.stdio = stdio;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "Name must not be null");
        this.name = name;
    }

    public void setVersion(String version) {
        Objects.requireNonNull(version, "Version must not be empty");
        this.version = version;
    }

    public void setResourceChangeNotification(boolean resourceChangeNotification) {
        this.resourceChangeNotification = resourceChangeNotification;
    }

    public void setToolChangeNotification(boolean toolChangeNotification) {
        this.toolChangeNotification = toolChangeNotification;
    }

    public void setPromptChangeNotification(boolean promptChangeNotification) {
        this.promptChangeNotification = promptChangeNotification;
    }

    public void setBaseUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "Base URL must not be null");
        this.baseUrl = baseUrl;
    }

    public void setSseEndpoint(String sseEndpoint) {
        Objects.requireNonNull(sseEndpoint, "SSE endpoint must not be empty");
        this.sseEndpoint = sseEndpoint;
    }

    public void setSseMessageEndpoint(String sseMessageEndpoint) {
        Objects.requireNonNull(sseMessageEndpoint, "SSE message endpoint must not be empty");
        this.sseMessageEndpoint = sseMessageEndpoint;
    }

    public void setType(ServerType serverType) {
        Objects.requireNonNull(serverType, "Server type must not be null");
        this.type = serverType;
    }

    public void setMcpEndpoint(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setTransport(TransportType transport) {
        this.transport = transport;
    }
}
