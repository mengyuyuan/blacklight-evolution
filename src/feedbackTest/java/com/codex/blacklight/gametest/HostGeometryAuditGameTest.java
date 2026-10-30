package com.codex.blacklight.gametest;

import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HostGeometryAuditGameTest {
    @GameTest(template="wall_test",timeoutTicks=50,batch="audit_host_geometry")
    public static void nonFiniteSavedBodyValuesCannotPoisonGeometry(GameTestHelper h){
        for(var type:new EntityType<?>[]{EntityType.COW,EntityType.WARDEN,EntityType.WITHER,EntityType.ENDER_DRAGON}){
            var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());var source=(Mob)type.create(h.getLevel());
            try{
                host.inherit(source);var saved=new CompoundTag();host.saveWithoutId(saved);var data=saved.getCompound("BlacklightHost");
                data.putFloat("Width",Float.NaN);data.putFloat("Height",Float.POSITIVE_INFINITY);data.putFloat("ModelScale",Float.NaN);
                data.putFloat("BodyScale",Float.NaN);data.putFloat("BaseBodyScale",Float.POSITIVE_INFINITY);
                host.load(saved);
                h.assertTrue(Float.isFinite(host.getBbWidth())&&host.getBbWidth()>0&&Float.isFinite(host.getBbHeight())&&host.getBbHeight()>0,
                        "saved dimensions must be positive and finite: "+type+" / "+host.getBoundingBox());
                h.assertTrue(Float.isFinite(host.visualScale())&&Float.isFinite(host.bodyScale()),"render/part scale must also be finite: "+type);
                for(var box:host.combatHitboxes())h.assertTrue(Double.isFinite(box.minX+box.minY+box.minZ+box.maxX+box.maxY+box.maxZ),"multipart boxes must remain finite");
            }finally{host.discard();source.discard();}
        }h.succeed();
    }
}
