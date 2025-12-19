package top.yzljc.manosaba.utils;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Player;
import lombok.Generated;

public abstract class CraftPlayerAPI {
    private static CraftPlayerAPI instance;
    @Generated
    public abstract ResolvableProfile getResolvableProfile(Player var1);
    @Generated
    public static CraftPlayerAPI getInstance() {
        return instance;
    }
    @Generated
    public static void setInstance(CraftPlayerAPI instance) {
        CraftPlayerAPI.instance = instance;
    }
}

