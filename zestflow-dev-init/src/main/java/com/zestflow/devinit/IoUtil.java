package com.zestflow.devinit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class IoUtil {

    private IoUtil() {
    }

    static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    static void writeFile(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    static String readClasspath(String classpathLocation) throws IOException {
        String normalized = classpathLocation.startsWith("/") ? classpathLocation : "/" + classpathLocation;
        InputStream in = IoUtil.class.getResourceAsStream(normalized);
        if (in == null) {
            throw new IOException("Classpath resource not found: " + classpathLocation);
        }
        try {
            return new String(readAllBytes(in), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
