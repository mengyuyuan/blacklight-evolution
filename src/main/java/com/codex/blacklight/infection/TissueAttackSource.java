package com.codex.blacklight.infection;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Explicit provenance for a Blacklight-issued organ/contact/consumption hit.
 * A native mob's original attacks stay foreign. Keep the native damage type,
 * attribution, tags and position; this adds no armor bypass or event bypass. */
public final class TissueAttackSource extends DamageSource {
    private final DamageSource original;
    private TissueAttackSource(DamageSource original){
        super(original.typeHolder(),original.getDirectEntity(),original.getEntity());this.original=original;
    }
    public static DamageSource mobAttack(Mob actor){return new TissueAttackSource(actor.damageSources().mobAttack(actor));}
    @Override public boolean is(TagKey<DamageType> tag){return original.is(tag);}
    @Override public Vec3 getSourcePosition(){return original.getSourcePosition();}
}
