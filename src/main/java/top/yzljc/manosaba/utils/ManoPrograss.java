package top.yzljc.manosaba.utils;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于管理玩家魔女化程度(Progress)和激活魔法ID的工具类
 */
public class ManoPrograss {

    // 玩家进度
    private static final ConcurrentHashMap<UUID, Integer> progressMap = new ConcurrentHashMap<>();

    // 玩家激活魔法id
    private static final ConcurrentHashMap<UUID, String> magicIdMap = new ConcurrentHashMap<>();

    /**
     * 获取玩家的魔女化程度
     */
    public static int getManoProgressPercentage(Player player) {
        return getManoProgressPercentage(player.getUniqueId());
    }

    public static int getManoProgressPercentage(UUID uuid) {
        return progressMap.getOrDefault(uuid, 0);
    }

    /**
     * 设置玩家的魔女化程度
     */
    public static void setManoProgress(Player player, int amount) {
        setManoProgress(player.getUniqueId(), amount);
    }

    public static void setManoProgress(UUID uuid, int amount) {
        int safeAmount = Math.max(0, Math.min(100, amount));
        progressMap.put(uuid, safeAmount);
    }

    /**
     * 获取玩家当前激活的魔法id（无则返回 null）
     */
    public static String getPlayerMagicId(Player player) {
        return getPlayerMagicId(player.getUniqueId());
    }

    public static String getPlayerMagicId(UUID uuid) {
        return magicIdMap.get(uuid);
    }

    /**
     * 设置玩家当前激活的魔法id
     */
    public static void setPlayerMagicId(Player player, String magicId) {
        setPlayerMagicId(player.getUniqueId(), magicId);
    }

    public static void setPlayerMagicId(UUID uuid, String magicId) {
        if (magicId == null || magicId.isBlank()) {
            magicIdMap.remove(uuid);
        } else {
            magicIdMap.put(uuid, magicId.trim());
        }
    }
}