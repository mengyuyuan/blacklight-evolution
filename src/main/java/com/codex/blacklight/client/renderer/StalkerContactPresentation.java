package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.StalkerContact;
import com.codex.blacklight.combat.MutantMove;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.network.ContactEffectsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Keep accepted claw tissue/audio together while network movement catches up.
 * Damage stays authoritative; stale/cancelled actors never hold the queue. */
public final class StalkerContactPresentation {
    public static boolean ready(ContactEffectsPayload packet,double clock,float partial){
        var tissue=packet.tissue();var level=Minecraft.getInstance().level;
        if(tissue==null||level==null||clock-packet.tick()>=6)return true;
        if(!(level.getEntity(tissue.attacker()) instanceof BlacklightMutant actor)
                ||actor.kind()!=BlacklightMutant.Kind.STALKER||actor.attackMove()!=MutantMove.STALKER_POUNCE)return true;
        var contact=new Vec3(tissue.x(),tissue.y(),tissue.z());
        float age=actor.attackAge(partial),yaw=Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
        for(var window:StalkerContact.windows(actor.attackMove()))if(age>=window.start()-1&&age<=window.end()+2)
            for(var claw:StalkerContact.claws(actor.attackMove(),age,actor.getPosition(partial),yaw))
                if(StalkerContact.owns(window,claw.bone())&&claw.box().nearest(contact).distanceToSqr(contact)<=.04)return true;
        return false;
    }
    private StalkerContactPresentation(){}
}
