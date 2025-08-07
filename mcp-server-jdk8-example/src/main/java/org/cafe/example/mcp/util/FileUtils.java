package org.cafe.example.mcp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.cafe.example.mcp.McpToolDef;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class FileUtils {

    private FileUtils() {
    }

    /**
     * 将具体的tool方法转换为MCP tools/list Response内容中的"tools"节点内容，并保存到文件中<br/>
     * 注意：<br/>
     * 1.文件已存在则将会覆盖原文件内容;<br/>
     * 2.生成的内容中不含"description"，需要手动添加。<br/>
     * （方法的"description"节点已存在、只需补充具体描述；参数的"inputSchema"-"properties"-"message"-"description"节点不存在、需补充整个节点）
     *
     * @param toolClz 将具体的tool方法所在类
     * @param absoluteFilePath 要保存的文件绝对路径
     * @throws JsonProcessingException
     */
    public static void createToolListJsonFile(Class toolClz, String absoluteFilePath) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        List<McpToolDef> toolDefList = new ArrayList<>();
        Method[] declaredMethods = toolClz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            McpToolDef toolDef = new McpToolDef();
            toolDef.setName(method.getName());
            toolDef.setDescription("");
            toolDef.setTargetBeanClass(toolClz.getName());
            toolDef.setTargetMethodName(method.getName());
            String inputSchema = JsonSchemaGenerator.generateForMethodInput(method);
            toolDef.setInputSchema(objectMapper.readValue(inputSchema, McpSchema.JsonSchema.class));

            toolDefList.add(toolDef);
        }

        String json = objectMapper.writeValueAsString(toolDefList);
        FileUtils.writeFile(absoluteFilePath, json);
    }

    public static String resolveFilePath(String absoluteFilePath) {
        if (File.separator.equals("\\")) {
            // Windows需要处理开头的 /、Linux/Mac则无需处理
            absoluteFilePath = absoluteFilePath.startsWith("/") ? absoluteFilePath.substring(1) : absoluteFilePath;
        }
        return absoluteFilePath;
    }

    public static Path createParentDir(String absoluteFilePath) {
        Path filePath = Paths.get(resolveFilePath(absoluteFilePath));
        filePath.getParent().toFile().mkdirs();
        return filePath;
    }

    /**
     * 根据绝对路径文件名写入文件
     *
     * @param absoluteFilePath 绝对路径
     * @param content          文件内容（UTF_8编码）
     */
    public static void writeFile(String absoluteFilePath, String content) {
        Path filePath = createParentDir(absoluteFilePath);
        try (OutputStream os = Files.newOutputStream(filePath)) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Error writing file: " + absoluteFilePath, e);
        }
    }

    /**
     * 根据绝对路径文件名读取文件
     *
     * @param absoluteFilePath 绝对路径
     * @return 文件内容（UTF_8编码）；文件不存在或读取失败则抛出异常
     */
    public static String readFile(String absoluteFilePath) {
        absoluteFilePath = resolveFilePath(absoluteFilePath);
        try (InputStream is = Files.newInputStream(Paths.get(absoluteFilePath))) {
            byte[] fileData = readBytesFromFile(is);
            if (fileData.length > 0) {
                return new String(fileData, StandardCharsets.UTF_8);
            } else {
                throw new RuntimeException("Error reading file: " + absoluteFilePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading file: " + absoluteFilePath, e);
        }
    }

    /**
     * 在指定路径下根据文件名（不含后缀）读取文件
     *
     * @param absolutePath 绝对路径
     * @param fileName     文件名（不含后缀）
     * @return 数据元素1为文件后缀、元素2为文件内容（如果是json类型返回文件内容、如果是图片类型则返回图片base64编码数据、其他类型抛出异常）
     */
    public static String[] readFileByName(String absolutePath, String fileName) {
        absolutePath = absolutePath.endsWith(File.separator) ? absolutePath : absolutePath + File.separator;
        absolutePath = resolveFilePath(absolutePath);

        String[] result = new String[2];
        // 查找fileName开头的文件
        for (String file : Objects.requireNonNull(new File(absolutePath).list())) {
            if (file.startsWith(fileName + ".")) {
                String fileExtension = file.substring(file.lastIndexOf(".") + 1);
                if ("json".equals(fileExtension)) {
                    // text类型的仅支持json后缀、且json文件内容格式按MCP的标准
                    result[0] = fileExtension;
                    result[1] = FileUtils.readFile(absolutePath + file);
                    return result;
                } else if (isImageFile(file)) {
                    result[0] = getMimeType4ImageFile(absolutePath + file);
                    result[1] = FileUtils.readImageToBase64(absolutePath + file);
                    return result;
                }
            }
        }
        throw new RuntimeException("File not found: " + absolutePath + fileName);
    }

    /**
     * 判断是否是图片文件
     *
     * @param fileName 文件名绝对路径
     * @return true 表示是图片文件；false 表示不是图片文件
     */
    public static boolean isImageFile(String fileName) {
        // TODO 支持更多图片类型
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
    }

    /**
     * 获取图片文件的MIME类型
     *
     * @param fileName 文件名绝对路径
     * @return 图片文件的MIME类型；如果不是图片类型则返回null
     */
    public static String getMimeType4ImageFile(String fileName) {
        if (!isImageFile(fileName)) {
            return null;
        }
        // TODO 支持更多图片类型
        return fileName.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    /**
     * 读取图片文件并转为base64编码
     *
     * @param imageFile 图片文件绝对路径
     * @return base64编码图片数据；读取失败则抛出异常
     */
    public static String readImageToBase64(String imageFile) {
        String base64Image = "";
        try (FileInputStream imageInFile = new FileInputStream(imageFile)) {
            // 读取图片文件为字节数组
            byte[] imageData = readBytesFromFile(imageInFile);
            // 使用 Base64 编码字节数组
            base64Image = Base64.getEncoder().encodeToString(imageData);
        } catch (IOException e) {
            throw new RuntimeException("Error reading image file: " + imageFile, e);
        }
        return base64Image;
    }

    private static byte[] readBytesFromFile(InputStream fileInputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }
}
