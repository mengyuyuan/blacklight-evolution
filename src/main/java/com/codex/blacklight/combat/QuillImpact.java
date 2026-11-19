package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The point and velocity here belong to the segment that actually dealt damage. */
public final class QuillImpact {
    public static final int LIGHT=15, HEAVY=16;
    private QuillImpact() {}
    public static boolean isQuill(int motion){return motion==LIGHT||motion==HEAVY;}
    public static void hit(ServerPlayer player,LivingEntity target,Vec3 entry,Vec3 direction,boolean heavy){
        if(direction.lengthSqr()<.5)return;
        double height=(entry.y-target.getY())/target.getBbHeight();
        double side=entry.subtract(target.position()).yRot((float)Math.toRadians(target.yBodyRot-180)).x;
        int region=height>.82?1:height<.40?(side<0?8:16):Math.abs(side)>target.getBbWidth()*.31?(side<0?2:4):32;
        var contact=new WeaponCutContact.Contact(region,"quill",entry,direction,direction.scale(-1));
        var report=new WeaponCutContact.Report(WeaponCutContact.Kind.PUNCTURE,List.of(contact));
        PlayerHitEffects.emit(player,target,InfectedAttackStyle.PIERCE,heavy?1.05F:.55F,report,FleshStrike.EDGE,heavy);
        if(heavy&&target.isAlive()&&!CombatTargeting.anchoredBody(target))
            target.knockback(.32,-direction.x,-direction.z);
        Vec3 momentum=direction.scale(heavy?.96:.27).add(0,heavy?.13:.035,0);
        RagdollImpact.send(target,heavy?HEAVY:LIGHT,target.isDeadOrDying()?65:6,true,momentum,report);
    }
}
