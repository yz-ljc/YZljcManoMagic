package top.yzljc.manosaba;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import top.yzljc.manosaba.magic.*;

import top.yzljc.manosaba.utils.CraftPlayerAPI;
import top.yzljc.manosaba.utils.ManoPrograss;

import java.util.*;

public class Manoyinxi extends JavaPlugin implements TabCompleter {

    // 魔法 id → 魔法名称（展示用）
    public static final Map<String, String> MAGIC_NAME_REGISTRY = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Manosaba 插件已启动! Ciallo～(∠・ω< )⌒★!");
        // 注册所有魔法的监听器
        getServer().getPluginManager().registerEvents(new MagicOfNoa(this), this);
        getServer().getPluginManager().registerEvents(new MagicOfSherii(this), this);
        getServer().getPluginManager().registerEvents(new MagicOfArisa(this), this);
        getServer().getPluginManager().registerEvents(new MagicOfAnan(), this);
        getServer().getPluginManager().registerEvents(new MagicOfHanna(this), this);;
        getServer().getPluginManager().registerEvents(new MagicOfKoko(this), this);
        getServer().getPluginManager().registerEvents(new MagicOfMeruru(this), this);
        getServer().getPluginManager().registerEvents(new MagicOfReia(), this);

        // 填充技能注册表，仅用于 grant/activate 时判断合法性&名称展示
        MAGIC_NAME_REGISTRY.put("magic_noa", "城崎的魔法");
        MAGIC_NAME_REGISTRY.put("magic_sherii", "雪莉的魔法");
        MAGIC_NAME_REGISTRY.put("magic_arisa", "亚里沙的魔法");
        MAGIC_NAME_REGISTRY.put("magic_anan", "安安的魔法");
        MAGIC_NAME_REGISTRY.put("magic_hanna", "汉娜的魔法");
        MAGIC_NAME_REGISTRY.put("magic_koko", "可可的魔法");
        MAGIC_NAME_REGISTRY.put("magic_meruru", "梅露露的魔法");
        MAGIC_NAME_REGISTRY.put("magic_reia", "蕾雅的魔法");

        getCommand("mano").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Manosaba 插件已卸载!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mano")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "用法: /mano <set|get|grant> <player> [amount|magicId]");
            return true;
        }

        String subCmd = args[0].toLowerCase();

        // grant/activate 魔法指令
        if (subCmd.equals("grant") || subCmd.equals("activate")) {
            if (!sender.hasPermission("manosaba.admin")) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行此操作!");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "用法: /mano grant <player> <magicId>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家不在线或不存在!");
                return true;
            }
            String magicId = args[2].toLowerCase();
            if (!MAGIC_NAME_REGISTRY.containsKey(magicId)) {
                sender.sendMessage(ChatColor.RED + "未发现该魔法ID, 可用: " + MAGIC_NAME_REGISTRY.keySet());
                return true;
            }
            ManoPrograss.setPlayerMagicId(target, magicId);
            sender.sendMessage(ChatColor.GREEN + "已为 " + target.getName() + " 启用魔法: " + magicId
                    + " (" + MAGIC_NAME_REGISTRY.get(magicId) + ")");
            target.sendMessage(ChatColor.GOLD + "你已获得新魔法: " + ChatColor.AQUA + MAGIC_NAME_REGISTRY.get(magicId));
            return true;
        }

        // set 进度指令
        if (subCmd.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "用法: /mano set <player> <amount>");
                return true;
            }
            if (!sender.hasPermission("manosaba.admin")) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行此操作!");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家不在线或不存在!");
                return true;
            }
            try {
                int amount = Integer.parseInt(args[2]);
                ManoPrograss.setManoProgress(target, amount);
                sender.sendMessage(ChatColor.GREEN + "已将玩家 " + target.getName() + " 的魔女化程度设置为: " + amount + "%");
                target.sendMessage(ChatColor.AQUA + "你的魔女化程度已变更为: " + amount + "%");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "请输入有效的整数!");
            }
            return true;
        }

        // get 进度指令
        if (subCmd.equals("get")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /mano get <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家不在线或不存在!");
                return true;
            }
            int current = ManoPrograss.getManoProgressPercentage(target);
            String magicId = ManoPrograss.getPlayerMagicId(target);
            String magicStr = magicId != null && MAGIC_NAME_REGISTRY.containsKey(magicId)
                    ? ("，当前魔法为: " + MAGIC_NAME_REGISTRY.get(magicId))
                    : "，当前无魔法";
            sender.sendMessage(ChatColor.GREEN + "玩家 " + target.getName() + " 当前的魔女化程度为: "
                    + current + "%" + magicStr);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "未知子命令: " + subCmd);
        return true;
    }

    // Tab补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("mano")) {
            if (args.length == 1) {
                return Arrays.asList("set", "get", "grant", "activate");
            }
            if (args.length == 2 && args[0].matches("set|get|grant|activate")) {
                return null; // Bukkit自带在线玩家名补全
            }
            if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("activate"))) {
                return new ArrayList<>(MAGIC_NAME_REGISTRY.keySet());
            }
        }
        return null;
    }
}