package com.example.oldraids;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * OldRaidMechanics - brings the pre-1.21.2 raid mechanics back to Paper 26.2.
 */
public final class OldRaidMechanicsPlugin extends JavaPlugin {

    private PluginConfig config;
    private Messages messages;
    private final Nms nms = new Nms();
    private RaidService service;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        config = PluginConfig.load(this);
        messages = new Messages(this);
        if (!nms.init(this)) {
            getLogger().severe("Old spawn positions and the captain raid check are unavailable on this server build.");
        }

        service = new RaidService(this);
        getServer().getPluginManager().registerEvents(new RaidListeners(this), this);

        PluginCommand command = getCommand("oldraid");
        if (command != null) {
            OldRaidCommand handler = new OldRaidCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        String features = config.enabledFeatureList();
        getLogger().info("Enabled - features: " + (features.isEmpty() ? messages.get("no-feature") : features));
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config = PluginConfig.load(this);
        messages.reload();
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public Nms nms() {
        return nms;
    }

    public RaidService service() {
        return service;
    }
}
