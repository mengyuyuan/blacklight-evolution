package com.codex.blacklight.city;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

/** A transient paired-animation constraint, deliberately unrelated to vanilla riding. */
public final class HydraTransport {
    private HydraTransport(){}
    public static BlacklightHydra carrier(Player p){
        if(p.isRemoved()||!ModAttachments.available(p))return null;
        int id=ModAttachments.peekData(p,ModAttachments.HYDRA_CARRIER);
        return id>=0&&p.level().getEntity(id) instanceof BlacklightHydra h&&h.isAlive()&&h.heldId()==p.getId()&&h.transporting()?h:null;
    }
    public static boolean active(Player p){return carrier(p)!=null;}
    public static void place(Player p){
        var h=carrier(p);if(h==null)return;
        var pos=h.gripPosition(p,0);p.setPos(pos);p.setDeltaMovement(0,0,0);p.fallDistance=0;p.setOnGround(false);
        p.yBodyRot=h.getYRot();
    }
}
