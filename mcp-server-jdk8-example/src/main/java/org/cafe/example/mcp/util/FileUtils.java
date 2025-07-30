package org.cafe.example.mcp.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;

public class FileUtils {

    private FileUtils() {
    }

    /**
     * 根据绝对路径文件名读取文件
     *
     * @param absoluteFilePath 绝对路径
     * @return 文件内容（UTF_8编码）；文件不存在或读取失败则抛出异常
     */
    public static String readFile(String absoluteFilePath) {
        if (File.separator.equals("\\")) {
            // Windows需要处理开头的 /、Linux/Mac则无需处理
            absoluteFilePath = absoluteFilePath.startsWith("/") ? absoluteFilePath.substring(1) : absoluteFilePath;
        }
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
        if (File.separator.equals("\\")) {
            // Windows需要处理开头的 /、Linux/Mac则无需处理
            absolutePath = absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
        }
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
