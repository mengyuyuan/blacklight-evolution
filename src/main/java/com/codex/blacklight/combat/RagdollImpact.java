package com.codex.blacklight.combat;

import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Only call after accepted damage, accepted control, or an actual throw release. */
@net.neoforged.fml.common.EventBusSubscriber(modid="blacklight")
public final class RagdollImpact {
    private RagdollImpact() {}
    public static boolean supportedBody(LivingEntity v){
        return !v.isBaby()&&!v.isPassenger()&&!v.isSleeping()&&Math.abs(v.getScale()-1)<.01 && (v instanceof net.minecraft.world.entity.monster.Zombie
                ||v instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                ||v instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                ||v instanceof com.codex.blacklight.entity.MilitarySoldier
                ||v instanceof com.codex.blacklight.entity.BlacklightCommonInfected
                ||v instanceof net.minecraft.world.entity.animal.Pig);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void edgedDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
        if(event.getNewDamage()<=0 || !(event.getSource().getDirectEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || com.codex.blacklight.mode.PrototypeModeSystem.isActive(player))return;
        var item=player.getMainHandItem();
        if(!item.is(net.minecraft.tags.ItemTags.SWORDS)&&!item.is(net.minecraft.tags.ItemTags.AXES))return;
        var victim=event.getEntity();
        if(!supportedBody(victim))return;
        var cut=WeaponCutContact.ordinary(player,victim);
        if(cut.contacts().isEmpty())cut=WeaponCutContact.impact(player,victim,player.getLookAngle());
        WeaponDismemberment.weapon(victim,cut);
        var profile=new ImpactProfile(HitReaction.STAGGER,ImpactProfile.RIGHT,8,.1,0,false);
        ImpactControl.begin(victim,profile,player.getYRot());
        send(victim,ImpactProfile.RIGHT,victim.isDeadOrDying()?60:8,true,
                victim.getDeltaMovement().add(player.getLookAngle().scale(.18)),cut);
    }
    public static void send(LivingEntity victim, int motion, int ticks, boolean sharp, Vec3 velocity) {
        send(victim,motion,ticks,sharp,velocity,WeaponCutContact.Report.NONE);
    }
    public static void send(LivingEntity victim, int motion, int ticks, boolean sharp, Vec3 velocity,WeaponCutContact.Report cut) {
        if(victim.level().isClientSide || victim.isRemoved() || !supportedBody(victim) || victim instanceof Player
                || CombatTargeting.multipart(victim) || victim.isBaby() || victim.getBbWidth()>1.2F
                || victim.getBbHeight()>2.8F || victim.isInvisible())return;
        var capture=victim.getData(ModAttachments.CONSUME_VISUAL);
        if(capture.active() && capture.stage()!=ConsumeVisual.FLIGHT)return;
        if(!Double.isFinite(velocity.lengthSqr()))return;
        if(velocity.lengthSqr()>9)velocity=velocity.normalize().scale(3);
        var impulses=new java.util.ArrayList<RagdollImpactPayload.CutImpulse>();
        int mask=victim.getData(ModAttachments.DISMEMBER);
        for(int bit=1;bit<=32;bit<<=1){
            if((mask&bit)==0)continue;
            Vec3 travel=Vec3.ZERO;for(var contact:cut.contacts())if(contact.region()==bit)travel=travel.add(contact.travel());
            if(travel.lengthSqr()<1e-8)continue;
            var impulse=velocity.scale(.5).add(travel.normalize().scale(.38));
            impulses.add(new RagdollImpactPayload.CutImpulse(bit,(float)impulse.x,(float)impulse.y,(float)impulse.z));
        }
        var planes=new java.util.ArrayList<RagdollImpactPayload.CutPlane>();
        if((mask&32)!=0&&victim instanceof net.minecraft.world.entity.animal.Pig){
            // One plane per intersecting claw/edge, in the receiver's unrotated body coordinates.
            var edges=new java.util.HashSet<String>();
            float angle=(float)Math.toRadians(victim.yBodyRot-180);
            for(var contact:cut.contacts()){
                if(contact.region()!=32||!edges.add(contact.edge()))continue;
                var point=contact.point().subtract(victim.position()).yRot(angle);
                var normal=contact.normal().yRot(angle).normalize();
                if(normal.lengthSqr()<.5)continue;
                // Distinct blades remain distinct even when their planes are close or nearly parallel.
                // The mesh clipper alone rejects a grazing/degenerate split; no three-blade truncation.
                planes.add(new RagdollImpactPayload.CutPlane(point,normal));
                if(planes.size()==RagdollImpactPayload.MAX_CUT_PLANES)break;
            }
        }
        var wounds=new java.util.ArrayList<RagdollImpactPayload.Wound>();
        var woundedEdges=new java.util.HashSet<String>();float localYaw=(float)Math.toRadians(victim.yBodyRot-180);
        for(var contact:cut.contacts()){
            if(!woundedEdges.add(contact.edge()))continue;
            Vec3 point=WeaponCutContact.woundPoint(victim,contact);
            Vec3 travel=contact.travel().yRot(localYaw).normalize();
            if(travel.lengthSqr()<.5)continue;
            // The entry wound faces outward; the blade's travel carries the spray sideways.
            Vec3 out=point.subtract(new Vec3(0,victim.getBbHeight()*.5,0)).normalize();
            Vec3 direction=out.scale(.65).add(travel.scale(.8)).add(0,.12,0).normalize();
            float strength=(float)Math.clamp((sharp?.65:.4)+contact.travel().length()*.3+(victim.isDeadOrDying()?.35:0),.35,1.8);
            wounds.add(new RagdollImpactPayload.Wound(contact.region(),point,direction,strength));
            if(wounds.size()==RagdollImpactPayload.MAX_CUT_PLANES)break;
        }
        PacketDistributor.sendToPlayersTrackingEntity(victim,new RagdollImpactPayload(victim.getId(),motion,
                Math.clamp(ticks,4,80),HitStop.remainingTicks(victim),sharp,mask,velocity.x,velocity.y,velocity.z,impulses,planes,wounds));
    }
}
