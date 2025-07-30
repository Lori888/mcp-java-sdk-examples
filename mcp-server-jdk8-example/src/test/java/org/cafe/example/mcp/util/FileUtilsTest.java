package org.cafe.example.mcp.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class FileUtilsTest {

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
