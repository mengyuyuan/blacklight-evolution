package com.codex.blacklight.compat;

import com.codex.blacklight.power.CombatScale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/** Re-evaluate the native attribute with every foreign modifier and its real
 * operation intact. Dividing a sanitized final value cannot undo clamping. */
public final class NativeAttributeBaseline {
    private NativeAttributeBaseline() {}
    public static double without(AttributeInstance attribute,ResourceLocation owned){
        if(attribute==null)return 0;
        var snapshot=new AttributeInstance(attribute.getAttribute(),ignored->{});
        snapshot.setBaseValue(attribute.getBaseValue());
        for(var modifier:attribute.getModifiers())if(!modifier.id().equals(owned))snapshot.addTransientModifier(modifier);
        double value=snapshot.getValue();return Double.isFinite(value)?Math.max(0,value):0;
    }
}
