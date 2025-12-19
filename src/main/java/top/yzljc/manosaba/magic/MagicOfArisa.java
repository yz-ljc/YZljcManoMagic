package top.yzljc.manosaba.magic;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MagicOfArisa implements Listener {

    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeBuffs = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();

    private static final String SKILL_IGNITE_GROUND = "焦土政策";
    private static final String SKILL_IGNITE_ATTACK = "引火烧身";
    private static final String SKILL_FIREBALL_BURST = "全弹发射";
    private static final String BUFF_IGNITE_NEXT = "BUFF_IGNITE";

    private final Map<UUID, Location> fireballOrigins = new ConcurrentHashMap<>();
    private final Map<UUID, Double> fireballMaxDist = new ConcurrentHashMap<>();

    private static final int SKILL_UNLOCK_1 = 5;
    private static final int SKILL_UNLOCK_2 = 40;
    private static final int SKILL_UNLOCK_3 = 75;

    private final JavaPlugin plugin;

    public MagicOfArisa(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 焦土 (Shift+右键方块)
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_arisa".equals(magicId)) return;
        if (!player.isSneaking()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - interactDebounce.getOrDefault(uid, 0L) < 200) return;
        interactDebounce.put(uid, now);

        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress < SKILL_UNLOCK_3) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        if (!checkAndApplyCooldown(SKILL_IGNITE_GROUND, player, progress)) return;

        double radius = getRadius(player);
        Location center = clickedBlock.getLocation().add(0.5, 1, 0.5);
        long durationMillis = (long) (progress * 0.3 * 1000L);

        player.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        player.sendMessage(ChatColor.GOLD + "让这一切燃烧吧！");

        new BukkitRunnable() {
            long elapsed = 0;
            final long period = 5;

            @Override
            public void run() {
                elapsed += period * 50;
                if (elapsed >= durationMillis) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < radius * 5; i++) {
                    double r = radius * Math.sqrt(ThreadLocalRandom.current().nextDouble());
                    double theta = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                    double x = center.getX() + r * Math.cos(theta);
                    double z = center.getZ() + r * Math.sin(theta);
                    Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);
                    center.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0.1, 0, 0.01);
                    if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                        center.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0.2, 0, 0.01);
                    }
                }
                Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, 2, radius);
                for (Entity e : nearby) {
                    if (e instanceof LivingEntity victim) {
                        if (victim.getUniqueId().equals(player.getUniqueId())) continue;
                        if (victim.getLocation().distanceSquared(center) > radius * radius) continue;
                        if (victim.getHealth() <= 3.0) continue;
                        victim.setFireTicks(20);

                        double damage = 1.0;
                        if (victim.getHealth() - damage > 0) {
                            victim.setHealth(Math.max(0, victim.getHealth() - damage));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * 副手F技能触发
     */
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_arisa".equals(magicId)) return;
        event.setCancelled(true);
        onActive(player);
    }

    public void onActive(Player player) {
        int progress = ManoPrograss.getManoProgressPercentage(player);

        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_arisa".equals(magicId)) return;
        if (player.isSneaking()) {
            // Shift + F: 全弹发射
            if (progress <= SKILL_UNLOCK_1) {
                player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
                return;
            }
            if (!checkAndApplyCooldown(SKILL_FIREBALL_BURST, player, progress)) return;
            executeFireballBurst(player, progress);
            return; // ★ 关键修正：加return杜绝“再执行普通F技能”
        }
        // F: 引火烧身
        if (progress <= SKILL_UNLOCK_2) {
            player.sendMessage(ChatColor.GRAY + "你的魔女化程度不足以发动此效果！");
            return;
        }
        if (!checkAndApplyCooldown(SKILL_IGNITE_ATTACK, player, progress)) return;

        activeBuffs.put(player.getUniqueId(), BUFF_IGNITE_NEXT);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (BUFF_IGNITE_NEXT.equals(activeBuffs.get(player.getUniqueId()))) {
                    activeBuffs.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.GRAY + "火焰熄灭了... (技能超时失效)");
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                }
            }
        }.runTaskLater(plugin, 30 * 20L);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1.2f);
        player.sendMessage(ChatColor.GOLD + "被烧一下可是很疼的，都给我起开，不信你去问雪莉！");
    }

    // 火球弹幕
    private void executeFireballBurst(Player player, int progress) {
        int fireballCount = Math.min(45, Math.max(5, progress - 45));
        double maxDistance = getRadius(player);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        player.sendMessage(ChatColor.GOLD + "大家一起去死吧...");

        double phi = Math.PI * (3. - Math.sqrt(5.));
        for (int i = 0; i < fireballCount; i++) {
            double y = 1 - (i / (double) (fireballCount - 1)) * 2;
            double radius = Math.sqrt(1 - y * y);
            double theta = phi * i;
            double x = Math.cos(theta) * radius;
            double z = Math.sin(theta) * radius;
            Vector dir = new Vector(x, y, z).normalize().multiply(1.5);

            SmallFireball fireball = player.launchProjectile(SmallFireball.class, dir);
            fireball.setShooter(player);
            fireball.setIsIncendiary(false);

            fireballOrigins.put(fireball.getUniqueId(), player.getEyeLocation());
            fireballMaxDist.put(fireball.getUniqueId(), maxDistance);
        }
        startFireballMonitor();
    }

    private void startFireballMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fireballOrigins.isEmpty()) {
                    this.cancel();
                    return;
                }
                Iterator<Map.Entry<UUID, Location>> it = fireballOrigins.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Location> entry = it.next();
                    UUID uuid = entry.getKey();
                    Location origin = entry.getValue();
                    Double maxDist = fireballMaxDist.get(uuid);

                    Entity entity = origin.getWorld().getEntity(uuid);
                    if (entity == null || !entity.isValid() || entity.isDead()) {
                        it.remove();
                        fireballMaxDist.remove(uuid);
                        continue;
                    }
                    if (entity.getLocation().distance(origin) > maxDist) {
                        entity.remove();
                        it.remove();
                        fireballMaxDist.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (event.getDamager() instanceof Player attacker) {
            if (activeBuffs.containsKey(attacker.getUniqueId()) && BUFF_IGNITE_NEXT.equals(activeBuffs.get(attacker.getUniqueId()))) {
                activeBuffs.remove(attacker.getUniqueId());

                int progress = ManoPrograss.getManoProgressPercentage(attacker);
                if (victim.getHealth() <= 3.0 && progress < SKILL_UNLOCK_3) {
                    attacker.sendMessage(ChatColor.GRAY + "算了吧，他快不行了，你的善良使你被迫放弃了这个做法...");
                } else {
                    double durationSeconds = progress * 0.23;
                    int durationTicks = (int) (durationSeconds * 20);

                    victim.setFireTicks(durationTicks);
                    attacker.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 1f);
                    attacker.sendMessage(ChatColor.GOLD + "感受灼烧吧！");
                }
            }
        } else if (event.getDamager() instanceof SmallFireball fireball) {
            if (fireball.getShooter() instanceof Player shooter) {
                if (fireballOrigins.containsKey(fireball.getUniqueId())) {
                    event.setDamage(2.0);
                    fireballOrigins.remove(fireball.getUniqueId());
                    fireballMaxDist.remove(fireball.getUniqueId());
                }
            }
        }
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

    public double getRadius(Player user) {
        return Math.max(3.0, ManoPrograss.getManoProgressPercentage(user) * 0.2);
    }
}