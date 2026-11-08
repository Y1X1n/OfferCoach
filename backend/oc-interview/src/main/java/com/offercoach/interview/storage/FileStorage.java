package com.offercoach.interview.storage;

/**
 * 文件存储端口；key 为相对路径，由实现映射到具体后端。
 */
public interface FileStorage {

    void store(String key, byte[] content);

    byte[] load(String key);

    void delete(String key);
}
