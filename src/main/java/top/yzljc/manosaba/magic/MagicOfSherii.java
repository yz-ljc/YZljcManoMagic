package top.yzljc.manosaba.magic;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 橘雪莉的怪力魔法 (重构版)
 * @Author YZ_Ljc_
 */
public class MagicOfSherii implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeBuffs = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();

    // 技能常量
    private static final String SKILL_KNOCKBACK = "大力士侦探";
    private static final String SKILL_PHASE = "大调查";
    private static final String SKILL_MACE_SMASH = "大力音波";

    private static final String BUFF_KNOCKBACK = "BUFF_KB";
    private static final String BUFF_MACE = "BUFF_MACE";

    // 解锁阈值
    private static final int PROGRESS_LEVEL_1 = 5;
    private static final int PROGRESS_LEVEL_2 = 40;
    private static final int PROGRESS_LEVEL_3 = 75;

    public MagicOfSherii(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * 监听 F 键 (副手交换)
     * 修复: 使用 PlayerSwapHandItemsEvent 替代错误的 PlayerSwapHandEvent
     */
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        // 取消原版交换物品的行为
        event.setCancelled(true);

        Player player = event.getPlayer();
        activateMagic(player);
    }

    /**
     * 监听右键交互 (Shift + 右键)
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_sherii".equals(magicId)) return;
        Action action = event.getAction();

        if (!player.isSneaking()) return;

        // Shift + Right Click Logic (Phase/Teleport)
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handlePhaseSkill(player);
        }
    }

    /**
     * 监听攻击事件 (消耗Buff)
     */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        UUID uid = attacker.getUniqueId();
        String magicId = ManoPrograss.getPlayerMagicId(attacker);
        if (!"magic_sherii".equals(magicId)) return;
        if (!activeBuffs.containsKey(uid)) return;

        String buffType = activeBuffs.remove(uid); // 消耗 Buff
        int progress = ManoPrograss.getManoProgressPercentage(attacker);

        if (BUFF_KNOCKBACK.equals(buffType)) {
            applyKnockbackSkill(attacker, victim, progress);
        } else if (BUFF_MACE.equals(buffType)) {
            applyMaceSkill(attacker, victim, progress);
        }
    }

    // --- 核心逻辑 ---

    private void activateMagic(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_sherii".equals(magicId)) return;
        if (player.isSneaking()) {
            // Shift + F -> 大力音波
            if (progress <= PROGRESS_LEVEL_3) {
                player.sendMessage(color("&7你的魔女化程度不足以发动此效果! (需要 " + PROGRESS_LEVEL_3 + "%)"));
                return;
            }

            if (!checkAndApplyCooldown(SKILL_MACE_SMASH, player, progress)) return;

            activeBuffs.put(player.getUniqueId(), BUFF_MACE);
            scheduleBuffExpiration(player, BUFF_MACE);

            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f);
            player.sendMessage(color("&b身为名侦探也需要有强健的体魄哦！(下次攻击触发音波)"));

        } else {
            // F -> 大力士侦探
            if (progress <= PROGRESS_LEVEL_1) {
                player.sendMessage(color("&7你的魔女化程度不足以发动此效果! (需要 " + PROGRESS_LEVEL_1 + "%)"));
                return;
            }

            if (!checkAndApplyCooldown(SKILL_KNOCKBACK, player, progress)) return;

            activeBuffs.put(player.getUniqueId(), BUFF_KNOCKBACK);
            scheduleBuffExpiration(player, BUFF_KNOCKBACK);

            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.5f);
            player.sendMessage(color("&b雪莉酱卡哇伊daisiki！(下次攻击触发击退)"));
        }
    }

    private void handlePhaseSkill(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_sherii".equals(magicId)) return;
        // 防抖动
        if (now - interactDebounce.getOrDefault(uid, 0L) < 200) return;
        interactDebounce.put(uid, now);

        int progress = ManoPrograss.getManoProgressPercentage(player);
        if (progress <= PROGRESS_LEVEL_2) {
            player.sendMessage(color("&7你的魔女化程度不足以发动此效果！(需要 " + PROGRESS_LEVEL_2 + "%)"));
            return;
        }

        Location currentLoc = player.getLocation();
        Vector direction = currentLoc.getDirection().setY(0).normalize();
        Location targetLoc = currentLoc.clone().add(direction.multiply(3));
        targetLoc.setYaw(currentLoc.getYaw());
        targetLoc.setPitch(currentLoc.getPitch());

        Material feetType = targetLoc.getBlock().getType();
        Material headType = targetLoc.clone().add(0, 1, 0).getBlock().getType();

        if (feetType.isSolid() || headType.isSolid()) {
            player.sendMessage(color("&c前方空间不足，大调查失败！"));
            return;
        }

        if (!checkAndApplyCooldown(SKILL_PHASE, player, progress)) {
            return;
        }

        player.teleport(targetLoc);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.getWorld().spawnParticle(Particle.PORTAL, currentLoc, 10);
        player.getWorld().spawnParticle(Particle.PORTAL, targetLoc, 10);

        player.sendMessage(color("&b看来我需要大调查一下你了..."));
    }

    private void scheduleBuffExpiration(Player player, String buffType) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (buffType.equals(activeBuffs.get(player.getUniqueId()))) {
                    activeBuffs.remove(player.getUniqueId());
                    player.sendMessage(color("&7保持姿势太累了... (技能超时失效)"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                }
            }
        }.runTaskLater(plugin, 30 * 20L); // 30秒
    }

    private void applyKnockbackSkill(Player attacker, LivingEntity victim, int progress) {
        double kbStrength = 2.0 + (progress * 0.08);

        Vector direction = attacker.getLocation().getDirection().normalize();
        victim.setVelocity(direction.multiply(kbStrength).setY(0.5));

        double durationSeconds = progress * 0.5;
        if (progress > 20) durationSeconds = 10.0;

        int durationTicks = (int) (durationSeconds * 20);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationTicks, 30));

        attacker.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2f);
        attacker.sendMessage(color("&b不小心搞砸了捏，欸嘿 ~★！"));
    }

    private void applyMaceSkill(Player attacker, LivingEntity victim, int progress) {
        double simulatedHeight = 5.0 + (progress * 0.06);
        double extraDamage = simulatedHeight * 1.4;

        victim.damage(extraDamage, attacker);

        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
        victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, victim.getLocation(), 1);
        victim.setVelocity(new Vector(0, 0.8, 0));

        attacker.sendMessage(color("&b身为名侦探也需要有强健的体魄哦！"));
    }

    private boolean checkAndApplyCooldown(String key, Player player, int progress) {
        // 调用 MagicCooldownTime 进行计算
        int cooldownSeconds = MagicCooldownTime.calculateCooldownTime(progress);

        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();

        // 确保每个玩家独立计算技能CD
        String uniqueKey = player.getUniqueId() + "_" + key;

        if (internalCooldowns.containsKey(uniqueKey)) {
            long lastUse = internalCooldowns.get(uniqueKey);
            if (now - lastUse < cooldownMillis) {
                double remain = (cooldownMillis - (now - lastUse)) / 1000.0;
                player.sendMessage(color(String.format("&c[%s] 冷却中: %.1fs", key, remain)));
                return false;
            }
        }

        internalCooldowns.put(uniqueKey, now);
        return true;
    }
}