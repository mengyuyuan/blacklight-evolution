package com.codex.blacklight.combat;

import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Receiving geometry shares the animated mesh; all proxies retain one living parent. */
public interface AnimatedHurtBody {
    default boolean animatedHurtBody(){return true;}
    List<HydraContact.Region> hurtRegions(float partial);
    HydraHurtContact.Contact incomingContact();
    Vec3 incomingPoint();
    boolean hurtAt(DamageSource source,float amount,HydraHurtContact.Contact contact);
    void confirmedIncoming(DamageSource source,float amount);
    static AnimatedHurtBody of(Entity entity){return entity instanceof AnimatedHurtBody body&&body.animatedHurtBody()?body:null;}
}
