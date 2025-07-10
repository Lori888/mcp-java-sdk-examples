package org.cafe.example.mcp.util;

import java.lang.management.ManagementFactory;

public class ProcessUtils {

    private ProcessUtils() {
    }

    public static long getCurrentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(name.split("@")[0]);
    }
}
