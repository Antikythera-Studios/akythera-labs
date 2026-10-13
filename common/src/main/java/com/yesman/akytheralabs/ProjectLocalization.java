package com.yesman.akytheralabs;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.*;

/// The open project's lang entries: every key with its text per language code, kept in
/// [#FILE] at the project root. A key stays an entry while no language fills it.
///
/// Keys are dot-separated paths, `gui.ability_screen` sitting under `gui`.
@NullMarked
public final class ProjectLocalization {
    public static final String FILE = "localization.akproject";

    private static final String DEFAULT_LANGUAGE = "en_us";

    public static final Codec<ProjectLocalization> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("language", DEFAULT_LANGUAGE).forGetter(localization -> localization.language),
        Codec.BOOL.optionalFieldOf("export_all_languages", false).forGetter(localization -> localization.exportAllLanguages),
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING))
            .optionalFieldOf("entries", Map.of())
            .forGetter(localization -> localization.entries)
    ).apply(instance, ProjectLocalization::new));

    /// Text of every key by language code, sorted by key.
    private final SortedMap<String, Map<String, String>> entries = new TreeMap<>();

    /// The language [LocalizationManagerModal] edits, and the one an exported language falls back to.
    private String language;

    /// Whether an export writes every language the game offers, not only the filled ones.
    private boolean exportAllLanguages;

    public ProjectLocalization() {
        this(DEFAULT_LANGUAGE, false, Map.of());
    }

    private ProjectLocalization(String language, boolean exportAllLanguages, Map<String, Map<String, String>> entries) {
        this.language = language;
        this.exportAllLanguages = exportAllLanguages;

        entries.forEach((key, texts) -> this.entries.put(key, new HashMap<>(texts)));
    }

    public static ProjectLocalization load(Path projectRoot) {
        return FileSystemHelper.readJson(projectRoot.resolve(FILE), CODEC, new ProjectLocalization());
    }

    public void save(Path projectRoot) {
        FileSystemHelper.writeJson(projectRoot.resolve(FILE), CODEC, this);
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isExportAllLanguages() {
        return exportAllLanguages;
    }

    public void setExportAllLanguages(boolean exportAllLanguages) {
        this.exportAllLanguages = exportAllLanguages;
    }

    /// Every key, sorted.
    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /// The text `key` shows in `language`, empty when it has none.
    public String text(String key, String language) {
        Map<String, String> texts = entries.get(key);

        return texts == null ? "" : texts.getOrDefault(language, "");
    }

    /// The text `key` shows in `language`: its own, else its text in [#language]. Empty when both are.
    public String resolve(String key, String language) {
        String text = text(key, language);

        return text.isEmpty() ? text(key, this.language) : text;
    }

    /// Fills `key` in `language`, adding the key when it is new. An empty text clears the language.
    public void setText(String key, String language, String text) {
        Map<String, String> texts = entries.computeIfAbsent(key, _ -> new HashMap<>());

        if (text.isEmpty()) {
            texts.remove(language);
        } else {
            texts.put(language, text);
        }
    }

    public void add(String key) {
        entries.putIfAbsent(key, new HashMap<>());
    }

    /// Removes `key` and every key under it.
    public void removeTree(String key) {
        entries.keySet().removeIf(k -> isUnder(k, key));
    }

    /// Moves `from` and every key under it to `to`. False, changing nothing, when a moved key would
    /// land on a key that stays.
    public boolean moveTree(String from, String to) {
        if (from.equals(to)) return true;

        Map<String, Map<String, String>> moved = new HashMap<>();

        entries.forEach((key, texts) -> {
            if (isUnder(key, from)) moved.put(to + key.substring(from.length()), texts);
        });

        for (String target : moved.keySet()) {
            if (entries.containsKey(target) && !isUnder(target, from)) return false;
        }

        entries.keySet().removeIf(k -> isUnder(k, from));
        entries.putAll(moved);

        return true;
    }

    /// Whether `key` is `root` or sits beneath it.
    public static boolean isUnder(String key, String root) {
        return key.equals(root) || key.startsWith(root + ".");
    }

    /// Whether `key` is a non-empty run of dot-separated segments, none of them empty or holding a space.
    public static boolean isValidKey(String key) {
        if (key.isEmpty() || key.chars().anyMatch(Character::isWhitespace)) return false;

        return Arrays.stream(key.split("\\.", -1)).noneMatch(String::isEmpty);
    }

    /// The lang files an export writes, by language code.
    ///
    /// Writes every language some key fills, or every one of `availableLanguages` under
    /// [#exportAllLanguages]. A key a language leaves empty takes its text in [#language], and is left
    /// out when that is empty too. A language left with no key is not written.
    public Map<String, JsonObject> exportedLangFiles(Collection<String> availableLanguages) {
        Set<String> codes = new TreeSet<>();

        if (exportAllLanguages) {
            codes.addAll(availableLanguages);
        } else {
            entries.values().forEach(texts -> codes.addAll(texts.keySet()));
        }

        Map<String, JsonObject> files = new TreeMap<>();

        for (String code : codes) {
            JsonObject langFile = new JsonObject();

            for (String key : entries.keySet()) {
                String text = resolve(key, code);
                if (!text.isEmpty()) langFile.addProperty(key, text);
            }

            if (!langFile.isEmpty()) files.put(code, langFile);
        }

        return files;
    }
}
