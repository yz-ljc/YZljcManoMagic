package top.yzljc.manosaba.utils;

/**
 * 技能冷却时间计算公式
 * 移植自原项目 dev.shizoukia.manosaba.utils
 */
public class MagicCooldownTime {

    public static int calculateCooldownTime(int progress) {
        int x = Math.max(0, Math.min(100, progress));
        double coolDown;
        if (x < 20) {
            coolDown = 90 - 36 * (1 - Math.exp(-0.026 * x));
        } else if (x < 50) {
            coolDown = 62.5 - 25 * (1 - Math.exp(-0.033 * (x - 20)));
        } else if (x < 80) {
            coolDown = 42.8 - 24.8 * (1 - Math.exp(-0.045 * (x - 50)));
        } else {
            coolDown = 18.0 - 8.0 * (1 - Math.exp(-0.03 * (x - 80)));
        }
        coolDown = Math.max(10.0, Math.round(coolDown * 10) / 10.0);
        return (int) Math.round(coolDown);
    }
}