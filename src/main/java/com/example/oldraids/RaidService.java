package com.example.oldraids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The raid logic itself: the raid behaviour of Minecraft 1.20.1 on a modern server.
 *
 * Two pieces:
 *  1. Raid triggering stays on the vanilla code path: vanilla turns Bad Omen into Raid Omen with the
 *     same level (that level is what drives the bonus wave, the hero duration and the gear
 *     enchantments), and this plugin only cuts the 30 second countdown - the "potion is drunk"
 *     effect happens in the background, so RaidOmenMobEffect triggers the raid right away at the
 *     position vanilla saved.
 *  2. A raid captain killed by a player hands out Bad Omen directly (old stacking, 100 minutes).
 *  3. Raid waves are placed with the pre-1.21.2 spawn position algorithm.
 */
public final class RaidService {

    /** 1.20.1 searched the wave position with 20 attempts and a fresh random angle each attempt. */
    private static final int SPAWN_TRIES = 20;
    /** Duration of Bad Omen handed out by a raid captain before 1.21: 120000 ticks = 100 minutes. */
    private static final int BAD_OMEN_DURATION = 120000;
    /** Keep the last-damager cache bounded (raiders can be damaged a lot). */
    private static final int LAST_DAMAGER_LIMIT = 4096;

    private final OldRaidMechanicsPlugin plugin;
    private final Map<UUID, UUID> lastDamager = new HashMap<>();

