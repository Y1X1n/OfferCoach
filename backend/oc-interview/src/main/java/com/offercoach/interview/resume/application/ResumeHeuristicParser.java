package com.offercoach.interview.resume.application;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 确定性简历解析：AI 不可用或输出非法时兜底，只从原文抽取，不推断缺失信息。
 */
@Component
public class ResumeHeuristicParser {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?86[\\s-]?)?(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern GENERIC_PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{3,4}[\\s-]?\\d{3,4}[\\s-]?\\d{4})(?!\\d)");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "((?:19|20)\\d{2})[./年-]?(\\d{1,2})?\\s*(?:月)?\\s*[-–—~至到]+\\s*((?:19|20)\\d{2})[./年-]?(\\d{1,2})?");
    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d{1,2})\\s*年(?:以上)?[^。\\n]{0,12}?经验");
    private static final Pattern YEARS_EN_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*\\+?\\s*years?\\s+(?:of\\s+)?experience", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_YEARS_PATTERN = Pattern.compile("(\\d{1,2})\\s*年");
    private static final Pattern SCHOOL_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]{2,30}(?:大学|学院|学校|University|College)");
    private static final Pattern MAJOR_PATTERN = Pattern.compile("(?:专业[:：]?\\s*)?([\\u4e00-\\u9fa5A-Za-z]{2,20})专业");
    private static final Pattern COMPANY_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fa5A-Za-z0-9（）()]{2,40}(?:公司|科技|集团|网络|信息|软件|银行|研究院|事业部|中心|Inc|Ltd|Corp|Group)");
    private static final Pattern NAME_LABEL_PATTERN = Pattern.compile("^姓\\s*名[:：]\\s*(.{1,30})$");
    private static final Pattern CJK_NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}$");
    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2}$");
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?:高级|资深|首席|初级|中级)?(?:后端|前端|全栈|Java|Go|Python|测试|运维|算法|数据|安全|产品|项目|研发)?"
                    + "(?:开发|研发)?(?:工程师|架构师|经理|主管|专员|分析师|实习生|负责人|"
                    + "Developer|Engineer|Manager|Intern|Architect|Lead)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC_PATTERN = Pattern.compile("\\d+\\s*(?:%|％|万|亿|ms|毫秒|秒|QPS|TPS|倍|人|条|次|台)");
    private static final Pattern DATE_POINT_PATTERN = Pattern.compile("((?:19|20)\\d{2})(?:[./-](\\d{1,2}))?");

    private static final List<String> CITIES = List.of(
            "北京", "上海", "深圳", "广州", "杭州", "成都", "武汉", "南京", "西安", "苏州", "长沙", "重庆", "天津", "厦门", "合肥", "郑州", "青岛", "大连",
            "宁波", "东莞", "佛山", "珠海", "济南", "福州", "无锡", "昆明");

    private static final List<String> NAME_EXCLUDES = List.of("简历", "求职", "个人", "应聘", "电话", "邮箱", "基本信息", "意向");

    private static final Set<String> EDUCATION_HEADINGS = Set.of("教育", "教育背景", "教育经历", "学历", "学历背景", "教育情况");
    private static final Set<String> EXPERIENCE_HEADINGS =
            Set.of("工作经历", "工作经验", "职业经历", "实习经历", "工作履历", "实习经验", "工作背景");
    private static final Set<String> PROJECT_HEADINGS = Set.of("项目经历", "项目经验", "项目实践", "项目");
    private static final Set<String> SKILLS_HEADINGS =
            Set.of("技能", "专业技能", "技能清单", "技术栈", "个人技能", "技能特长", "掌握技能", "技术能力");
    private static final Set<String> CERTIFICATE_HEADINGS =
            Set.of("证书", "证书与荣誉", "荣誉证书", "荣誉", "获奖", "获奖情况", "资质证书", "资格证书", "培训证书");
    private static final Set<String> SUMMARY_HEADINGS = Set.of("自我评价", "个人总结", "自我介绍", "个人简介", "个人优势");

    private static final List<String> DEGREE_WORDS =
            List.of("博士", "硕士", "本科", "学士", "大专", "高中", "中专", "MBA", "Master", "Bachelor", "PhD");

    private static final List<String> ROLE_WORDS = List.of("负责人", "主导", "核心开发", "后端", "前端", "全栈", "架构", "测试", "成员");

    private static final List<String> SKILL_STOP_WORDS =
            List.of("技能", "专业技能", "技术栈", "熟悉", "掌握", "精通", "了解", "等", "其他", "以及", "熟练使用");

    private static final List<String> TECH_TOKENS = List.of(
            "Java",
            "Spring Boot",
            "Spring Cloud",
            "Spring",
            "MyBatis",
            "MySQL",
            "PostgreSQL",
            "Redis",
            "Kafka",
            "RabbitMQ",
            "RocketMQ",
            "Docker",
            "Kubernetes",
            "Linux",
            "Python",
            "Go",
            "C++",
            "JavaScript",
            "TypeScript",
            "React",
            "Vue",
            "Node.js",
            "Nginx",
            "Elasticsearch",
            "MongoDB",
            "Flink",
            "Spark",
            "Hadoop",
            "Hive",
            "ClickHouse",
            "Dubbo",
            "Netty",
            "ZooKeeper",
            "JVM",
            "SQL",
            "AWS",
            "阿里云",
            "腾讯云",
            "微服务",
            "高并发",
            "分布式",
            "消息队列",
            "缓存");

    private enum Section {
        BASIC,
        EDUCATION,
        EXPERIENCE,
        PROJECT,
        SKILLS,
        CERTIFICATES,
        SUMMARY
    }

    public Map<String, Object> parse(String rawText) {
        List<String> lines = splitLines(rawText);
        Map<Section, List<String>> sections = splitSections(lines);
        String email = findEmail(rawText);
        String phone = findPhone(rawText);
        String city = findCity(rawText);
        String name = findName(lines);

        List<Map<String, Object>> education = parseEducation(sections.getOrDefault(Section.EDUCATION, List.of()));
        List<Map<String, Object>> experiences = parseExperiences(sections.getOrDefault(Section.EXPERIENCE, List.of()));
        List<Map<String, Object>> projects = parseProjects(sections.getOrDefault(Section.PROJECT, List.of()));
        List<Map<String, Object>> skills = parseSkills(sections.getOrDefault(Section.SKILLS, List.of()));
        List<String> certificates = parseCertificates(sections.getOrDefault(Section.CERTIFICATES, List.of()));

        int years = findYears(rawText);
        if (years <= 0) {
            years = deriveYears(experiences);
        }

        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("name", name);
        basic.put("phoneMasked", maskPhone(phone));
        basic.put("emailMasked", maskEmail(email));
        basic.put("city", city);
        basic.put("yearsOfExperience", years);

        Map<String, Object> confidence = new LinkedHashMap<>();
        confidence.put("basic", basicConfidence(name, email, phone, city));
        confidence.put("education", sectionConfidence(education));
        confidence.put("experiences", sectionConfidence(experiences));
        confidence.put("projects", sectionConfidence(projects));
        confidence.put("skills", sectionConfidence(skills));
        confidence.put("certificates", certificates.isEmpty() ? 0.4 : 0.55);

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("basic", basic);
        structured.put("education", education);
        structured.put("experiences", experiences);
        structured.put("projects", projects);
        structured.put("skills", skills);
        structured.put("certificates", certificates);
        structured.put("confidence", confidence);
        return structured;
    }

    private static List<String> splitLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawText.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private Map<Section, List<String>> splitSections(List<String> lines) {
        Map<Section, List<String>> sections = new EnumMap<>(Section.class);
        Section current = Section.BASIC;
        for (String line : lines) {
            Section heading = headingOf(line);
            if (heading != null) {
                current = heading;
                continue;
            }
            sections.computeIfAbsent(current, key -> new ArrayList<>()).add(line);
        }
        return sections;
    }

    private Section headingOf(String line) {
        String cleaned =
                line.replaceAll("^[#*\\s\\d一二三四五六七八九十、.()（）【】\\[\\]]+", "").replaceAll("[:：\\s]+$", "");
        if (cleaned.length() < 2 || cleaned.length() > 16) {
            return null;
        }
        if (EDUCATION_HEADINGS.contains(cleaned)) {
            return Section.EDUCATION;
        }
        if (EXPERIENCE_HEADINGS.contains(cleaned)) {
            return Section.EXPERIENCE;
        }
        if (PROJECT_HEADINGS.contains(cleaned)) {
            return Section.PROJECT;
        }
        if (SKILLS_HEADINGS.contains(cleaned)) {
            return Section.SKILLS;
        }
        if (CERTIFICATE_HEADINGS.contains(cleaned)) {
            return Section.CERTIFICATES;
        }
        if (SUMMARY_HEADINGS.contains(cleaned)) {
            return Section.SUMMARY;
        }
        return null;
    }

    private String findName(List<String> lines) {
        int labeledLimit = Math.min(lines.size(), 15);
        for (int i = 0; i < labeledLimit; i++) {
            Matcher matcher = NAME_LABEL_PATTERN.matcher(lines.get(i));
            if (matcher.matches()) {
                String value = matcher.group(1).strip();
                if (!value.isEmpty()) {
                    return truncate(value, 30);
                }
            }
        }
        int plainLimit = Math.min(lines.size(), 8);
        for (int i = 0; i < plainLimit; i++) {
            String line = lines.get(i).strip();
            if (CJK_NAME_PATTERN.matcher(line).matches()
                    && NAME_EXCLUDES.stream().noneMatch(line::contains)) {
                return line;
            }
            if (EN_NAME_PATTERN.matcher(line).matches()) {
                return line;
            }
        }
        return null;
    }

    private static String findEmail(String rawText) {
        if (rawText == null) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(rawText);
        return matcher.find() ? matcher.group() : null;
    }

    private static String findPhone(String rawText) {
        if (rawText == null) {
            return null;
        }
        Matcher mobile = PHONE_PATTERN.matcher(rawText);
        if (mobile.find()) {
            return mobile.group(1);
        }
        Matcher generic = GENERIC_PHONE_PATTERN.matcher(rawText);
        return generic.find() ? generic.group(1) : null;
    }

    private static String findCity(String rawText) {
        if (rawText == null) {
            return null;
        }
        String matched = null;
        int matchedIndex = Integer.MAX_VALUE;
        for (String city : CITIES) {
            int index = rawText.indexOf(city);
            if (index >= 0 && index < matchedIndex) {
                matched = city;
                matchedIndex = index;
            }
        }
        return matched;
    }

    private static int findYears(String rawText) {
        if (rawText == null) {
            return 0;
        }
        Matcher matcher = YEARS_PATTERN.matcher(rawText);
        int years = 0;
        if (matcher.find()) {
            years = parsePositiveInt(matcher.group(1));
        } else {
            Matcher english = YEARS_EN_PATTERN.matcher(rawText);
            if (english.find()) {
                years = parsePositiveInt(english.group(1));
            }
        }
        return Math.min(years, 50);
    }

    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return null;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return null;
        }
        int prefixLength = Math.min(3, digits.length() - 4);
        return digits.substring(0, prefixLength) + "****" + digits.substring(digits.length() - 4);
    }

    private static double basicConfidence(String name, String email, String phone, String city) {
        double value = 0.40;
        if (name != null) {
            value += 0.10;
        }
        if (email != null) {
            value += 0.05;
        }
        if (phone != null) {
            value += 0.03;
        }
        if (city != null) {
            value += 0.02;
        }
        return Math.round(Math.min(value, 0.60) * 100.0) / 100.0;
    }

    private static double sectionConfidence(List<?> entries) {
        return entries.isEmpty() ? 0.4 : 0.55;
    }

    private List<Map<String, Object>> parseEducation(List<String> lines) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> current = null;
        for (String line : lines) {
            String cleaned = stripBullet(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            boolean header = SCHOOL_PATTERN.matcher(cleaned).find()
                    || DATE_RANGE_PATTERN.matcher(cleaned).find();
            if (header) {
                current = educationEntry(cleaned);
                entries.add(current);
            } else if (current != null) {
                fillEducation(current, cleaned);
            }
        }
        entries.removeIf(entry -> entry.get("school") == null);
        return entries.stream().limit(10).toList();
    }

    private Map<String, Object> educationEntry(String line) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Matcher school = SCHOOL_PATTERN.matcher(line);
        entry.put("school", school.find() ? school.group() : null);
        entry.put("degree", firstMatch(DEGREE_WORDS, line));
        Matcher major = MAJOR_PATTERN.matcher(line);
        entry.put("major", major.find() ? major.group(1) : null);
        putDateRange(entry, line);
        return entry;
    }

    private void fillEducation(Map<String, Object> entry, String line) {
        if (entry.get("school") == null) {
            Matcher school = SCHOOL_PATTERN.matcher(line);
            if (school.find()) {
                entry.put("school", school.group());
            }
        }
        if (entry.get("degree") == null) {
            entry.put("degree", firstMatch(DEGREE_WORDS, line));
        }
        if (entry.get("major") == null) {
            Matcher major = MAJOR_PATTERN.matcher(line);
            if (major.find()) {
                entry.put("major", major.group(1));
            }
        }
        if (entry.get("start") == null && entry.get("end") == null) {
            putDateRange(entry, line);
        }
    }

    private List<Map<String, Object>> parseExperiences(List<String> lines) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripBullet(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (isExperienceHeader(cleaned) && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(cleaned);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (List<String> group : groups) {
            Map<String, Object> entry = experienceEntry(group);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries.stream().limit(12).toList();
    }

    private boolean isExperienceHeader(String line) {
        return DATE_RANGE_PATTERN.matcher(line).find()
                || COMPANY_PATTERN.matcher(line).find()
                || (TITLE_PATTERN.matcher(line).find() && line.length() <= 40);
    }

    private Map<String, Object> experienceEntry(List<String> group) {
        String header = group.get(0);
        Map<String, Object> entry = new LinkedHashMap<>();
        Matcher company = COMPANY_PATTERN.matcher(header);
        String companyName = company.find() ? company.group() : null;
        String title = findTitle(header);
        if (companyName == null) {
            companyName = inferCompany(header);
        }
        entry.put("company", companyName);
        entry.put("title", title);
        putDateRange(entry, header);
        List<String> bullets = new ArrayList<>();
        for (int i = 1; i < group.size(); i++) {
            String bullet = stripBullet(group.get(i));
            if (bullet.length() > 1) {
                bullets.add(truncate(bullet, 200));
            }
        }
        entry.put("bullets", bullets.stream().limit(15).toList());
        if (companyName == null && title == null && bullets.isEmpty()) {
            return null;
        }
        return entry;
    }

    private static String inferCompany(String header) {
        String text = DATE_RANGE_PATTERN.matcher(header).replaceAll(" ");
        text = TITLE_PATTERN.matcher(text).replaceAll(" ");
        text = text.replaceAll("[|｜·•,，:：]+", " ").strip();
        return text.isEmpty() || text.length() > 60 ? null : text;
    }

    private static String findTitle(String line) {
        Matcher matcher = TITLE_PATTERN.matcher(line);
        return matcher.find() ? matcher.group().strip() : null;
    }

    private List<Map<String, Object>> parseProjects(List<String> lines) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripBullet(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (isProjectHeader(cleaned) && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(cleaned);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (List<String> group : groups) {
            entries.add(projectEntry(group));
        }
        return entries.stream().limit(8).toList();
    }

    private static boolean isProjectHeader(String line) {
        return line.contains("项目") && line.length() <= 60;
    }

    private Map<String, Object> projectEntry(List<String> group) {
        String header = group.get(0);
        String joined = String.join(" ", group);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", projectName(header));
        entry.put("role", firstMatch(ROLE_WORDS, header));
        entry.put(
                "stack",
                TECH_TOKENS.stream()
                        .filter(token -> containsIgnoreCase(joined, token))
                        .limit(10)
                        .toList());
        List<String> highlights = new ArrayList<>();
        for (int i = 1; i < group.size(); i++) {
            String bullet = stripBullet(group.get(i));
            if (bullet.length() > 1) {
                highlights.add(truncate(bullet, 200));
            }
        }
        entry.put("highlights", highlights.stream().limit(12).toList());
        entry.put(
                "metrics",
                highlights.stream()
                        .filter(line -> METRIC_PATTERN.matcher(line).find())
                        .limit(8)
                        .toList());
        return entry;
    }

    private static String projectName(String header) {
        String name = header.replaceAll("^项目[一二三四五六七八九十百0-9]*\\s*[:：\\-—|｜]?\\s*", "");
        name = DATE_RANGE_PATTERN.matcher(name).replaceAll(" ").strip();
        return name.isEmpty() ? "项目" : truncate(name, 40);
    }

    private List<Map<String, Object>> parseSkills(List<String> lines) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (String line : lines) {
            String cleanedLine = stripBullet(line);
            if (cleanedLine.isEmpty()) {
                continue;
            }
            String level = levelOf(cleanedLine);
            Integer years = firstInt(SKILL_YEARS_PATTERN, cleanedLine);
            for (String token : splitSkillTokens(cleanedLine)) {
                String name = normalizeSkillName(token);
                if (name == null) {
                    continue;
                }
                String key = name.toLowerCase(Locale.ROOT);
                if (byName.containsKey(key)) {
                    continue;
                }
                Map<String, Object> skill = new LinkedHashMap<>();
                skill.put("name", name);
                skill.put("level", level);
                skill.put("years", years);
                byName.put(key, skill);
            }
        }
        return byName.values().stream().limit(40).toList();
    }

    private static List<String> splitSkillTokens(String line) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : line.split("[、,，/;；|｜]+")) {
            String token = part.strip();
            if (token.length() > 24) {
                tokens.addAll(Arrays.asList(token.split("\\s+")));
            } else if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String normalizeSkillName(String token) {
        String cleaned = token.replaceAll("^(?:熟悉|掌握|精通|了解|熟练使用|熟练|使用|具备|良好的|扎实的|较强的|一定的|相关|能|会)+", "")
                .replaceAll("[（(].*?[)）]", "")
                .replaceAll("[。.；;：:]+$", "")
                .strip();
        if (cleaned.length() < 2 || cleaned.length() > 30) {
            return null;
        }
        if (SKILL_STOP_WORDS.contains(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private static String levelOf(String line) {
        if (line.contains("精通")) {
            return "ADVANCED";
        }
        if (line.contains("熟练") || line.contains("掌握") || line.contains("熟悉")) {
            return "INTERMEDIATE";
        }
        if (line.contains("了解") || line.contains("接触")) {
            return "BEGINNER";
        }
        return "INTERMEDIATE";
    }

    private static List<String> parseCertificates(List<String> lines) {
        List<String> entries = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripBullet(line);
            if (cleaned.isEmpty() || cleaned.length() > 120) {
                continue;
            }
            entries.add(cleaned);
        }
        return entries.stream().distinct().limit(10).toList();
    }

    private static int deriveYears(List<Map<String, Object>> experiences) {
        YearMonth earliest = null;
        YearMonth latest = null;
        for (Map<String, Object> experience : experiences) {
            YearMonth start = parseDatePoint(experience.get("start"));
            YearMonth end = parseDatePoint(experience.get("end"));
            if (start != null && (earliest == null || start.isBefore(earliest))) {
                earliest = start;
            }
            if (end != null && (latest == null || end.isAfter(latest))) {
                latest = end;
            }
        }
        if (earliest == null) {
            return 0;
        }
        YearMonth end = latest == null ? YearMonth.now() : latest;
        long months = ChronoUnit.MONTHS.between(earliest, end);
        return (int) Math.max(0, Math.min(months / 12, 50));
    }

    private static YearMonth parseDatePoint(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_POINT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int year = parsePositiveInt(matcher.group(1));
        int month = matcher.group(2) == null ? 1 : parsePositiveInt(matcher.group(2));
        if (year <= 0) {
            return null;
        }
        if (month < 1 || month > 12) {
            month = 1;
        }
        return YearMonth.of(year, month);
    }

    private void putDateRange(Map<String, Object> entry, String line) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line);
        if (matcher.find()) {
            entry.put("start", formatDatePoint(matcher.group(1), matcher.group(2)));
            entry.put("end", formatDatePoint(matcher.group(3), matcher.group(4)));
        } else {
            entry.put("start", null);
            entry.put("end", null);
        }
    }

    private static String formatDatePoint(String year, String month) {
        if (year == null || year.isBlank()) {
            return null;
        }
        if (month == null || month.isBlank()) {
            return year;
        }
        int monthValue = parsePositiveInt(month);
        if (monthValue < 1 || monthValue > 12) {
            return year;
        }
        return year + "-" + String.format(Locale.ROOT, "%02d", monthValue);
    }

    private static String firstMatch(List<String> words, String text) {
        for (String word : words) {
            if (text.contains(word)) {
                return word;
            }
        }
        return null;
    }

    private static Integer firstInt(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int value = parsePositiveInt(matcher.group(1));
        return value <= 0 || value > 50 ? null : value;
    }

    private static int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String stripBullet(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = line.strip().replaceAll("^[•·●○▪◦\\-*–—]+\\s*", "").replaceAll("^\\d+[.)、]\\s*", "");
        return cleaned.strip();
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
