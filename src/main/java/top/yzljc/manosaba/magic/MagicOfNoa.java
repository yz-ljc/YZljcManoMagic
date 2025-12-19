package top.yzljc.manosaba.magic;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import top.yzljc.manosaba.utils.MagicCooldownTime;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 城崎诺亚的液体操控魔法 (重构版)
 * @Author YZ_Ljc_
 */
public class MagicOfNoa implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, Long> internalCooldowns = new ConcurrentHashMap<>();

    // 技能 Key
    private static final String SKILL_SUMMON = "神笔诺亚";
    private static final String SKILL_WATER = "水流爆炸";
    private static final String SKILL_WEATHER = "液体操纵";
    private static final String SKILL_RED_MIST = "赤红之雾";

    // 解锁阈值
    private static final int SKILL_UNLOCK_1 = 3;
    private static final int SKILL_UNLOCK_2 = 18;
    private static final int SKILL_UNLOCK_3 = 40;
    private static final int SKILL_UNLOCK_4 = 75;

    public MagicOfNoa(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startPassiveEffects(); // 启动常驻被动效果
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * 模拟原本的 tick() 方法
     * 常驻被动：所有拥有此魔法的玩家灭火 + 雨天给予周围生物失明
     */
    private void startPassiveEffects() {
        new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 这里假设所有在线玩家都拥有此魔法 (或者你可以加个权限/List判断)
                    // 如果只希望特定玩家拥有，需要加 if (!hasMagic(player)) return;

                    // 被动 1: 自动灭火
                    if (player.getFireTicks() > 0) {
                        player.setFireTicks(0);
                    }

                    // 被动 2: 雨天光环 (每秒检测一次)
                    if (tickCounter % 20 == 0) {
                        World world = player.getWorld();
                        if (world.hasStorm()) {
                            double radius = getRadius(player);
                            for (Entity target : player.getNearbyEntities(radius, radius, radius)) {
                                if (target instanceof LivingEntity livingTarget && !target.equals(player)) {
                                    Location loc = livingTarget.getLocation();
                                    int highestY = world.getHighestBlockYAt(loc);
                                    // 露天判定
                                    if (loc.getY() >= highestY - 1) {
                                        livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 监听 Shift + 右键方块
     * 技能: 赤红之雾
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_noa".equals(magicId)) return;

        if (!player.isSneaking()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);

        if (progress > SKILL_UNLOCK_1) {
            if (!checkAndApplyCooldown(SKILL_RED_MIST, player, progress)) {
                return;
            }

            Location clickedBlockLoc = event.getClickedBlock().getLocation();
            Location particleLoc = clickedBlockLoc.add(0.5, 1.0, 0.5);

            int durationTicks = (int) ((5 + progress * 0.6) * 20);

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= durationTicks) {
                        this.cancel();
                        return;
                    }
                    // 生成红色粒子雾
                    player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, particleLoc, 500, 1.2, 0.25, 1.2, 0.1, Color.fromRGB(255, 0, 0));
                    ticks += 2;
                }
            }.runTaskTimer(plugin, 0L, 2L);

            player.sendMessage(color("&c鲜&4红&c的&4雾&c气&4弥&c漫&4开&c来&4..."));
        }
    }

    /**
     * 监听 F 键 (副手交换)
     * 技能:
     * 1. Shift+F: 水流爆炸
     * 2. F+抬头: 改变天气
     * 3. F: 召唤生物
     */
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        String magicId = ManoPrograss.getPlayerMagicId(player);
        if (!"magic_noa".equals(magicId)) return;

        int progress = ManoPrograss.getManoProgressPercentage(player);
        Location center = player.getLocation();

        if (player.isSneaking()) {
            // --- Shift + F: 水流爆炸 ---
            if (progress <= SKILL_UNLOCK_4) {
                player.sendMessage(color("&7你的魔女化程度不足以发动此效果! (需要 " + SKILL_UNLOCK_4 + "%)"));
                return;
            }
            if (!checkAndApplyCooldown(SKILL_WATER, player, progress)) {
                return;
            }

            player.sendMessage(color("&4诺&6亚&e不&a小&3心&9又&5操&d纵&c了&6液&e体&a了&b哦&9.&5.&d."));

            int range = Math.max(5, progress - 70); // 最小范围5

            spawnWaterParticles(center, range);
            dealDamage(player, progress);

            // 延迟重复一次粒子效果
            new BukkitRunnable() {
                int count = 0;
                @Override
                public void run() {
                    if (count >= 3) {
                        this.cancel();
                        spawnWaterParticles(center, range);
                        return;
                    }
                    player.getWorld().playSound(center, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, 1f, 1f);
                    count++;
                }
            }.runTaskTimer(plugin, 0L, 20L);

        } else {
            // --- 非潜行状态 ---

            if (player.getLocation().getPitch() < -60) {
                // --- F + 抬头: 改变天气 ---
                if (progress >= SKILL_UNLOCK_3) {
                    if (!checkAndApplyCooldown(SKILL_WEATHER, player, progress)) {
                        return;
                    }
                    World world = player.getWorld();
                    if (!world.hasStorm()) {
                        world.setStorm(true);
                        // 根据进度决定下雨时长 (tick)
                        int rainDurationTicks = (progress - 35) * 20 * 10; // 稍微放大一点时长感受
                        if (rainDurationTicks < 200) rainDurationTicks = 200;

                        world.setWeatherDuration(rainDurationTicks);
                        player.sendMessage(color("&c喷&6水&e池&2里&b的&9水&5怎&d么&c不&6见&e了&2呢&b.&9.&5.&d？"));
                    } else {
                        player.sendMessage(color("&7已经是雨天了哦~"));
                    }
                } else {
                    player.sendMessage(color("&7你的魔女化程度不足以使用这个魔法！(需要 " + SKILL_UNLOCK_3 + "%)"));
                }
            } else {
                // --- F: 召唤生物 ---
                if (progress < SKILL_UNLOCK_2){
                    player.sendMessage(color("&7你的魔女化程度不足以使用这个魔法！(需要 " + SKILL_UNLOCK_2 + "%)"));
                    return;
                }
                if (!checkAndApplyCooldown(SKILL_SUMMON, player, progress)) {
                    return;
                }

                double durationBase = 5 + progress * 0.3;
                int count;
                int lifeTime = (int)durationBase + 5;

                if (progress >= 50) {
                    count = Math.max(1, progress / 10 - 4);
                    spawnRandomMonsters(center, count, lifeTime);
                    player.sendMessage(color("&d喜&6欢&e诺&a亚&b画&9的&5画&c吗&6.&e.&a."));
                } else {
                    count = Math.max(1, progress / 10);
                    spawnRandomAnimals(center, count, lifeTime);
                    player.sendMessage(color("&d喜&6欢&e诺&a亚&b画&9的&5画&c吗&6.&e.&a."));
                }
            }
        }
    }

    private boolean checkAndApplyCooldown(String key, Player player, int progress) {
        // 调用工具类计算CD
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

        internalCooldowns.put(uniqueKey, now);
        return true;
    }

    private void spawnWaterParticles(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        final int particleCount = radius * radius * 10;
        final int durationTicks = 40;
        final int intervalTicks = 3;

        new BukkitRunnable() {
            int times = 0;
            @Override
            public void run() {
                times++;
                for (int i = 0; i < particleCount; i++) {
                    double x = center.getX() + (random.nextDouble() * radius * 2) - radius;
                    double y = center.getY() + (random.nextDouble() * radius * 2) - radius;
                    double z = center.getZ() + (random.nextDouble() * radius * 2) - radius;
                    world.spawnParticle(Particle.BUBBLE, x, y, z, 1, 0, 0, 0, 0);
                }
                // CLOUD 粒子在新版本可能需要调整数据，这里保持基本用法
                world.spawnParticle(Particle.CLOUD, center, radius * 5, radius/2d, radius/2d, radius/2d, 0.1);

                if (times * intervalTicks >= durationTicks) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
    }

    private void dealDamage(Player attacker, int progress) {
        double damage = progress * 0.18;
        int range = Math.max(5, progress - 72); // 避免range过小
        if (damage <= 0) return;

        Location center = attacker.getLocation();
        for (Entity entity : attacker.getNearbyEntities(range, range, range)) {
            if (entity.equals(attacker)) continue;
            if (entity instanceof Damageable damageable) {
                damageable.damage(damage, attacker);
            }
        }
    }

    private void spawnRandomAnimals(Location loc, int count, int seconds) {
        EntityType[] types = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT};
        spawnEntities(loc, count, seconds, types);
    }

    private void spawnRandomMonsters(Location loc, int count, int seconds) {
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
        spawnEntities(loc, count, seconds, types);
    }

    private void spawnEntities(Location loc, int count, int seconds, EntityType[] types) {
        for (int i = 0; i < count; i++) {
            Location spawnLoc = loc.clone().add(
                    ThreadLocalRandom.current().nextInt(6) - 3,
                    1,
                    ThreadLocalRandom.current().nextInt(6) - 3
            );
            // 简单防止卡墙
            if (spawnLoc.getBlock().getType() != Material.AIR) {
                spawnLoc.add(0,1,0);
            }
            EntityType type = types[random.nextInt(types.length)];

            // 使用 Consumer 设置属性 (1.21 推荐写法，不过这里简单 spawn 也没事)
            Entity entity = loc.getWorld().spawnEntity(spawnLoc, type);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (entity.isValid()) {
                        entity.remove();
                        // 消失特效
                        if (entity.getLocation().getWorld() != null) {
                            entity.getWorld().spawnParticle(Particle.POOF, entity.getLocation(), 5);
                        }
                    }
                }
            }.runTaskLater(plugin, seconds * 20L);
        }
    }

    // 辅助计算半径的方法
    private double getRadius(Player user) {
        int progress = ManoPrograss.getManoProgressPercentage(user);
        return Double.parseDouble(String.format("%.2f", progress * 0.2));
    }
}