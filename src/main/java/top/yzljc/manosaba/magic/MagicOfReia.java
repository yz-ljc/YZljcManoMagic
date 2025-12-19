package top.yzljc.manosaba.magic;

import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MagicOfReia implements Listener {

    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();

    private static final String SKILL_DARKNESS = "视线固定";
    private static final int SKILL_UNLOCK_1 = 5;

    // 只保留右键玩家黑暗的主干实现
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_reia".equals(magicId)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!player.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);
        if (progress <= SKILL_UNLOCK_1) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }
        if (!checkAndApplyCooldown(SKILL_DARKNESS, player, progress)) return;

        double durationSeconds = Math.min(12.0, progress * 0.3);
        int durationTicks = (int) (durationSeconds * 20);

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 255));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 100));
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation(), 15, 0.5, 1, 0.5, 0.1);

        player.sendMessage(ChatColor.YELLOW + "你固定了一名玩家的视线...");
    }

    // 公共冷却逻辑
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
}