package org.cafe.example.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResourceProvider {

    private static final String TEST_RESOURCE_URI = "test://resource";

    public List<McpServerFeatures.SyncResourceSpecification> allSyncResources() {
        McpSchema.Resource resource = new McpSchema.Resource(TEST_RESOURCE_URI, "Test Resource", "text/plain", "Test resource description",
                null);
        McpServerFeatures.SyncResourceSpecification specification = new McpServerFeatures.SyncResourceSpecification(
                resource, (exchange, req) -> new McpSchema.ReadResourceResult(Collections.emptyList()));
        return Collections.singletonList(specification);
    }

    public List<McpServerFeatures.AsyncResourceSpecification> allAsyncResources() {
        List<McpServerFeatures.AsyncResourceSpecification> asyncResourceSpecifications = new ArrayList<>();
        allSyncResources().forEach(syncResourceSpecification ->
                asyncResourceSpecifications.add(McpServerFeatures.AsyncResourceSpecification.fromSync(syncResourceSpecification)));
        return asyncResourceSpecifications;
    }
}
