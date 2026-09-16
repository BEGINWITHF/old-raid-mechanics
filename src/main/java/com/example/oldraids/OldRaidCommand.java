package com.example.oldraids;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /oldraid reload|status - the only two subcommands, everything else is config driven. */
public final class OldRaidCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "status");
    private static final DateTimeFormatter BUILD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OldRaidMechanicsPlugin plugin;

    public OldRaidCommand(OldRaidMechanicsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("oldraid.admin")) {
            sender.sendMessage(messages.prefix() + messages.get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.prefix() + messages.get("usage"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                Messages msg = plugin.messages();
                sender.sendMessage(msg.prefix() + msg.get("reloaded",
                        "features", featureList(msg, plugin.config())));
            }
            case "status" -> {
                Messages msg = plugin.messages();
                PluginConfig cfg = plugin.config();
                sender.sendMessage(msg.prefix() + msg.get("status-header",
                        "version", plugin.getPluginMeta().getVersion(),
                        "state", msg.get(cfg.enabled ? "state-on" : "state-off")));
                sender.sendMessage(msg.get("status-features", "features", featureList(msg, cfg)));
                sender.sendMessage(msg.get("status-nms",
                        "state", plugin.nms().isReady() ? msg.get("nms-ready")
                                : msg.get("nms-unavailable", "error", plugin.nms().error())));
                sender.sendMessage(msg.get("status-config",
                        "radius", String.valueOf(cfg.radiusFactor),
                        "delay", String.valueOf(cfg.teleportDelayTicks)));
                sender.sendMessage(msg.get("status-build", "build", buildStamp()));
            }
            default -> sender.sendMessage(messages.prefix() + messages.get("usage"));
        }
        return true;
    }

    private String featureList(Messages messages, PluginConfig config) {
        String list = config.enabledFeatureList();
        return list.isEmpty() ? messages.get("no-feature") : list;
    }

    /**
     * Build stamp of the running plugin: "build-info.txt" is written into the jar by the build, so it
     * describes the classes that are loaded right now. The jar file timestamp is only a fallback - it
     * changes when the file on disk is replaced, which says nothing about the loaded build.
     */
    private String buildStamp() {
        try (InputStream stream = getClass().getResourceAsStream("/build-info.txt")) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
            // fall through to the jar timestamp
        }
        try {
            URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
            File jar = new File(location.toURI());
            return BUILD_FORMAT.format(Instant.ofEpochMilli(jar.lastModified()).atZone(ZoneId.systemDefault()))
                    + " (jar file timestamp)";
        } catch (Exception exception) {
            return "unknown";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oldraid.admin") || args.length != 1) {
            return new ArrayList<>();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(partial)).collect(Collectors.toList());
    }
}
