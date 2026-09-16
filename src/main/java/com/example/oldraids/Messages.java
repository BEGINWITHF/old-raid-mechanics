package com.example.oldraids;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Texts for the command output and the status lines, read from messages.yml in the plugin data folder
 * (a missing file is recreated from the jar). A key that is missing falls back to the key name, so a
 * half-edited file never turns into an empty message.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final Map<String, String> values = new HashMap<>();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        values.clear();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            String value = cfg.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    /** Returns the message for {@code key}, with {name} placeholders replaced by the given pairs. */
    public String get(String key, String... replacements) {
        String raw = values.getOrDefault(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return raw;
    }

    public String prefix() {
        return get("prefix");
    }
}
