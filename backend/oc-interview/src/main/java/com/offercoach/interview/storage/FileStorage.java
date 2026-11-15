package com.offercoach.interview.storage;

/**
 * 文件存储端口；key 为相对路径，由实现映射到具体后端。
 */
public interface FileStorage {

    void store(String key, byte[] content);

    byte[] load(String key);

    void delete(String key);

    /**
     * 列举指定前缀下的全部 key；用于保留期清理。
     */
    java.util.List<String> listKeys(String prefix);

    /**
     * 文件最后修改时间；不存在时返回 {@code null}。
     */
    java.time.Instant lastModified(String key);
}
