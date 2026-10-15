package com.codex.blacklight.power;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;

/** Optional, lazy ABI bridge: Blacklight never requires the independent potion mod. */
public final class IndependentPassives {
    private IndependentPassives() {}
    public static boolean installed() { return net.minecraftforge.fml.ModList.get().isLoaded("adaptive_passives"); }
    private static final class Api {
        static final Method OWNS = method("owns", Player.class, int.class);
        static final Method DAMAGE = method("damage", ServerPlayer.class, DamageSource.class, float.class);
        static final Method GRANT = method("grant", ServerPlayer.class, int.class, int.class);
        static final Method MIGRATE = method("migrate", ServerPlayer.class, int.class, int.class, int.class);
        private static Method method(String name, Class<?>... args) {
            try { return Class.forName("com.codex.passives.PassiveApi").getMethod(name, args); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Incompatible Adaptive Passives API", e); }
        }
    }
    private static Object call(Method method, Object... args) {
        try { return method.invoke(null, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Adaptive Passives integration failed", e); }
    }
    public static boolean owns(Player p, int flag) { return installed() && (boolean)call(Api.OWNS, p, flag); }
    public static float damage(ServerPlayer p, DamageSource source, float amount) {
        return installed() ? (float)call(Api.DAMAGE, p, source, amount) : amount;
    }
    public static boolean grant(ServerPlayer p, int flag) { return installed() && (boolean)call(Api.GRANT, p, flag, 1); }
    public static boolean migrate(ServerPlayer p, AdaptivePassiveState old) {
        if (!installed() || old.permanentMask() == 0) return false;
        call(Api.MIGRATE, p, old.permanentMask(), old.adaptation(), old.regeneration()); return true;
    }
}
