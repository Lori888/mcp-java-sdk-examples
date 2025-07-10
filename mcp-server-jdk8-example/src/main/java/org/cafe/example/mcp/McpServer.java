package org.cafe.example.mcp;

import io.modelcontextprotocol.server.McpAsyncStreamableHttpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.StreamableHttpServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.cafe.example.mcp.annotation.McpServerEndpoint;
import org.cafe.example.mcp.util.ProcessUtils;
import org.cafe.example.mcp.util.TimeUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.servlet.Servlet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class McpServer {

    private static final String MSG_REGISTER_TOOLS = "Registered tools: %d, notification: %s";
    private static final String MCP_SERVLET_NAME = "mcpServlet";

    private final Object targetBean = new McpServerTool();
    private final McpServerProperties serverProperties = new McpServerProperties();

    private Tomcat tomcat;
    private McpServerEndpoint mcpServerEndpoint;
    private McpSchema.Implementation serverInfo;
    private McpSchema.ServerCapabilities.Builder capabilitiesBuilder;
    private McpServerTransportProvider transportProvider;

    public void start() throws LifecycleException {
        log.info("Starting MCP Server...");

        processServerProperties();

        buildMcpServer();

        if (!serverProperties.isStdio()) {
            startTomcat();
        }
    }

    private void processServerProperties() {
        mcpServerEndpoint = McpServerEndpointProcessor.resolveMcpServerEndpoint(targetBean.getClass().getName());
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
        log.info("MCP Server properties: [{}]", serverProperties);

        serverInfo = new McpSchema.Implementation(serverProperties.getName(), serverProperties.getVersion());
    }

    private void buildMcpServer() {
        capabilitiesBuilder = McpSchema.ServerCapabilities.builder();
        if (serverProperties.getTransport() == TransportType.STREAMABLE_HTTP) {
            transportProvider = StreamableHttpServerTransportProvider.builder()
                    .withMcpEndpoint(serverProperties.getMcpEndpoint())
                    .build();
        } else if (serverProperties.getTransport() == TransportType.SSE) {
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

    private void buildAsyncStreamableHttpServer() {
        log.info("building async StreamableHttpServer...");

        McpAsyncStreamableHttpServer.Builder serverBuilder = McpAsyncStreamableHttpServer.builder()
                .serverInfo(serverInfo.getName(), serverInfo.getVersion())
                .withMcpEndpoint(serverProperties.getMcpEndpoint());

        // tools
        List<McpServerFeatures.AsyncToolSpecification> toolSpecifications = buildAsyncToolSpecifications();
        if (!toolSpecifications.isEmpty()) {
            toolSpecifications.forEach(serverBuilder::withTool);
            capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
        }
        log.info(String.format(MSG_REGISTER_TOOLS, toolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // TODO resources, prompts, rootsChangeConsumers

        serverBuilder.serverCapabilities(capabilitiesBuilder.build());
        McpAsyncStreamableHttpServer streamableHttpServer = serverBuilder.build();
        transportProvider = streamableHttpServer.getTransportProvider();
    }

    private List<McpServerFeatures.AsyncToolSpecification> buildAsyncToolSpecifications() {
        List<McpServerFeatures.AsyncToolSpecification> asyncToolSpecifications = new ArrayList<>();
        List<McpServerFeatures.SyncToolSpecification> syncToolSpecifications = buildSyncToolSpecifications();
        syncToolSpecifications.forEach(syncToolSpecification -> {
            McpServerFeatures.AsyncToolSpecification asyncToolSpecification =
                    new McpServerFeatures.AsyncToolSpecification(syncToolSpecification.getTool(),
                            (exchange, map) -> Mono
                                    .fromCallable(() -> syncToolSpecification.getCall().apply(new McpSyncServerExchange(exchange), map))
                                    .subscribeOn(Schedulers.boundedElastic()));
            asyncToolSpecifications.add(asyncToolSpecification);
        });
        return asyncToolSpecifications;
    }

    private List<McpServerFeatures.SyncToolSpecification> buildSyncToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> toolSpecifications = new ArrayList<>();
        List<McpToolInfo> toolInfos = McpServerEndpointProcessor.resolveMcpServerTools(targetBean, mcpServerEndpoint);

        toolInfos.forEach(toolInfo -> {
            McpSchema.Tool tool = new McpSchema.Tool(toolInfo.getName(), toolInfo.getDescription(), toolInfo.getInputSchema());

            McpServerFeatures.SyncToolSpecification toolSpecification =
                    new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        try {
                            String callResult = toolInfo.getMethod().invoke(toolInfo.getTargetBean(), request.values().toArray()).toString();
                            // TODO mimeType
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(callResult)), false);
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult(Collections.singletonList(new McpSchema.TextContent(e.getMessage())), true);
                        }
                    });
            toolSpecifications.add(toolSpecification);
        });
        return toolSpecifications;
    }

    private void buildSyncServer() {
        log.info("building sync server...");

        // Create the server with both tool and resource capabilities
        io.modelcontextprotocol.server.McpServer.SyncSpecification serverBuilder = io.modelcontextprotocol.server.McpServer.sync(transportProvider).serverInfo(serverInfo);

        // tools
        List<McpServerFeatures.SyncToolSpecification> toolSpecifications = buildSyncToolSpecifications();
        if (!toolSpecifications.isEmpty()) {
            serverBuilder.tools(toolSpecifications);
            capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
        }
        log.info(String.format(MSG_REGISTER_TOOLS, toolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // TODO resources, prompts, rootsChangeConsumers

        serverBuilder.capabilities(capabilitiesBuilder.build());
        serverBuilder.build();
    }

    private void buildAsyncServer() {
        log.info("building async server...");

        // Create the server with both tool and resource capabilities
        io.modelcontextprotocol.server.McpServer.AsyncSpecification serverBuilder = io.modelcontextprotocol.server.McpServer.async(transportProvider).serverInfo(serverInfo);

        // tools
        List<McpServerFeatures.AsyncToolSpecification> asyncToolSpecifications = buildAsyncToolSpecifications();
        if (!asyncToolSpecifications.isEmpty()) {
            serverBuilder.tools(asyncToolSpecifications);
            capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
        }
        log.info(String.format(MSG_REGISTER_TOOLS, asyncToolSpecifications.size(), serverProperties.isToolChangeNotification()));

        // TODO resources, prompts, rootsChangeConsumers

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
            new McpServer().start();
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
