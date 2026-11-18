package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.FlyerContact;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.network.ContactEffectsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Entity tracking can trail an immediate damage packet. Present the entire
 * accepted impact together when the visible flyer actually reaches its contact.
 * Never changes damage, and never blocks another actor's queued presentation. */
public final class FlyerContactPresentation {
    public static boolean ready(ContactEffectsPayload packet,double clock,float partial){
        var tissue=packet.tissue();var level=Minecraft.getInstance().level;
        if(tissue==null||level==null||clock-packet.tick()>=6)return true;
        if(!(level.getEntity(tissue.attacker()) instanceof EncounterMob flyer)||flyer.kind()!=EncounterKind.FLYER)return true;
        var contact=new Vec3(tissue.x(),tissue.y(),tissue.z());
        float yaw=Mth.rotLerp(partial,flyer.yBodyRotO,flyer.yBodyRot);
        for(var surface:FlyerContact.body(flyer.flyerPose(partial),flyer.getPosition(partial),yaw))
            if(surface.box().nearest(contact).distanceToSqr(contact)<=.04)return true;
        return false;
    }
    private FlyerContactPresentation(){}
}
