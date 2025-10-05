package me.javierflores.bettergolems.reflect;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.function.Function;

public class NavigationChanger {

    private final VarHandle navigationHandle;
    private final MethodHandle getEntityHandle;

    public NavigationChanger() {
        this.navigationHandle = ReflectiveHandler.ofField("navigation")
                .from(CopperGolem.class)
                .type(PathNavigation.class)
                .privileged()
                .expect("Could not find 'navigation' field in CopperGolem. Incompatible server version?");

        this.getEntityHandle = ReflectiveHandler.ofMethod("getHandle")
                .fromCraftClass("entity.CraftCopperGolem")
                .type(CopperGolem.class)
                .specialAccess()
                .expect("Could not find 'getHandle' method in CraftCopperGolem. Incompatible server version?");
    }

    public void changeNavigation(org.bukkit.entity.CopperGolem golem, Function<CopperGolem, PathNavigation> navigationFactory) {
        try {
            var nmsGolem = (CopperGolem) getEntityHandle.invoke(golem);
            var newNavigation = navigationFactory.apply(nmsGolem);
            navigationHandle.set(nmsGolem, newNavigation);
        } catch (Throwable e) {
            throw new ReflectiveException("Something went wrong while trying to set the CopperGolem's path-navigation", e);
        }
    }
}
