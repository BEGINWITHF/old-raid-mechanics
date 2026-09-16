package com.example.oldraids;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reflection bridge to the Mojang-mapped server classes.
 *
 * Paper runs its server jar with official (Mojang) names, so these two read-only helpers can be
 * reached without compiling against NMS: the village test used by the old spawn search and the raid
 * lookup used by the captain rule. Everything else stays on the public API, and raid triggering is
 * left to vanilla (see {@link RaidService#shortenRaidOmen}).
 *
 * Version sensitive: if a future Paper build renames a member, {@link #init} fails and the plugin
 * reports it in /oldraid status instead of crashing.
 */
public final class Nms {

    private JavaPlugin plugin;

    private Method craftWorldGetHandle;
    private Method levelIsVillage;
    private Method levelGetRaidAt;
    private Constructor<?> blockPosConstructor;

    /** Non-null when the initial lookup of the server members failed (the only permanent failure). */
    private String error;
    /** Init succeeded; a failed call later on does not flip this back. */
    private boolean ready;
    /** Last distinct call failure, so a broken call does not spam the log every tick. */
    private String lastCallError;

    public boolean init(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");

            this.craftWorldGetHandle = craftWorld.getMethod("getHandle");
            this.levelIsVillage = serverLevel.getMethod("isVillage", blockPos);
            this.levelGetRaidAt = serverLevel.getMethod("getRaidAt", blockPos);
            this.blockPosConstructor = blockPos.getConstructor(int.class, int.class, int.class);

            this.error = null;
            this.ready = true;
            return true;
        } catch (Throwable throwable) {
            this.error = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            this.ready = false;
            plugin.getLogger().log(Level.SEVERE, "NMS bridge unavailable - " + error);
            return false;
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String error() {
        return error == null ? "" : error;
    }

    private Object newBlockPos(int x, int y, int z) {
        try {
            return blockPosConstructor.newInstance(x, y, z);
        } catch (Throwable throwable) {
            fail("BlockPos(int,int,int)", throwable);
            return null;
        }
    }

    /** ServerLevel of a Bukkit world. */
    private Object level(World world) {
        return invoke(craftWorldGetHandle, world);
    }

    /** ServerLevel#isVillage(BlockPos). */
    public boolean isVillage(World world, int x, int y, int z) {
        if (!isReady()) {
            return false;
        }
        Object serverLevel = level(world);
        Object pos = newBlockPos(x, y, z);
        if (serverLevel == null || pos == null) {
            return false;
        }
        Object result = invoke(levelIsVillage, serverLevel, pos);
        return result instanceof Boolean && (Boolean) result;
    }

    /** ServerLevel#getRaidAt(BlockPos) - the raid covering that position, or null. */
    public Object raidAt(World world, int x, int y, int z) {
        if (!isReady()) {
            return null;
        }
        Object serverLevel = level(world);
        Object pos = newBlockPos(x, y, z);
        if (serverLevel == null || pos == null) {
            return null;
        }
        return invoke(levelGetRaidAt, serverLevel, pos);
    }

    private Object invoke(Method method, Object target, Object... args) {
        if (!isReady() || method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable throwable) {
            fail(method.getName(), throwable);
            return null;
        }
    }

    private void fail(String what, Throwable throwable) {
        String message = what + " -> " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        if (message.equals(lastCallError)) {
            return;
        }
        lastCallError = message;
        if (plugin != null) {
            plugin.getLogger().log(Level.WARNING, "NMS call failed: " + message);
        }
    }
}