    public RaidService(OldRaidMechanicsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Simulates the player drinking the potion: vanilla has already converted Bad Omen into Raid Omen
     * with the same level and has saved the position (raid_omen_position), so all that is left is the
     * 30 second countdown. Cutting the effect duration lets vanilla's own RaidOmenMobEffect start the
     * raid on the next tick - the level, the raid centre and Paper's RaidTriggerEvent all stay vanilla.
     */
    public void shortenRaidOmen(Player player, int amplifier) {
        int ticks = Math.max(2, plugin.config().raidOmenTicks);
        player.removePotionEffect(PotionEffectType.RAID_OMEN);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RAID_OMEN, ticks, Math.max(0, amplifier),
                false, false, true));
        if (plugin.config().debug) {
            plugin.getLogger().info("Raid Omen countdown cut to " + ticks + " tick(s) for " + player.getName()
                    + " (level " + (Math.max(0, amplifier) + 1) + ")");
        }
    }

    /** Port of Raider#die: a captain that dies outside of a raid hands his Bad Omen to the killer. */
    public void handleCaptainDeath(Raider raider, EntityDeathEvent event) {
        PluginConfig cfg = plugin.config();
        if (!cfg.enabled || !cfg.captainGivesBadOmen) {
            return;
        }
        if (!raider.isPatrolLeader() || raider.getRaid() != null) {
            return;
        }
        if (plugin.nms().isReady() && plugin.nms().raidAt(raider.getWorld(), raider.getLocation().getBlockX(),
                raider.getLocation().getBlockY(), raider.getLocation().getBlockZ()) != null) {
            return;
        }
        Player player = resolveKiller(raider);
        if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (Boolean.TRUE.equals(raider.getWorld().getGameRuleValue(GameRule.DISABLE_RAIDS))) {
            return;
        }

        int level = 1;
        PotionEffect existing = player.getPotionEffect(PotionEffectType.BAD_OMEN);
        if (existing != null) {
            level += existing.getAmplifier();
            player.removePotionEffect(PotionEffectType.BAD_OMEN);
        } else {
            level--;
        }
        int amplifier = Math.max(0, Math.min(4, level));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BAD_OMEN, BAD_OMEN_DURATION, amplifier,
                false, false, true));
        if (cfg.removeOminousBottleDrop) {
            event.getDrops().removeIf(stack -> stack.getType() == Material.OMINOUS_BOTTLE);
        }
        if (cfg.debug) {
            plugin.getLogger().info("Captain killed by " + player.getName() + " -> Bad Omen "
                    + (amplifier + 1));
        }
    }

    public void rememberDamager(Raider raider, Entity damager) {
        if (lastDamager.size() > LAST_DAMAGER_LIMIT) {
            lastDamager.clear();
        }
        if (damager instanceof Player player) {
            lastDamager.put(raider.getUniqueId(), player.getUniqueId());
        } else if (damager instanceof Wolf wolf && wolf.isTamed() && wolf.getOwner() instanceof Player owner) {
            lastDamager.put(raider.getUniqueId(), owner.getUniqueId());
        }
    }

    public void forget(Raider raider) {
        lastDamager.remove(raider.getUniqueId());
    }

    private Player resolveKiller(Raider raider) {
        Player killer = raider.getKiller();
        if (killer != null) {
            return killer;
        }
        UUID owner = lastDamager.remove(raider.getUniqueId());
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    /** Moves a freshly spawned wave onto the position the pre-1.21.2 algorithm would have picked. */
    public void moveWaveToOldSpawnPosition(RaidSpawnWaveEvent event) {
        PluginConfig cfg = plugin.config();
        if (!cfg.enabled || !cfg.oldSpawnPositions) {
            return;
        }
        Location center = event.getRaid().getLocation();
        if (center == null || center.getWorld() == null) {
            return;
        }
        List<Raider> raiders = new ArrayList<>(event.getRaiders());
        Raider leader = event.getPatrolLeader();
        if (leader != null && !raiders.contains(leader)) {
            raiders.add(leader);
        }
        // Teleporting an entity dismounts its passengers, so move the mounts and re-seat the riding
        // pairs (ravager + pillager) afterwards. Pre-1.21.2 spawned a whole group at one block, and
        // so does this - just with the raiders moved to the block the old search picked.
        List<Raider> movers = new ArrayList<>();
        Map<Raider, List<Entity>> ridingPairs = new HashMap<>();
        for (Raider raider : raiders) {
            if (raider.isInsideVehicle()) {
                continue;
            }
            movers.add(raider);
            if (!raider.getPassengers().isEmpty()) {
                ridingPairs.put(raider, new ArrayList<>(raider.getPassengers()));
            }
        }
        if (movers.isEmpty()) {
            return;
        }
        Location target = findOldSpawnLocation(center.getWorld(), center, cfg.radiusFactor);
        if (target == null) {
            if (cfg.debug) {
                plugin.getLogger().info("Old spawn search found no position for the raid at " + center);
            }
            return;
        }
        Runnable move = () -> {
            for (Raider mover : movers) {
                if (mover.isValid()) {
                    mover.teleport(target);
                }
            }
            for (Map.Entry<Raider, List<Entity>> pair : ridingPairs.entrySet()) {
                Raider vehicle = pair.getKey();
                if (!vehicle.isValid()) {
                    continue;
                }
                for (Entity passenger : pair.getValue()) {
                    if (passenger.isValid()) {
                        vehicle.addPassenger(passenger);
                    }
                }
            }
        };
        if (cfg.teleportDelayTicks <= 0) {
            move.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, move, cfg.teleportDelayTicks);
        }
        if (cfg.debug) {
            plugin.getLogger().info("Moved " + movers.size() + " raiders (of " + raiders.size()
                    + " in the wave) to " + target.getBlockX() + " " + target.getBlockY() + " "
                    + target.getBlockZ());
        }
    }

    /**
     * Port of Raid#getRavagerSpawnLocation / findRandomSpawnPos from Minecraft 1.21.1 (the version
     * the old raid farms were built for).
     *
     * Before 1.21.2 the search walked three ranges in order: radius 32*2, then 32*1, then the small
     * area around the raid centre (the jitter of up to 4 blocks), each with 20 attempts and a fresh
     * random angle per attempt. Only the last range was allowed to sit inside the village
     * (the vanilla test was "not in a village OR proximity >= 2", and proximity 2 means the centre
     * range). 1.21.2 dropped that ladder and made the range shrink with the wave countdown instead.
     *
     * @param startFactor highest range tried: 2 = up to ~64 blocks, 1 = ~32, 0 = centre only
     */
    public Location findOldSpawnLocation(World world, Location center, int startFactor) {
        for (int factor = Math.max(0, Math.min(2, startFactor)); factor >= 0; factor--) {
            Location found = tryOldSpawnRange(world, center, factor);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** One range of the old ladder: {@code factor * 32} blocks around the centre, 20 attempts. */
    private Location tryOldSpawnRange(World world, Location center, int factor) {
        Nms nms = plugin.nms();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        boolean villageAllowed = factor <= 0;
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2.0);
            int x = centerX + (int) Math.floor(Math.cos(angle) * 32.0 * factor + random.nextInt(5));
            int z = centerZ + (int) Math.floor(Math.sin(angle) * 32.0 * factor + random.nextInt(5));
            // Level#getHeight(WORLD_SURFACE, x, z) is one block above Bukkit's highest block.
            int y = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE).getY() + 1;
            if (!chunksLoaded(world, x, z)) {
                continue;
            }
            if (!isRavagerSpawnPositionOk(world, x, y, z)) {
                continue;
            }
            if (!villageAllowed && nms.isReady() && nms.isVillage(world, x, y, z)) {
                continue;
            }
            if (plugin.config().debug) {
                plugin.getLogger().info("Old spawn range factor " + factor + " hit after " + (attempt + 1)
                        + " attempt(s) -> " + x + " " + y + " " + z);
            }
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /** Vanilla requires the 21x21 block box around the position to be loaded and ticking. */
    private boolean chunksLoaded(World world, int x, int z) {
        int minChunkX = (x - 10) >> 4;
        int maxChunkX = (x + 10) >> 4;
        int minChunkZ = (z - 10) >> 4;
        int maxChunkZ = (z + 10) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Approximation of the ON_GROUND spawn placement check used for ravagers. */
    private boolean isRavagerSpawnPositionOk(World world, int x, int y, int z) {
        Block at = world.getBlockAt(x, y, z);
        Block below = world.getBlockAt(x, y - 1, z);
        if (below.getType() == Material.SNOW && at.getType() == Material.AIR) {
            return true;
        }
        return below.getType().isSolid() && at.isPassable();
    }
}
