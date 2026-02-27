package com.gtceuterminal.common.config;

import com.gtceuterminal.GTCEUTerminalMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** A simple YAML config loader that supports comments and preserves user edits.
 * - Can read/write scalar values and lists (both block and inline style).
 * - When writing defaults, it only appends missing keys to preserve existing comments and formatting.
 * - Logs warnings for type mismatches and duplicate keys.
 */
public class YamlConfigLoader {

    private final File file;
    // LinkedHashMap preserves insertion order.
    private final Map<String, Object> data = new LinkedHashMap<>();

    public YamlConfigLoader(String subDir, String fileName) {
        Path dir = (subDir == null || subDir.isBlank())
                ? FMLPaths.CONFIGDIR.get()
                : FMLPaths.CONFIGDIR.get().resolve(subDir);
        dir.toFile().mkdirs();
        this.file = dir.resolve(fileName).toFile();
    }

    // ─── Public API ──────────────────────────────────────────────────────────
    public int getInt(String key, int defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) {
                GTCEUTerminalMod.LOGGER.warn("[YamlConfig] Key '{}' expected int but got '{}', using default {}",
                        key, s, defaultValue);
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = data.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) {
            String lower = s.trim().toLowerCase();
            if (lower.equals("true") || lower.equals("yes")) return true;
            if (lower.equals("false") || lower.equals("no")) return false;
            GTCEUTerminalMod.LOGGER.warn("[YamlConfig] Key '{}' expected boolean but got '{}', using default {}",
                    key, s, defaultValue);
        }
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object val = data.get(key);
        if (val == null) return defaultValue;
        return val.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object val = data.get(key);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) result.add(item.toString().trim());
            return result;
        }
        // If someone put a single value where a list is expected, wrap it
        if (val instanceof String s && !s.isBlank()) {
            GTCEUTerminalMod.LOGGER.warn("[YamlConfig] Key '{}' expected a list but found a single value '{}', wrapping it.", key, s);
            return List.of(s.trim());
        }
        return new ArrayList<>();
    }

    public boolean exists() {
        return file.exists();
    }

    // ─── Load ────────────────────────────────────────────────────────────────
    public void load() {
        if (!file.exists()) return;
        data.clear();
        Set<String> seenKeys = new LinkedHashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String currentListKey = null;
            String line;

            while ((line = reader.readLine()) != null) {
                // Normalize Windows line endings
                line = line.replace("\r", "");

                // Detect list items BEFORE stripping comments (the dash is unambiguous)
                String stripped = line.stripLeading();
                if (stripped.startsWith("- ")) {
                    if (currentListKey != null) {
                        String value = stripped.substring(2).trim();
                        value = stripInlineComment(value);
                        value = unquote(value);
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) data.computeIfAbsent(currentListKey, k -> new ArrayList<>());
                        list.add(value);
                    }
                    continue;
                }

                // For key:value lines, strip inline comments respecting quoted strings
                line = stripInlineComment(line);
                if (line.isBlank()) continue;

                int colonIdx = line.indexOf(':');
                if (colonIdx < 0) continue;

                String key   = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                currentListKey = null;

                if (key.isBlank()) continue;

                // Warn on duplicate keys
                if (seenKeys.contains(key)) {
                    GTCEUTerminalMod.LOGGER.warn("[YamlConfig] Duplicate key '{}' in {}, last value wins.", key, file.getName());
                }
                seenKeys.add(key);

                if (value.isEmpty()) {
                    // Next lines should be a block list
                    currentListKey = key;
                    data.put(key, new ArrayList<>());
                } else if (value.startsWith("[") && value.endsWith("]")) {
                    // Inline list: [a, b, c]
                    data.put(key, parseInlineList(value));
                } else {
                    // Scalar value
                    value = unquote(value);
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
                        data.put(key, Boolean.TRUE);
                    } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
                        data.put(key, Boolean.FALSE);
                    } else {
                        try {
                            data.put(key, Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            data.put(key, value);
                        }
                    }
                }
            }
        } catch (IOException e) {
            GTCEUTerminalMod.LOGGER.error("[YamlConfig] Failed to load {}: {}", file.getName(), e.getMessage());
        }
    }

    // ─── Write ───────────────────────────────────────────────────────────────
    public void writeDefaults(List<ConfigEntry> entries) {
        if (file.exists()) {
            load();
            List<ConfigEntry> missing = entries.stream()
                    .filter(e -> !data.containsKey(e.key))
                    .toList();
            if (missing.isEmpty()) return;

            // Append missing keys to the existing file
            GTCEUTerminalMod.LOGGER.info("[YamlConfig] Adding {} missing key(s) to {}", missing.size(), file.getName());
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.println();
                writer.println("# --- Auto-added missing keys ---");
                for (ConfigEntry entry : missing) {
                    writeEntry(writer, entry);
                }
            } catch (IOException e) {
                GTCEUTerminalMod.LOGGER.error("[YamlConfig] Failed to append missing keys to {}: {}", file.getName(), e.getMessage());
            }
            load();
        } else {
            // Write fresh file
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (ConfigEntry entry : entries) {
                    writeEntry(writer, entry);
                }
            } catch (IOException e) {
                GTCEUTerminalMod.LOGGER.error("[YamlConfig] Failed to write {}: {}", file.getName(), e.getMessage());
            }
            load();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private static String stripInlineComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble) {
                return line.substring(0, i).trim();
            }
        }
        return line.trim();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'")  && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static List<String> parseInlineList(String raw) {
        String inner = raw.substring(1, raw.length() - 1).trim();
        if (inner.isBlank()) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"'  && !inSingle) { inDouble = !inDouble; continue; }
            if (c == ',' && !inSingle && !inDouble) {
                String item = current.toString().trim();
                if (!item.isBlank()) result.add(item);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isBlank()) result.add(last);
        return result;
    }

    private static void writeEntry(PrintWriter writer, ConfigEntry entry) {
        if (entry.comment != null && !entry.comment.isBlank()) {
            writer.println("# " + entry.comment);
        }
        if (entry.value instanceof List<?> list) {
            writer.println(entry.key + ":");
            for (Object item : list) {
                writer.println("  - " + item);
            }
        } else {
            writer.println(entry.key + ": " + entry.value);
        }
    }

    // ─── Entry helper ────────────────────────────────────────────────────────
    public record ConfigEntry(String key, Object value, String comment) {
        public static ConfigEntry of(String key, Object value, String comment) {
            return new ConfigEntry(key, value, comment);
        }
        public static ConfigEntry of(String key, Object value) {
            return new ConfigEntry(key, value, null);
        }
    }
}