package com.codex.blacklight.mixin;

import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.power.TheBoysBodyCompatibility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A severing finisher must resolve death once, rather than kill the rescued slime again. */
@Pseudo
@Mixin(targets="net.benniao.theboysmod.server.body.BodyDamageService",remap=false)
public abstract class OptionalTheBoysSeverMixin {
    @Shadow(remap=false) public static boolean syncRegrowthIntegrity(LivingEntity target){throw new AssertionError();}

    @Inject(method="breakPart(Lnet/minecraft/world/entity/LivingEntity;Lnet/benniao/theboysmod/body/BodyPart;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void blacklight$resolveVitalSeverOnce(LivingEntity target,@Coerce Object part,Vec3 point,
            DamageSource source,boolean fatal,CallbackInfo callback) {
        if(!TheBoysBodyCompatibility.owns(target))return;
        if(BiomassSystem.active(target)){syncRegrowthIntegrity(target);callback.cancel();return;}
        if(!fatal||!(part instanceof Enum<?> limb)||!(limb.name().equals("HEAD")||limb.name().equals("BODY")))return;
        var player=(ServerPlayer)target;
        player.setHealth(0);
        player.die(source==null?player.damageSources().generic():source);
        if(player.isAlive())syncRegrowthIntegrity(player);
        callback.cancel();
    }
}
