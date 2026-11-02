package com.meekdev.amnetic.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public final class ShaderSwitches {
    public static final String NATIVE = "AMNETIC_NATIVE";
    public static final String ZERO_TO_ONE = "AMNETIC_ZERO_TO_ONE";
    private static final Set<String> KNOWN = Set.of(NATIVE, ZERO_TO_ONE);

    private record Branch(boolean known, boolean taken) {
        Branch flipped() {
            return new Branch(known, !taken);
        }
    }

    private ShaderSwitches() {}

    public static String apply(String source, Set<String> enabled) {
        List<String> kept = new ArrayList<>();
        Deque<Branch> branches = new ArrayDeque<>();
        for (String line : source.split("\n", -1)) {
            String[] words = directive(line);
            String keyword = words.length > 0 ? words[0] : "";
            String name = words.length > 1 ? words[1] : "";
            switch (keyword) {
                case "ifdef", "ifndef" -> {
                    if (KNOWN.contains(name)) {
                        boolean defined = enabled.contains(name);
                        branches.push(new Branch(true, keyword.equals("ifdef") == defined));
                        continue;
                    }
                    branches.push(new Branch(false, true));
                }
                case "if" -> branches.push(new Branch(false, true));
                case "elif" -> {
                    if (top(branches).known()) throw new IllegalArgumentException("#elif after a shader switch");
                }
                case "else" -> {
                    if (top(branches).known()) {
                        branches.push(branches.pop().flipped());
                        continue;
                    }
                }
                case "endif" -> {
                    if (branches.pop().known()) continue;
                }
                default -> {
                }
            }
            if (visible(branches)) kept.add(line);
        }
        if (!branches.isEmpty()) throw new IllegalArgumentException("unclosed #ifdef in shader");
        return String.join("\n", kept);
    }

    private static String[] directive(String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith("#")) return new String[0];
        return trimmed.substring(1).strip().split("\\s+");
    }

    private static Branch top(Deque<Branch> branches) {
        if (branches.isEmpty()) throw new IllegalArgumentException("#else or #elif without #if in shader");
        return branches.peek();
    }

    private static boolean visible(Deque<Branch> branches) {
        for (Branch branch : branches) {
            if (branch.known() && !branch.taken()) return false;
        }
        return true;
    }
}
