package com.offercoach.common.time;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 业务时区的「今天」。
 *
 * <p>产品面向国内用户，日额度、每日上限、周期边界都必须按北京时间滚动。此前各处用
 * {@code LocalDate.now()} 取 JVM 默认时区（容器里是 UTC），导致计数在早上 8 点才重置；
 * 而邀请上限又按 {@code Asia/Shanghai} 算，同一个用户有两套「今天」。统一从这里取。</p>
 */
public final class BusinessDate {

    /** 业务时区；与 {@code oc.retention.zone} 的默认值保持一致。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    private BusinessDate() {}

    /**
     * 业务时区的今天。
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * 业务时区今天的紧凑格式（yyyyMMdd），用于 Redis 每日键。
     */
    public static String todayKey() {
        return today().format(COMPACT);
    }

    /**
     * 业务时区今天的 ISO 格式（yyyy-MM-dd），用于 Redis 每日键。
     */
    public static String todayIso() {
        return today().toString();
    }
}
