package org.cafe.example.mcp;

import io.modelcontextprotocol.server.McpAsyncStreamableHttpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.cafe.example.mcp.util.ProcessUtils;
import org.cafe.example.mcp.util.TimeUtils;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
public class McpServer {

    private static final String MSG_REGISTER_TOOLS = "Registered tools: %d, notification: %s";
    private static final String MSG_REGISTER_RESOURCES = "Registered resources: %d, notification: %s";
    private static final String MSG_REGISTER_PROMPTS = "Registered prompts: %d, notification: %s";
    private static final String MCP_SERVLET_NAME = "mcpServlet";

    private final McpServerProperties serverProperties = new McpServerProperties();
    private final ToolProvider toolProvider = new ToolProvider();
    private final ResourceProvider resourceProvider = new ResourceProvider();
    private final PromptProvider promptProvider = new PromptProvider();

    private Tomcat tomcat;
    private McpSchema.Implementation serverInfo;
    private McpSchema.ServerCapabilities.Builder capabilitiesBuilder;
    private McpServerTransportProvider transportProvider;

    public void start() throws LifecycleException, IOException {
        log.info("Starting MCP Server...");

        processServerProperties();

        buildMcpServer();

        if (!serverProperties.isStdio()) {
            startTomcat();
        }
    }

    private void processServerProperties() {
        McpServerEndpoint mcpServerEndpoint = toolProvider.getEndpoint();
        assert mcpServerEndpoint != null;

        serverProperties.setName(mcpServerEndpoint.name());
        serverProperties.setVersion(mcpServerEndpoint.version());
        if (McpServerProperties.ServerType.ASYNC.name().equalsIgnoreCase(mcpServerEndpoint.type())) {
            serverProperties.setType(McpServerProperties.ServerType.ASYNC);
        }
        if (mcpServerEndpoint.transport().equalsIgnoreCase(TransportType.SSE.name())) {
            serverProperties.setTransport(TransportType.SSE);
        } else if (mcpServerEndpoint.transport().equalsIgnoreCase(TransportType.STDIO.name())) {
            serverProperties.setTransport(TransportType.STDIO);
            serverProperties.setStdio(true);
        }
        serverProperties.setPort(mcpServerEndpoint.port());
        serverProperties.setBaseUrl(mcpServerEndpoint.baseUrl());
        serverProperties.setSseEndpoint(mcpServerEndpoint.sseEndpoint());
        serverProperties.setSseMessageEndpoint(mcpServerEndpoint.sseMessageEndpoint());
        serverProperties.setMcpEndpoint(mcpServerEndpoint.mcpEndpoint());
        serverProperties.setToolChangeNotification(mcpServerEndpoint.toolChangeNotification());
        serverProperties.setResourceChangeNotification(mcpServerEndpoint.resourceChangeNotification());
        serverProperties.setPromptChangeNotification(mcpServerEndpoint.promptChangeNotification());
        log.info("MCP Server properties: [{}]", serverProperties);

        serverInfo = new McpSchema.Implementation(serverProperties.getName(), serverProperties.getVersion());
    }

    private void buildMcpServer() throws IOException {
        capabilitiesBuilder = McpSchema.ServerCapabilities.builder();
        capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
        capabilitiesBuilder.resources(true, serverProperties.isResourceChangeNotification());
        capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());

        if (serverProperties.getTransport() == TransportType.SSE) {
            transportProvider = HttpServletSseServerTransportProvider.builder()
                    .baseUrl(serverProperties.getBaseUrl())
                    .messageEndpoint(serverProperties.getSseMessageEndpoint())
                    .sseEndpoint(serverProperties.getSseEndpoint())
                    .build();
        } else if (serverProperties.isStdio()){
            transportProvider = new StdioServerTransportProvider();
        }

