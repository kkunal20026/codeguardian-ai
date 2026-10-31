package com.codehealth.agent.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, strict unified diff model: parse, apply and write. Used to check LLM-generated patches before any
 * human or tool sees them, and to produce patches from the template generator.
 */
public final class UnifiedDiff {

    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

    private UnifiedDiff() {
    }

    public record Hunk(int oldStart, List<String> lines) {
    }

    public record FilePatch(String path, List<Hunk> hunks) {

        public int linesChanged() {
            return (int) hunks.stream().flatMap(h -> h.lines().stream())
                    .filter(l -> l.startsWith("+") || l.startsWith("-"))
                    .count();
        }
    }

    public static final class PatchException extends RuntimeException {
        public PatchException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- parse

    public static List<FilePatch> parse(String diff) {
        if (diff == null || diff.isBlank()) {
            throw new PatchException("Patch is empty");
        }
        String[] raw = diff.replace("\r\n", "\n").split("\n", -1);
        List<FilePatch> files = new ArrayList<>();
        String currentPath = null;
        List<Hunk> hunks = null;
        List<String> hunkLines = null;
        int hunkOldStart = 0;

        for (String line : raw) {
            if (line.startsWith("--- ")) {
                continue;
            }
            if (line.startsWith("+++ ")) {
                if (currentPath != null) {
                    closeHunk(hunks, hunkLines, hunkOldStart);
                    files.add(new FilePatch(currentPath, List.copyOf(hunks)));
                }
                currentPath = stripPrefix(line.substring(4).trim());
                hunks = new ArrayList<>();
                hunkLines = null;
                continue;
            }
            Matcher m = HUNK.matcher(line);
            if (m.matches()) {
                if (currentPath == null) {
                    throw new PatchException("Hunk before file header");
                }
                closeHunk(hunks, hunkLines, hunkOldStart);
                hunkOldStart = Integer.parseInt(m.group(1));
                hunkLines = new ArrayList<>();
                continue;
            }
            if (hunkLines != null && (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-"))) {
                hunkLines.add(line);
            } else if (hunkLines != null && line.isEmpty()) {
                hunkLines.add(" ");
            }
        }
        if (currentPath != null) {
            closeHunk(hunks, hunkLines, hunkOldStart);
            files.add(new FilePatch(currentPath, List.copyOf(hunks)));
        }
        if (files.isEmpty() || files.stream().allMatch(f -> f.hunks().isEmpty())) {
            throw new PatchException("No file hunks found in patch");
        }
        return files;
    }

    private static void closeHunk(List<Hunk> hunks, List<String> lines, int oldStart) {
        if (lines != null) {
            while (!lines.isEmpty() && " ".equals(lines.getLast())) {
                lines.removeLast();
            }
            if (!lines.isEmpty()) {
                hunks.add(new Hunk(oldStart, List.copyOf(lines)));
            }
        }
    }

    private static String stripPrefix(String path) {
        int tab = path.indexOf('\t');
        String clean = tab >= 0 ? path.substring(0, tab) : path;
        if (clean.startsWith("a/") || clean.startsWith("b/")) {
            clean = clean.substring(2);
        }
        if (clean.contains("..") || clean.startsWith("/") || clean.contains("{") || clean.contains("}")
                || clean.contains(":")) {
            throw new PatchException("Unsafe path in patch: " + clean);
        }
        return clean;
    }

    // ---------------------------------------------------------------- apply

    /**
     * Applies hunks with exact context matching. A small search window tolerates wrong line numbers, which LLMs
     * often get wrong, but the context itself must match exactly.
     */
    public static String apply(String original, FilePatch patch) {
        boolean trailingNewline = original.endsWith("\n");
        List<String> lines = new ArrayList<>(List.of(original.replace("\r\n", "\n").split("\n", -1)));
        if (trailingNewline) {
            lines.removeLast();
        }
        int offset = 0;
        for (Hunk hunk : patch.hunks()) {
            List<String> expected = hunk.lines().stream().filter(l -> !l.startsWith("+")).map(l -> l.substring(1)).toList();
            List<String> replacement = hunk.lines().stream().filter(l -> !l.startsWith("-")).map(l -> l.substring(1)).toList();
            int anchor = Math.max(0, hunk.oldStart() - 1 + offset);
            int at = locate(lines, expected, anchor);
            if (at < 0) {
                throw new PatchException("Hunk at line " + hunk.oldStart() + " does not match " + patch.path());
            }
            for (int i = 0; i < expected.size(); i++) {
                lines.remove(at);
            }
            lines.addAll(at, replacement);
            offset += replacement.size() - expected.size();
        }
        return String.join("\n", lines) + (trailingNewline ? "\n" : "");
    }

    /**
     * True when {@code apply} would succeed on {@code content}.
     */
    public static boolean appliesTo(String content, FilePatch patch) {
        try {
            apply(content, patch);
            return true;
        } catch (PatchException ex) {
            return false;
        }
    }

    /**
     * The inverse patch: added lines become removed lines and vice versa. Applying it to patched content gives
     * back the original, so it also tells whether a patch is already present in a file.
     */
    public static FilePatch reverse(FilePatch patch) {
        List<Hunk> hunks = patch.hunks().stream()
                .map(h -> new Hunk(h.oldStart(), h.lines().stream()
                        .map(l -> l.startsWith("+") ? "-" + l.substring(1)
                                : l.startsWith("-") ? "+" + l.substring(1) : l)
                        .toList()))
                .toList();
        return new FilePatch(patch.path(), hunks);
    }

    private static int locate(List<String> lines, List<String> expected, int anchor) {
        for (int distance = 0; distance <= 50; distance++) {
            for (int candidate : new int[]{anchor - distance, anchor + distance}) {
                if (candidate >= 0 && matchesAt(lines, expected, candidate)) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    private static boolean matchesAt(List<String> lines, List<String> expected, int at) {
        if (at + expected.size() > lines.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!lines.get(at + i).stripTrailing().equals(expected.get(i).stripTrailing())) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- write

    /**
     * Produces a one-file unified diff (3 lines of context) using an LCS line diff.
     */
    public static String write(String path, String before, String after) {
        List<String> a = List.of(before.replace("\r\n", "\n").split("\n", -1));
        List<String> b = List.of(after.replace("\r\n", "\n").split("\n", -1));
        List<String[]> ops = lcsOps(a, b);

        StringBuilder out = new StringBuilder("--- a/").append(path).append('\n')
                .append("+++ b/").append(path).append('\n');
        int context = 3;
        int i = 0;
        while (i < ops.size()) {
            if (" ".equals(ops.get(i)[0])) {
                i++;
                continue;
            }
            int start = Math.max(0, i - context);
            int end = i;
            int lastChange = i;
            while (end < ops.size() && (end - lastChange) <= context * 2) {
                if (!" ".equals(ops.get(end)[0])) {
                    lastChange = end;
                }
                end++;
            }
            end = Math.min(ops.size(), lastChange + context + 1);
            int oldLine = 1 + (int) ops.subList(0, start).stream().filter(o -> !"+".equals(o[0])).count();
            int newLine = 1 + (int) ops.subList(0, start).stream().filter(o -> !"-".equals(o[0])).count();
            List<String[]> hunk = ops.subList(start, end);
            long oldCount = hunk.stream().filter(o -> !"+".equals(o[0])).count();
            long newCount = hunk.stream().filter(o -> !"-".equals(o[0])).count();
            out.append("@@ -").append(oldLine).append(',').append(oldCount)
                    .append(" +").append(newLine).append(',').append(newCount).append(" @@\n");
            hunk.forEach(o -> out.append(o[0]).append(o[1]).append('\n'));
            i = end;
        }
        return out.toString();
    }

    private static List<String[]> lcsOps(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int x = n - 1; x >= 0; x--) {
            for (int y = m - 1; y >= 0; y--) {
                dp[x][y] = a.get(x).equals(b.get(y)) ? dp[x + 1][y + 1] + 1 : Math.max(dp[x + 1][y], dp[x][y + 1]);
            }
        }
        List<String[]> ops = new ArrayList<>();
        int x = 0;
        int y = 0;
        while (x < n && y < m) {
            if (a.get(x).equals(b.get(y))) {
                ops.add(new String[]{" ", a.get(x++)});
                y++;
            } else if (dp[x + 1][y] >= dp[x][y + 1]) {
                ops.add(new String[]{"-", a.get(x++)});
            } else {
                ops.add(new String[]{"+", b.get(y++)});
            }
        }
        while (x < n) {
            ops.add(new String[]{"-", a.get(x++)});
        }
        while (y < m) {
            ops.add(new String[]{"+", b.get(y++)});
        }
        return ops;
    }

    public static Map<String, Integer> stats(List<FilePatch> patches) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        patches.forEach(p -> stats.put(p.path(), p.linesChanged()));
        return stats;
    }
}
