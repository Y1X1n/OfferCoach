package com.offercoach.interview.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储根目录；生产通过 STORAGE_ROOT 挂载持久卷。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.storage")
public class StorageProperties {

    private String root = ".local/storage";
}
