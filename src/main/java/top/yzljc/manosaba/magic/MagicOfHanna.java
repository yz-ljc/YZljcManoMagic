package top.yzljc.manosaba.magic;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MagicOfHanna implements Listener {

    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeFlightTasks = new ConcurrentHashMap<>();

    private boolean isEjecting = false;

    private static final String SKILL_FLOAT = "失重漂浮";
    private static final String SKILL_CARRY = "轻盈加护";
    private static final String SKILL_AOE = "重力反转";
    private static final String SKILL_FLY = "自由之翼";
    // 进度解锁
    private static final int SKILL_UNLOCK_1 = 3;
    private static final int SKILL_UNLOCK_2 = 18;
    private static final int SKILL_UNLOCK_3 = 49;
    private static final int SKILL_UNLOCK_4 = 75;

    private final JavaPlugin plugin;

    public MagicOfHanna(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // 持续检测飞行条件（必须在 tick 调用）
    public void tick(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress > SKILL_UNLOCK_4) {
            if (!isCooldown(SKILL_FLY, player) && !activeFlightTasks.containsKey(player.getUniqueId()) && !player.getAllowFlight() && player.getGameMode() == GameMode.SURVIVAL) {
                if (!player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir()) {
                    player.setAllowFlight(true);
                }
            }
        }
    }

    /**
     * 携带玩家 (Shift + 右键玩家)
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_hanna".equals(magicId)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!player.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player targetPlayer)) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress <= SKILL_UNLOCK_2) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果 (需 >20%)");
            return;
        }
        if (!checkAndApplyCooldown(SKILL_CARRY, player, progress)) return;

        player.addPassenger(targetPlayer);

        double durationSeconds = 4.0 + (progress * 0.13);
        int durationTicks = (int) (durationSeconds * 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
        player.sendMessage(ChatColor.GREEN + "抓紧了哦，本大小姐可要起飞了！");

        // 时间结束自动放下 + 安全落地
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    isEjecting = true;
                    player.eject();
                    isEjecting = false;
                    startSafeLandingMonitor(player, false, null, null);
                }
                if (targetPlayer.isOnline()) {
                    startSafeLandingMonitor(targetPlayer, false, null, null);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Player p && !isEjecting) {
            event.setCancelled(true);
        }
    }

    // 飞行（双击跳跃）
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!event.isFlying()) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress <= SKILL_UNLOCK_4) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            return;
        }

        if (isCooldown(SKILL_FLY, player)) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            double remain = (MagicCooldownTime.calculateCooldownTime(progress) * 1000L - (System.currentTimeMillis() - internalCooldowns.get(SKILL_FLY))) / 1000.0;
            player.sendMessage(ChatColor.RED + String.format("[%s] 冷却中: %.1fs", SKILL_FLY, remain));
            return;
        }
        if (activeFlightTasks.containsKey(player.getUniqueId())) return;

        double durationSeconds = Double.parseDouble(String.format("%.2f", progress * 0.63));
        long durationTicks = (long) (durationSeconds * 20);

        player.sendMessage(ChatColor.GREEN + "重力...解除了！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanupFlight(player.getUniqueId());
                    this.cancel();
                    return;
                }
                if (ticks++ >= durationTicks) {
                    endFlight(player, progress, true);
                    this.cancel();
                    return;
                }
                if (ticks > 10) {
                    boolean onGround = !player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir();
                    if (onGround) {
                        endFlight(player, progress, false);
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeFlightTasks.put(player.getUniqueId(), task);
    }

    private void endFlight(Player player, int progress, boolean needSafeLanding) {
        cleanupFlight(player.getUniqueId());
        if (player.isOnline()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        if (needSafeLanding && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "飞...不动了...我要...摔下去了！");
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.5f);
            startSafeLandingMonitor(player, true, SKILL_FLY, progress);
        } else {
            applyCooldownDirectly(SKILL_FLY, progress, player);
        }
    }

    private void cleanupFlight(UUID uuid) {
        BukkitTask task = activeFlightTasks.remove(uuid);
        if (task != null && !task.isCancelled())
            task.cancel();
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_hanna".equals(magicId)) return;
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
            if (!checkAndApplyCooldown(SKILL_AOE, player, progress)) return;

            double durationSeconds = Double.parseDouble(String.format("%.2f", progress * 0.1));
            int durationTicks = (int) (durationSeconds * 20);
            double radius = getRadius(player);

            player.sendMessage(ChatColor.GREEN + "原地拔起...");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 50, radius, 1, radius);

            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player target) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, 1));
                }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, 1));

            new BukkitRunnable() {
                @Override public void run() { startSafeLandingMonitor(player, false, null, null); }
            }.runTaskLater(plugin, durationTicks);
        } else {
            // 自身漂浮 (>1%)
            if (progress <= SKILL_UNLOCK_1) {
                player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
                return;
            }
            if (!checkAndApplyCooldown(SKILL_FLOAT, player, progress)) return;

            double durationSeconds = Double.parseDouble(String.format("%.2f", 3.0 + (progress * 0.5)));
            int durationTicks = (int) (durationSeconds * 20);

            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, 0));
            player.sendMessage(ChatColor.GREEN + "飞起来了~ ！");
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SHULKER_BULLET_HIT, 1f, 1f);

            new BukkitRunnable() {
                @Override public void run() { startSafeLandingMonitor(player, false, null, null); }
            }.runTaskLater(plugin, durationTicks);
        }
    }

    // 落地判定浮空监控
    private void startSafeLandingMonitor(Player player, boolean triggerCooldown, String cooldownKey, Object cdAttach) {
        new BukkitRunnable() {
            int ticks = 0;
            final int MAX_WAIT_TICKS = 20 * 30;
            boolean hasGivenSlowFalling = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    this.cancel();
                    return;
                }
                if (ticks++ > MAX_WAIT_TICKS) {
                    if (triggerCooldown && cooldownKey != null && cdAttach != null) {
                        if (cdAttach instanceof Player)
                            applyCooldownDirectly(cooldownKey, ManoPrograss.getManoProgressPercentage((Player) cdAttach), player);
                        if (cdAttach instanceof Integer)
                            applyCooldownDirectly(cooldownKey, (Integer) cdAttach, player);
                    }
                    this.cancel();
                    return;
                }
                boolean isOnGround = !player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir();

                if (isOnGround) {
                    if (triggerCooldown && cooldownKey != null && cdAttach != null) {
                        if (cdAttach instanceof Player)
                            applyCooldownDirectly(cooldownKey, ManoPrograss.getManoProgressPercentage((Player) cdAttach), player);
                        if (cdAttach instanceof Integer)
                            applyCooldownDirectly(cooldownKey, (Integer) cdAttach, player);
                    }
                    this.cancel();
                    return;
                }
                if (!hasGivenSlowFalling && player.getVelocity().getY() < -0.1) {
                    Location loc = player.getLocation();
                    boolean groundNear = false;
                    for (int i = 1; i <= 4; i++) {
                        Block block = loc.clone().subtract(0, i, 0).getBlock();
                        if (block.getType() != Material.AIR && block.getType().isSolid()) {
                            groundNear = true;
                            break;
                        }
                    }
                    if (groundNear) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false));
                        hasGivenSlowFalling = true;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // 冷却管理
    private boolean isCooldown(String key, Player player) {
        return internalCooldowns.containsKey(key) &&
                (System.currentTimeMillis() - internalCooldowns.get(key) < MagicCooldownTime.calculateCooldownTime(ManoPrograss.getManoProgressPercentage(player)) * 1000L);
    }

    private void applyCooldownDirectly(String key, int manoProgress, Player player) {
        internalCooldowns.put(key, System.currentTimeMillis());
    }

    private boolean checkAndApplyCooldown(String key, Player player, int manoProgress) {
        int cooldownSeconds = MagicCooldownTime.calculateCooldownTime(manoProgress);
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

    private double getRadius(Player user) {
        return Math.max(3.0, ManoPrograss.getManoProgressPercentage(user) * 0.2);
    }

}