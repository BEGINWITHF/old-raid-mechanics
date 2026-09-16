package com.example.oldraids;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Event glue. All decisions live in {@link RaidService}; this class only forwards Bukkit events.
 */
public final class RaidListeners implements Listener {

    private final OldRaidMechanicsPlugin plugin;

    public RaidListeners(OldRaidMechanicsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Raider raider) {
            plugin.service().rememberDamager(raider, event.getDamager());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Raider raider) {
            plugin.service().forget(raider);
            plugin.service().handleCaptainDeath(raider, event);
        }
    }

    /**
     * Vanilla 1.21.2+ converts Bad Omen into Raid Omen (30 seconds) when the player enters a village
     * and only starts the raid when that countdown runs out. The conversion itself is exactly what
     * should be kept - it carries the level over to the raid - so this only shortens the duration:
     * the countdown is spent in the background and the raid starts right away.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED) {
            return;
        }
        PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null || !PotionEffectType.RAID_OMEN.equals(newEffect.getType())) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PluginConfig cfg = plugin.config();
        if (!cfg.enabled || !cfg.badOmenTriggersRaid) {
            return;
        }
        // Already short (our own rewrite) - leave it alone.
        if (newEffect.getDuration() <= cfg.raidOmenTicks) {
            return;
        }
        int amplifier = newEffect.getAmplifier();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.service().shortenRaidOmen(player, amplifier));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRaidSpawnWave(RaidSpawnWaveEvent event) {
        plugin.service().moveWaveToOldSpawnPosition(event);
    }
}
