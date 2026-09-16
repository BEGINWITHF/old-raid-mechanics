package com.example.oldraids;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Immutable snapshot of config.yml.
 *
 * The file is read straight from disk with YamlConfiguration instead of JavaPlugin#getConfig(),
 * because the cached FileConfiguration is known to return stale lists/values after a reload.
 */
public final class PluginConfig {

    public final boolean enabled;
    public final boolean badOmenTriggersRaid;
    public final int raidOmenTicks;
    public final boolean captainGivesBadOmen;
    public final boolean removeOminousBottleDrop;
    public final boolean oldSpawnPositions;
    public final int radiusFactor;
    public final int teleportDelayTicks;
    public final boolean debug;

    private PluginConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.badOmenTriggersRaid = cfg.getBoolean("features.bad-omen-triggers-raid", true);
        this.raidOmenTicks = Math.max(2, Math.min(600, cfg.getInt("trigger.raid-omen-ticks", 2)));
        this.captainGivesBadOmen = cfg.getBoolean("features.captain-gives-bad-omen", true);
        this.removeOminousBottleDrop = cfg.getBoolean("features.remove-ominous-bottle-drop", false);
        this.oldSpawnPositions = cfg.getBoolean("features.old-spawn-positions", true);
        this.radiusFactor = Math.max(0, Math.min(2, cfg.getInt("spawn.radius-factor", 2)));
        this.teleportDelayTicks = Math.max(0, cfg.getInt("spawn.teleport-delay-ticks", 1));
        this.debug = cfg.getBoolean("debug", false);
    }

    public static PluginConfig load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
        }
        return new PluginConfig(YamlConfiguration.loadConfiguration(file));
    }

    /** Comma separated list of the features that are currently on; empty when nothing is enabled. */
    public String enabledFeatureList() {
        List<String> names = new ArrayList<>();
        if (!enabled) {
            return "";
        }
        if (badOmenTriggersRaid) {
            names.add("bad-omen-triggers-raid");
        }
        if (captainGivesBadOmen) {
            names.add("captain-gives-bad-omen");
        }
        if (oldSpawnPositions) {
            names.add("old-spawn-positions");
        }
        return String.join(", ", names);
    }
}
