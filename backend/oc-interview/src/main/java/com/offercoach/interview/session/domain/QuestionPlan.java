package com.offercoach.interview.session.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出题计划：按维度配比（DEPTH 40%、PROJECT 30%、BEHAVIOR 20%、FIT 剩余）与难度阶梯生成题目槽位。
 */
public record QuestionPlan(List<Slot> slots) {

    public static final double BANK_RATIO = 0.6;

    private static final Map<String, Double> DIMENSION_WEIGHTS = new LinkedHashMap<>();

    static {
        DIMENSION_WEIGHTS.put("DEPTH", 0.4);
        DIMENSION_WEIGHTS.put("PROJECT", 0.3);
        DIMENSION_WEIGHTS.put("BEHAVIOR", 0.2);
        DIMENSION_WEIGHTS.put("FIT", 0.1);
    }

    /**
     * 每个维度一个槽位，难度按配置生成阶梯（MIXED 时 EASY→MEDIUM→HARD 递进）。
     */
    public record Slot(String dimension, String difficulty) {}

    public static QuestionPlan of(int questionCount, String difficulty) {
        int count = Math.max(1, questionCount);
        List<String> dimensions = allocateDimensions(count);
        List<String> difficulties = allocateDifficulties(count, difficulty);
        List<Slot> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            slots.add(new Slot(dimensions.get(index), difficulties.get(index)));
        }
        return new QuestionPlan(List.copyOf(slots));
    }

    /** 至少多少题必须来自题库。 */
    public int bankMinimum() {
        return (int) Math.ceil(slots.size() * BANK_RATIO);
    }

    private static List<String> allocateDimensions(int count) {
        double[] weights = DIMENSION_WEIGHTS.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        String[] names = DIMENSION_WEIGHTS.keySet().toArray(new String[0]);
        int[] base = new int[names.length];
        double[] remainders = new double[names.length];
        int assigned = 0;
        for (int index = 0; index < names.length; index++) {
            double exact = count * weights[index];
            base[index] = (int) Math.floor(exact);
            remainders[index] = exact - base[index];
            assigned += base[index];
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble((Integer index) -> remainders[index])
                .reversed()
                .thenComparingInt(index -> index));
        int cursor = 0;
        while (assigned < count) {
            base[order.get(cursor % order.size())]++;
            assigned++;
            cursor++;
        }
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < names.length; index++) {
            for (int repeat = 0; repeat < base[index]; repeat++) {
                result.add(names[index]);
            }
        }
        return result;
    }

    private static List<String> allocateDifficulties(int count, String difficulty) {
        String normalized = difficulty == null ? "MIXED" : difficulty.toUpperCase();
        if ("EASY".equals(normalized) || "MEDIUM".equals(normalized) || "HARD".equals(normalized)) {
            return java.util.Collections.nCopies(count, normalized);
        }
        int easy = Math.max(1, (int) Math.round(count * 0.3));
        int medium = Math.max(1, (int) Math.round(count * 0.5));
        int hard = Math.max(1, count - easy - medium);
        while (easy + medium + hard > count) {
            if (hard >= medium && hard >= easy && hard > 0) {
                hard--;
            } else if (medium >= easy && medium > 0) {
                medium--;
            } else {
                easy--;
            }
        }
        while (easy + medium + hard < count) {
            medium++;
        }
        List<String> result = new ArrayList<>(count);
        result.addAll(java.util.Collections.nCopies(easy, "EASY"));
        result.addAll(java.util.Collections.nCopies(medium, "MEDIUM"));
        result.addAll(java.util.Collections.nCopies(hard, "HARD"));
        return result;
    }
}
