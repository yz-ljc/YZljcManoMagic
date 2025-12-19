package top.yzljc.manosaba.magic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 夏目安安的洗脑魔法
 */
public class MagicOfAnan implements Listener {

    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> silencedPlayers = new ConcurrentHashMap<>();

    private static final String SKILL_SILENCE = "给吾辈闭嘴";
    private static final String SKILL_FREEZE = "吾辈让你不许动";
    private static final int SKILL_UNLOCK_1 = 3;
    private static final int SKILL_UNLOCK_2 = 40;

    private final double getRadius(Player user) {
        return Math.max(3.0, ManoPrograss.getManoProgressPercentage(user) * 0.2);
    }

    /**
     * 技能：Shift+右键玩家禁言
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_anan".equals(magicId)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!player.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress <= SKILL_UNLOCK_1) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }

        if (!checkAndApplyCooldown(SKILL_SILENCE, player, progress)) {
            return;
        }

        double durationSeconds = 3.0 + (progress * 0.3);
        long durationMillis = (long) (durationSeconds * 1000);
        silencedPlayers.put(target.getUniqueId(), System.currentTimeMillis() + durationMillis);

        // 标题/音效
        target.sendTitle(ChatColor.DARK_AQUA + "【给吾辈闭嘴】", "", 10, (int) (durationSeconds * 20), 10);
        player.sendMessage(ChatColor.DARK_AQUA + target.getName() + "，给吾辈【闭嘴】");
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
    }

    /**
     * 技能：副手F触发范围控制
     * 外部由主类副手激活快捷键/指令主动调用
     */

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_anan".equals(magicId)) return; // 只响应持有自己魔法的玩家

        event.setCancelled(true); // 防止物品真的被交换
        onActive(player);         // 直接调用你写好的副手触发技能
    }

    public void onActive(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress <= SKILL_UNLOCK_2) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }

        if (!checkAndApplyCooldown(SKILL_FREEZE, player, progress)) {
            return;
        }

        double radius = getRadius(player);
        List<Entity> nearbyEntities = player.getNearbyEntities(radius, radius, radius);
        double durationSeconds = Math.min(progress * 0.3, 12.0);
        int durationTicks = (int) (durationSeconds * 20);

        boolean hitAnyone = false;
        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player target && !target.getUniqueId().equals(player.getUniqueId())) {
                hitAnyone = true;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 255));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 100));
                player.getWorld().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.5f);
                target.sendTitle(ChatColor.DARK_AQUA + "【吾辈让你不许动】", "", 10, durationTicks, 10);
            }
        }

        if (hitAnyone) {
            player.sendMessage(ChatColor.DARK_AQUA + "都给吾辈【闭嘴】");
        } else {
            player.sendMessage(ChatColor.GRAY + "周围好像并没有人...");
        }
    }

    /**
     * 聊天禁言效果
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        if (silencedPlayers.containsKey(uid)) {
            long expiry = silencedPlayers.get(uid);
            if (System.currentTimeMillis() < expiry) {
                event.setCancelled(true);
            } else {
                silencedPlayers.remove(uid);
            }
        }
    }

    // 公共：CD管理
    private boolean checkAndApplyCooldown(String key, Player player, int progress) {
        int cooldownSeconds = MagicCooldownTime.calculateCooldownTime(progress);
        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();
        if (internalCooldowns.containsKey(key)) {
            long lastUse = internalCooldowns.get(key);
            if (now - lastUse < cooldownMillis) {
                double remain = (cooldownMillis - (now - lastUse)) / 1000.0;
                player.sendMessage(ChatColor.RED + String.format("[%s] 冷却中: %.1fs", key, remain));
                return false;
            }
        }
        internalCooldowns.put(key, now);
        return true;
    }

    public ItemStack getIcon(Player user) {
        return new ItemStack(Material.ECHO_SHARD);
    }
}