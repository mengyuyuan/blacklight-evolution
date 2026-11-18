package com.codex.blacklight.client.renderer;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.network.ContactEffectsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
/** Moving charge contact waits for its drawn hull; damage and other actors are unaffected. */
public final class BruteContactPresentation {
    public static boolean ready(ContactEffectsPayload packet,double clock,float partial){
        var tissue=packet.tissue();var level=Minecraft.getInstance().level;
        if(tissue==null||level==null||clock-packet.tick()>=6)return true;
        if(!(level.getEntity(tissue.attacker()) instanceof BlacklightMutant actor)||actor.attackMove()!=MutantMove.BRUTE_CHARGE)return true;
        var point=new Vec3(tissue.x(),tissue.y(),tissue.z());float yaw=Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
        for(var surface:BruteContact.body(actor.attackMove(),actor.attackPoseAge(partial),actor.getPosition(partial),yaw))if(surface.box().nearest(point).distanceToSqr(point)<=.04)return true;
        return false;
    }
    private BruteContactPresentation(){}
}
