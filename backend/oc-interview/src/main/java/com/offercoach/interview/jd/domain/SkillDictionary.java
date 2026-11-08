package com.offercoach.interview.jd.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 技能词典：别名归一化（K8s → Kubernetes）与文本命中判断，供启发式结构化与匹配复用。
 */
public final class SkillDictionary {

    private static final Map<String, String> ALIASES;
    private static final List<String> SORTED_ALIASES;
    private static final Map<String, List<String>> ALIASES_BY_NAME;
    private static final Map<String, Pattern> ASCII_MATCHERS;

    private static final List<String> DOMAIN_TERMS = List.of(
            "微服务", "高并发", "分布式", "稳定性", "性能优化", "缓存", "消息队列", "大数据", "云原生", "容器化", "监控", "告警", "安全", "支付", "电商", "金融",
            "中台", "数据一致性", "可用性", "扩展性", "团队协作", "沟通能力", "项目管理", "架构设计", "领域驱动", "DevOps");

    static {
        ALIASES = buildAliases();
        SORTED_ALIASES = ALIASES.keySet().stream()
                .sorted((first, second) -> Integer.compare(second.length(), first.length()))
                .toList();
        ALIASES_BY_NAME = buildAliasesByName();
        ASCII_MATCHERS = buildMatchers();
    }

    private SkillDictionary() {}

    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        String mapped = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        return mapped == null ? trimmed : mapped;
    }

    public static boolean containsSkill(String text, String skill) {
        if (text == null || text.isBlank() || skill == null || skill.isBlank()) {
            return false;
        }
        String canonical = canonical(skill);
        List<String> aliases = ALIASES_BY_NAME.getOrDefault(canonical, List.of(canonical.toLowerCase(Locale.ROOT)));
        return aliases.stream().anyMatch(alias -> matches(text, alias));
    }

    public static List<String> findSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (String alias : SORTED_ALIASES) {
            if (matches(text, alias)) {
                found.add(ALIASES.get(alias));
            }
        }
        return new ArrayList<>(found);
    }

    public static List<String> findDomainTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return DOMAIN_TERMS.stream()
                .filter(term -> matches(text, term.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static boolean matches(String text, String alias) {
        Pattern pattern = ASCII_MATCHERS.get(alias);
        if (pattern != null) {
            return pattern.matcher(text).find();
        }
        return text.toLowerCase(Locale.ROOT).contains(alias);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        add(map, "Java", "java");
        add(map, "Spring Boot", "spring boot", "springboot", "spring-boot");
        add(map, "Spring Cloud", "spring cloud", "springcloud");
        add(map, "Spring", "spring");
        add(map, "MyBatis", "mybatis");
        add(map, "MySQL", "mysql");
        add(map, "PostgreSQL", "postgresql", "postgres", "pgsql");
        add(map, "Redis", "redis");
        add(map, "Kafka", "kafka");
        add(map, "RabbitMQ", "rabbitmq");
        add(map, "RocketMQ", "rocketmq");
        add(map, "Docker", "docker", "容器化");
        add(map, "Kubernetes", "kubernetes", "k8s", "k8s集群");
        add(map, "Linux", "linux");
        add(map, "Python", "python");
        add(map, "Go", "golang", "go语言", "go");
        add(map, "C++", "c++");
        add(map, "JavaScript", "javascript", "js");
        add(map, "TypeScript", "typescript", "ts");
        add(map, "React", "react");
        add(map, "Vue", "vue", "vue.js", "vuejs");
        add(map, "Node.js", "node.js", "nodejs", "node");
        add(map, "Nginx", "nginx");
        add(map, "Elasticsearch", "elasticsearch", "es");
        add(map, "MongoDB", "mongodb", "mongo");
        add(map, "Flink", "flink");
        add(map, "Spark", "spark");
        add(map, "Hadoop", "hadoop");
        add(map, "Hive", "hive");
        add(map, "ClickHouse", "clickhouse");
        add(map, "Dubbo", "dubbo");
        add(map, "Netty", "netty");
        add(map, "ZooKeeper", "zookeeper", "zk");
        add(map, "JVM", "jvm");
        add(map, "SQL", "sql");
        add(map, "AWS", "aws");
        add(map, "阿里云", "阿里云");
        add(map, "腾讯云", "腾讯云");
        add(map, "微服务", "微服务");
        add(map, "高并发", "高并发", "高性能", "大流量");
        add(map, "分布式", "分布式", "分布式系统");
        add(map, "消息队列", "消息队列", "mq");
        add(map, "缓存", "缓存");
        add(map, "CI/CD", "ci/cd", "cicd");
        add(map, "Jenkins", "jenkins");
        add(map, "Git", "git");
        add(map, "Prometheus", "prometheus");
        add(map, "Grafana", "grafana");
        add(map, "Oracle", "oracle");
        add(map, "RESTful", "restful", "rest");
        add(map, "gRPC", "grpc");
        add(map, "HTTP", "http");
        add(map, "HTTPS", "https");
        add(map, "TCP", "tcp");
        return map;
    }

    private static void add(Map<String, String> map, String canonical, String... aliases) {
        for (String alias : aliases) {
            map.put(alias.toLowerCase(Locale.ROOT), canonical);
        }
    }

    private static Map<String, List<String>> buildAliasesByName() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            map.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        return map;
    }

    private static Map<String, Pattern> buildMatchers() {
        Map<String, Pattern> map = new LinkedHashMap<>();
        for (String alias : ALIASES.keySet()) {
            if (alias.matches("[a-z0-9.+#/ ]+")) {
                map.put(
                        alias,
                        Pattern.compile(
                                "(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])", Pattern.CASE_INSENSITIVE));
            }
        }
        return map;
    }
}
