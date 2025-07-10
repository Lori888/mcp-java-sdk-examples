package org.cafe.example.mcp.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    private TimeUtils() {
    }

    public static String getCurrentDateTime() {
        ZonedDateTime nowUtc = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return nowUtc.format(formatter);
    }
}