        if (serverProperties.getTransport() == TransportType.STREAMABLE_HTTP) {
            buildAsyncStreamableHttpServer();
        } else if (serverProperties.getType() == McpServerProperties.ServerType.SYNC) {
            buildSyncServer();
        } else {
            buildAsyncServer();
        }
    }

    private void buildAsyncStreamableHttpServer() throws IOException {
        log.info("building async StreamableHttpServer...");

        McpAsyncStreamableHttpServer.Builder serverBuilder = McpAsyncStreamableHttpServer.builder()
                .serverInfo(serverInfo.getName(), serverInfo.getVersion())
                .withMcpEndpoint(serverProperties.getMcpEndpoint());

        // tools
        List<McpServerFeatures.AsyncToolSpecification> toolSpecifications = toolProvider.allAsyncTools();
        if (!toolSpecifications.isEmpty()) {
            toolSpecifications.forEach(serverBuilder::withTool);
        }
        log.info(String.format(MSG_REGISTER_TOOLS, toolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // resources
        List<McpServerFeatures.AsyncResourceSpecification> resourceSpecifications = resourceProvider.allAsyncResources();
        if (!resourceSpecifications.isEmpty()) {
            resourceSpecifications.forEach(asyncResourceSpecification -> {
                serverBuilder.withResource(asyncResourceSpecification.getResource().getUri(), asyncResourceSpecification);
            });
        }
        log.info(String.format(MSG_REGISTER_RESOURCES, resourceSpecifications.size(), serverProperties.isResourceChangeNotification()));

        // prompts
        List<McpServerFeatures.AsyncPromptSpecification> promptSpecifications = promptProvider.allAsyncPrompts();
        if (!promptSpecifications.isEmpty()) {
            promptSpecifications.forEach(asyncPromptSpecification ->
                    serverBuilder.withPrompt(asyncPromptSpecification.getPrompt().getName(), asyncPromptSpecification));
        }
        log.info(String.format(MSG_REGISTER_PROMPTS, promptSpecifications.size(), serverProperties.isPromptChangeNotification()));

        serverBuilder.serverCapabilities(capabilitiesBuilder.build());
        McpAsyncStreamableHttpServer streamableHttpServer = serverBuilder.build();
        // StreamableHttpServer必须在这里对transportProvider进行赋值，否则在注册servlet时transportProvider为null将引起NPE
        transportProvider = streamableHttpServer.getTransportProvider();
    }

    private void buildSyncServer() throws IOException {
        log.info("building sync server...");

        // Create the server with both tool and resource capabilities
        io.modelcontextprotocol.server.McpServer.SyncSpecification serverBuilder = io.modelcontextprotocol.server.McpServer.sync(transportProvider).serverInfo(serverInfo);

        // tools
        List<McpServerFeatures.SyncToolSpecification> toolSpecifications = toolProvider.allSyncTools();
        if (!toolSpecifications.isEmpty()) {
            serverBuilder.tools(toolSpecifications);
        }
        log.info(String.format(MSG_REGISTER_TOOLS, toolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // resources
        List<McpServerFeatures.SyncResourceSpecification> syncResourceSpecifications = resourceProvider.allSyncResources();
        if (!syncResourceSpecifications.isEmpty()) {
            serverBuilder.resources(syncResourceSpecifications);
        }
        log.info(String.format(MSG_REGISTER_RESOURCES, syncResourceSpecifications.size(), serverProperties.isResourceChangeNotification()));

        // prompts
        List<McpServerFeatures.SyncPromptSpecification> syncPromptSpecifications = promptProvider.allSyncPrompts();
        if (!syncPromptSpecifications.isEmpty()) {
            serverBuilder.prompts(syncPromptSpecifications);
        }
        log.info(String.format(MSG_REGISTER_PROMPTS, syncPromptSpecifications.size(), serverProperties.isPromptChangeNotification()));

        // TODO Resource Templates, roots, rootsChangeConsumers

        serverBuilder.capabilities(capabilitiesBuilder.build());
        serverBuilder.build();
    }

    private void buildAsyncServer() throws IOException {
        log.info("building async server...");

        // Create the server with both tool and resource capabilities
        io.modelcontextprotocol.server.McpServer.AsyncSpecification serverBuilder = io.modelcontextprotocol.server.McpServer.async(transportProvider).serverInfo(serverInfo);

        // tools
        List<McpServerFeatures.AsyncToolSpecification> asyncToolSpecifications = toolProvider.allAsyncTools();
        if (!asyncToolSpecifications.isEmpty()) {
            serverBuilder.tools(asyncToolSpecifications);
        }
        log.info(String.format(MSG_REGISTER_TOOLS, asyncToolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // resources
        List<McpServerFeatures.AsyncResourceSpecification> asyncResourceSpecifications = resourceProvider.allAsyncResources();
        if (!asyncResourceSpecifications.isEmpty()) {
            serverBuilder.resources(asyncResourceSpecifications);
        }
        log.info(String.format(MSG_REGISTER_RESOURCES, asyncResourceSpecifications.size(), serverProperties.isResourceChangeNotification()));

        // prompts
        List<McpServerFeatures.AsyncPromptSpecification> asyncPromptSpecifications = promptProvider.allAsyncPrompts();
        if (!asyncPromptSpecifications.isEmpty()) {
            serverBuilder.prompts(asyncPromptSpecifications);
        }
        log.info(String.format(MSG_REGISTER_PROMPTS, asyncPromptSpecifications.size(), serverProperties.isPromptChangeNotification()));

        // TODO Resource Templates, roots, rootsChangeConsumers

        serverBuilder.capabilities(capabilitiesBuilder.build());
        serverBuilder.build();
    }

    private void startTomcat() throws LifecycleException {
        log.info("Starting Tomcat...");

        tomcat = new Tomcat();
        tomcat.setPort(serverProperties.getPort());

        // 设置临时目录
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        tomcat.setBaseDir(baseDir.getAbsolutePath());

        // 添加 Web 应用（无 webapp 目录，只注册 servlet）
        Context ctx = tomcat.addContext("", baseDir.getAbsolutePath());

        // 注册 MCP Servlet
        org.apache.catalina.Wrapper wrapper = ctx.createWrapper();
        wrapper.setName(MCP_SERVLET_NAME);
        wrapper.setServlet((Servlet) transportProvider);
        wrapper.setLoadOnStartup(1);
        wrapper.setAsyncSupported(true);
        ctx.addChild(wrapper);
        ctx.addServletMappingDecoded("/*", MCP_SERVLET_NAME);

        tomcat.getConnector(); // 默认创建一个 HTTP connector
        tomcat.start();

        String endpoint = serverProperties.getTransport() == TransportType.STREAMABLE_HTTP ?
                serverProperties.getMcpEndpoint() : serverProperties.getSseEndpoint();
        log.info("Tomcat running on {} and mcp server path is {}", serverProperties.getPort(), endpoint);
    }

    public static void main(String[] args) {
        log.info("=====================================================");
        log.info("Example McpServer starting at {}", TimeUtils.getCurrentDateTime());
        log.info("PID: {}", ProcessUtils.getCurrentPid());
        log.info("=====================================================");

        McpServer mcpServer = new McpServer();
        try {
            mcpServer.start();
        } catch (Exception e) {
            log.error("McpServer error:", e);
            mcpServer.stop();
        }
    }

    private void stop() {
        log.info("Stopping MCP Server...");

        if (transportProvider != null) {
            transportProvider.closeGracefully().block();
        }

        if (tomcat != null) {
            log.info("Stopping Tomcat...");
            try {
                tomcat.stop();
                tomcat.destroy();

                log.info("Tomcat stopped");
            } catch (LifecycleException e) {
                log.error("stop Tomcat error:", e);
            }
        }
    }
}
