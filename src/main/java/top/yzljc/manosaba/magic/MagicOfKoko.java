package top.yzljc.manosaba.magic;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import top.yzljc.manosaba.utils.CraftPlayerAPI;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 泽渡可可的千里眼魔法 (重构版)
 * @Author YZ_Ljc_
 * @Package top.yzljc.manosaba.magic
 */
public class MagicOfKoko implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> possessingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Entity> sessionTargets = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerBodyMap = new ConcurrentHashMap<>();

    private static final String SKILL_PEEK = "全知之眼";
    private static final String SKILL_POSSESSION = "幽灵附身";
    private static final int SKILL_UNLOCK_1 = 5;
    private static final int SKILL_UNLOCK_2 = 40;
    private static final int SKILL_UNLOCK_3 = 75;

    public MagicOfKoko(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_koko".equals(magicId)) return;
        onActive(player);
    }

    private void onActive(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (player.isSneaking()) {
            handleAdvancedSkill(player, progress);
        } else {
            handleBasicSkill(player, progress);
        }
    }

    private void handleBasicSkill(Player player, int progress) {
        if (progress <= SKILL_UNLOCK_1) {
            player.sendMessage(color("&7你的魔女化程度不足以发动此效果！"));
            return;
        }

        if (!checkAndApplyCooldown(SKILL_PEEK, player, progress, true)) {
            return;
        }

        int targetCount = calculateTargetCount(progress);
        List<Player> validTargets = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());

        if (validTargets.isEmpty()) {
            player.sendMessage(color("&c没有人正在注视着你..."));
            return;
        }

        Collections.shuffle(validTargets);
        List<Player> selectedTargets = validTargets.stream().limit(targetCount).toList();

        player.sendMessage(color("&4在座的各位，让我看看有没有人在看我直播呢..."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);

        for (Player target : selectedTargets) {
            String locStr = String.format("&e%s &7-> &aX:%d Y:%d Z:%d &7(%s)",
                    target.getName(),
                    target.getLocation().getBlockX(),
                    target.getLocation().getBlockY(),
                    target.getLocation().getBlockZ(),
                    target.getWorld().getName()
            );
            player.sendMessage(color(locStr));
        }
    }

    private void handleAdvancedSkill(Player player, int progress) {
        if (progress <= SKILL_UNLOCK_3) {
            player.sendMessage(color("&7你的魔女化程度不足以发动此效果！"));
            return;
        }

        if (possessingPlayers.contains(player.getUniqueId())) {
            return;
        }

        if (!checkAndApplyCooldown(SKILL_POSSESSION, player, progress, false)) {
            return;
        }

        double radius = 5 + progress;
        double durationCalc = 3 + (progress * 0.4);
        double durationSeconds = Math.min(durationCalc, 25.0);
        long durationTicks = (long) (durationSeconds * 20);

        List<Player> nearbyPlayers = player.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())))
                .toList();

        if (nearbyPlayers.isEmpty()) {
            player.sendMessage(color("&c没有人正在注视着你..."));
            return;
        }

        Player target = nearbyPlayers.get(0);

        Location originalLocation = player.getLocation();
        GameMode originalGameMode = player.getGameMode();

        player.sendMessage(color("&4你走进了他人的视线..."));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);

        possessingPlayers.add(player.getUniqueId());
        sessionTargets.put(player.getUniqueId(), target);

        // 使用您指定的 Mannequin 实体
        Mannequin body = originalLocation.getWorld().spawn(originalLocation, Mannequin.class);
        setupBody(body, player);
        playerBodyMap.put(player.getUniqueId(), body.getUniqueId());

        player.setSneaking(false);

        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(target);
        player.sendTitle(color("&4正在观察"), color("&f" + target.getName()), 10, 70, 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !possessingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    Entity currentTarget = player.getSpectatorTarget();
                    Entity requiredTarget = sessionTargets.get(player.getUniqueId());

                    if (requiredTarget != null && requiredTarget.isValid() && currentTarget != requiredTarget) {
                        player.setSpectatorTarget(requiredTarget);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    UUID bodyId = playerBodyMap.remove(player.getUniqueId());
                    Entity bodyEntity = bodyId != null ? Bukkit.getEntity(bodyId) : null;

                    boolean bodyIsDead = bodyEntity == null || bodyEntity.isDead();
                    double remainingHealth = 0;
                    Location returnLocation = originalLocation;

                    if (!bodyIsDead && bodyEntity instanceof LivingEntity livingBody) {
                        remainingHealth = livingBody.getHealth();
                        returnLocation = livingBody.getLocation();
                        livingBody.remove();
                    }

                    if (player.isOnline()) {
                        player.setSpectatorTarget(null);
                        player.teleport(returnLocation);
                        player.setGameMode(originalGameMode);

                        if (bodyIsDead) {
                            player.setHealth(0);
                            player.sendMessage(color("&c你的肉体在附身期间被摧毁了！"));
                        } else {
                            double maxHealth = player.getMaxHealth();
                            player.setHealth(Math.min(remainingHealth, maxHealth));

                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 2f);
                            player.sendMessage(color("&4灵魂已回归本体"));
                        }
                    } else {
                        if (!bodyIsDead) {
                            bodyEntity.remove();
                        }
                    }
                    internalCooldowns.put(SKILL_POSSESSION, System.currentTimeMillis());
                } finally {
                    possessingPlayers.remove(player.getUniqueId());
                    sessionTargets.remove(player.getUniqueId());
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private void setupBody(Mannequin body, Player player) {
        body.setAI(false);
        body.setSilent(true);
        body.setCanPickupItems(false);

        // 核心修改：使用 createResolvableProfile 替代旧的 CraftPlayerAPI 调用
        // 这将解决类型不匹配的报错
        body.setProfile(CraftPlayerAPI.getInstance().getResolvableProfile(player));

        body.setCustomNameVisible(false);

        if (body.getEquipment() != null) {
            body.getEquipment().setArmorContents(player.getInventory().getArmorContents());
            body.getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand());
            body.getEquipment().setItemInOffHand(player.getInventory().getItemInOffHand());
        }

        double maxHealth = player.getMaxHealth();
        body.setMaxHealth(maxHealth);
        body.setHealth(player.getHealth());
    }

    /**
     * 手动构建 Paper 1.21+ 的 ResolvableProfile
     * 替代原有的 CraftPlayerAPI.getInstance().getResolvableProfile(player)
     */

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (possessingPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && possessingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private int calculateTargetCount(int progress) {
        if (progress >= 85) return 12;
        if (progress >= 77) return 11;
        if (progress >= 70) return 10;
        if (progress >= 63) return 9;
        if (progress >= 55) return 8;
        if (progress >= 47) return 7;
        if (progress >= 40) return 6;
        if (progress >= 33) return 5;
        if (progress >= 25) return 4;
        if (progress >= 17) return 3;
        if (progress >= 10) return 2;
        return 1;
    }

    private boolean checkAndApplyCooldown(String key, Player player, int progress, boolean apply) {
        int cooldownSeconds = MagicCooldownTime.calculateCooldownTime(progress);
        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();

        String uniqueKey = player.getUniqueId() + "_" + key;

        if (internalCooldowns.containsKey(uniqueKey)) {
            long lastUse = internalCooldowns.get(uniqueKey);
            if (now - lastUse < cooldownMillis) {
                double remain = (cooldownMillis - (now - lastUse)) / 1000.0;
                player.sendMessage(color(String.format("&c[%s] 冷却中: %.1fs", key, remain)));
                return false;
            }
        }

        if (apply) {
            internalCooldowns.put(uniqueKey, now);
        }
        return true;
    }
}