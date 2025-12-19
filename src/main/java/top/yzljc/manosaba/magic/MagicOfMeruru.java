package top.yzljc.manosaba.magic;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MagicOfMeruru implements Listener {

    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> potionRestockTimer = new ConcurrentHashMap<>();

    private static final String SKILL_SELF_HEAL = "自愈";
    private static final String SKILL_TARGET_HEAL = "治愈他人";
    private static final String SKILL_TIMID_ANGEL = "胆怯的天使";
    private static final long RESTOCK_DELAY_MS = 90 * 1000L;
    private static final int SKILL_UNLOCK_1 = 5;
    private static final int SKILL_UNLOCK_2 = 20;
    private static final int SKILL_UNLOCK_GROUP = 40;
    private static final int SKILL_UNLOCK_3 = 75;

    private final JavaPlugin plugin;

    public MagicOfMeruru(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // 被动药水给药（tick中调用）
    public void tick(Player player) {
        ItemStack potion = createBetaPotion();
        boolean hasPotion = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(potion)) {
                hasPotion = true;
                break;
            }
        }
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (hasPotion) {
            potionRestockTimer.remove(uid);
        } else {
            if (!potionRestockTimer.containsKey(uid)) {
                potionRestockTimer.put(uid, now);
            } else {
                long lostTime = potionRestockTimer.get(uid);
                if (now - lostTime >= RESTOCK_DELAY_MS) {
                    givePassivePotion(player, potion);
                    potionRestockTimer.remove(uid);
                }
            }
        }
    }

    private ItemStack createBetaPotion() {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.HARMING);
            meta.setDisplayName(ChatColor.GRAY + "Β");
            meta.setLore(List.of(ChatColor.DARK_RED + "魔女杀手...?"));
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private void givePassivePotion(Player player, ItemStack potion) {
        HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(potion);
        if (!leftOver.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), potion);
        }
        player.sendMessage(ChatColor.GRAY + "你获得了一瓶药水 'Β'，它可能是安眠药？");
    }

    // Shift + 右键玩家：单/群体治愈
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_meruru".equals(magicId)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!player.isSneaking()) return;
        Entity target = event.getRightClicked();
        if (!(target instanceof Player targetPlayer)) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);
        if (progress < SKILL_UNLOCK_2) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }
        if (!checkAndApplyCooldown(SKILL_TARGET_HEAL, player, progress)) return;

        if (progress >= SKILL_UNLOCK_GROUP) {
            double radius = progress * 0.2;
            int durationTicks = (int) (progress * 0.4 * 20);

            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player p) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 1));
                    spawnHealParticles(p);
                }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 1));
            spawnHealParticles(player);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "大家的伤口...由我来治愈！");
        } else {
            int durationTicks = getDurationByProgress(progress);
            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0));
            spawnHealParticles(targetPlayer);
            player.sendMessage(ChatColor.LIGHT_PURPLE + targetPlayer.getName() + "小姐，不要怕呢...");
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_meruru".equals(magicId)) return;
        event.setCancelled(true); // 阻止 F 键交换物品的原生行为
        onActive(player);         // 调用你的副手技能逻辑，上面会自行判断是否蹲下
    }

    public void onActive(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (player.isSneaking()) {
            if (progress <= SKILL_UNLOCK_3) {
                player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
                return;
            }
            if (!checkAndApplyCooldown(SKILL_TIMID_ANGEL, player, progress)) return;

            double radius = progress * 0.2;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player p) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 10));
                    spawnHealParticles(p);
                }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 10));
            spawnHealParticles(player);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "为了见到大魔女大人...我会守护大家的！");
        } else {
            if (progress <= SKILL_UNLOCK_1) {
                player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
                return;
            }
            if (!checkAndApplyCooldown(SKILL_SELF_HEAL, player, progress)) return;

            int durationTicks = getDurationByProgress(progress);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0));
            spawnHealParticles(player);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "伤口正在愈合...");
        }
    }

    private int getDurationByProgress(int progress) {
        if (progress > 10) {
            return (int) (progress * 0.6 * 20);
        } else if (progress >= 7) {
            return 5 * 20;
        } else if (progress >= 3) {
            return 4 * 20;
        } else {
            return 3 * 20;
        }
    }

    private void spawnHealParticles(Player p) {
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5);
    }

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

    // 菜单展示等
    public double getRadius(Player user) {
        return ManoPrograss.getManoProgressPercentage(user) * 0.2;
    }
    public ItemStack getIcon(Player user) {
        return new ItemStack(Material.GLISTERING_MELON_SLICE);
    }
}