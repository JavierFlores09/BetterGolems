package me.javierflores.bettergolems;

import me.javierflores.bettergolems.reflect.NavigationChanger;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BetterGolems extends JavaPlugin implements Listener {

    private NavigationChanger navigationChanger;

    @Override
    public void onEnable() {
        this.navigationChanger = new NavigationChanger();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.CopperGolem golem))
            return;

        navigationChanger.changeNavigation(golem, nmsGolem -> new WallClimberNavigation(nmsGolem, nmsGolem.level()));
    }
}
