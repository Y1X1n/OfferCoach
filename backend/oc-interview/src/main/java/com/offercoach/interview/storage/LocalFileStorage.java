package com.offercoach.interview.storage;

import com.offercoach.interview.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地磁盘存储：相对 key 映射到 oc.storage.root，拒绝越界路径。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private static final String DEFAULT_ROOT = ".local/storage";

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        String configured =
                properties.getRoot() == null || properties.getRoot().isBlank() ? DEFAULT_ROOT : properties.getRoot();
        this.root = Paths.get(configured).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("文件写入失败", exception);
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException exception) {
            throw new IllegalStateException("文件读取失败", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            log.warn("删除文件失败：key={}", key);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("文件 key 不能为空");
        }
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("非法文件 key");
        }
        return candidate;
    }
}
