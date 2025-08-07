package org.cafe.example.mcp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.cafe.example.mcp.McpTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

class FileUtilsTest {

    @Test
    void testCreateToolListJsonFile() throws JsonProcessingException {
        String absoluteFileDir = getClass().getClassLoader().getResource("").getPath() + "tool/tool-list.json";
        FileUtils.createToolListJsonFile(McpTool.class, absoluteFileDir);
    }

    @Test
    void testCreateParentDir() throws IOException {
        // path is file
        String absoluteFileDir = getClass().getClassLoader().getResource("").getPath() + "test";
        absoluteFileDir = FileUtils.resolveFilePath(absoluteFileDir);

        Files.deleteIfExists(Paths.get(absoluteFileDir));
        String absoluteFile = absoluteFileDir + "/tool-list.json";
        FileUtils.createParentDir(absoluteFile);

        // path is dir, dir2 will not create
        FileUtils.createParentDir(absoluteFileDir + "/dir1/dir2");
    }

    @Test
    void testReadFile() {
        String filePath = Objects.requireNonNull(getClass().getClassLoader().getResource("imageBase64.txt")).getPath();
        System.out.println(FileUtils.readFile(filePath));
    }

    @Test
    void testReadFileByName() {
        String promptDir = Objects.requireNonNull(getClass().getClassLoader().getResource("prompt")).getPath();
        String[] result = FileUtils.readFileByName(promptDir, "prompt-text-from-file");
        Assertions.assertEquals("json", result[0]);
        System.out.println(result[1]);
    }

    @Test
    void testReadImageToBase64() {
        String filePath = Objects.requireNonNull(FileUtils.class.getClassLoader().getResource("prompt/")).getPath();
        System.out.println(FileUtils.readImageToBase64(filePath + "prompt-image.png"));
    }
}
