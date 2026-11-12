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
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> DISABLED=
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","ragdoll_disabled"));
    private RagdollImpact() {}
    public static boolean supportedBody(LivingEntity v){
        return !(v instanceof Player) && !v.getType().is(DISABLED) && Float.isFinite(v.getBbWidth()+v.getBbHeight())
                && v.getBbWidth()>0 && v.getBbHeight()>0 && v.getBbWidth()<=64 && v.getBbHeight()<=64;
    }
    /** Dedicated rigs have fixed adult anatomy; every other model uses its rendered skeleton. */
    public static boolean legacyBody(LivingEntity v){
        return !v.isBaby()&&!v.isPassenger()&&!v.isSleeping()&&Math.abs(v.getScale()-1)<.01
                &&v.getBbWidth()<=1.2F&&v.getBbHeight()<=2.8F && (v instanceof net.minecraft.world.entity.monster.Zombie
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
        send(victim,motion,ticks,sharp,velocity,cut,false,cut);
    }
    /** Dead entities leave the server movement hold; their captured body still shares the contact pause. */
    public static void send(LivingEntity victim,int motion,int ticks,boolean sharp,Vec3 velocity,WeaponCutContact.Report cut,int corpseFreeze){
        send(victim,motion,ticks,sharp,velocity,cut,false,cut,corpseFreeze);
    }
    /** A later edge of the same accepted strike adds a wound/cut, not another launch. */
    public static void refine(LivingEntity victim,int motion,int ticks,WeaponCutContact.Report cut,WeaponCutContact.Report wounds){
        send(victim,motion,ticks,true,Vec3.ZERO,cut,true,wounds);
    }
    private static void send(LivingEntity victim,int motion,int ticks,boolean sharp,Vec3 velocity,WeaponCutContact.Report cut,boolean refinement,WeaponCutContact.Report woundContacts){
        send(victim,motion,ticks,sharp,velocity,cut,refinement,woundContacts,0);
    }
    static int freezeTicks(boolean refinement,boolean dead,int remaining,int corpseFreeze){
        return refinement?0:Math.clamp(dead?corpseFreeze:remaining,0,8);
    }
    private static void send(LivingEntity victim,int motion,int ticks,boolean sharp,Vec3 velocity,WeaponCutContact.Report cut,boolean refinement,WeaponCutContact.Report woundContacts,int corpseFreeze){
        if(victim.level().isClientSide || victim.isRemoved() || !supportedBody(victim) || victim instanceof Player
                || victim.isInvisible())return;
        var capture=victim.getData(ModAttachments.CONSUME_VISUAL);
        if(capture.active() && capture.stage()!=ConsumeVisual.FLIGHT)return;
        if(!Double.isFinite(velocity.lengthSqr()))return;
        if(velocity.lengthSqr()>9)velocity=velocity.normalize().scale(3);
        var impulses=new java.util.ArrayList<RagdollImpactPayload.CutImpulse>();
        int mask=victim.getData(ModAttachments.DISMEMBER)&WeaponDismemberment.ALL;
        for(int bit=1;bit<=32;bit<<=1){
            // Every confirmed contact drives its struck joint, including non-lethal hits.
            // Separation remains governed independently by the server's dismember mask.
            Vec3 travel=Vec3.ZERO;for(var contact:cut.contacts())if(contact.region()==bit)travel=travel.add(contact.travel());
            if(travel.lengthSqr()<1e-8)continue;
            var impulse=velocity.scale(.5).add(travel.normalize().scale(.38));
            impulses.add(new RagdollImpactPayload.CutImpulse(bit,(float)impulse.x,(float)impulse.y,(float)impulse.z));
        }
        var planes=new java.util.ArrayList<RagdollImpactPayload.CutPlane>();
        if(sharp&&mask!=0&&victim.isDeadOrDying()&&cut.kind()==WeaponCutContact.Kind.SLICE){
            // One plane per intersecting claw/edge, in the receiver's unrotated body coordinates.
            var edges=new java.util.HashSet<String>();
            float angle=(float)Math.toRadians(victim.yBodyRot-180);
            for(var contact:cut.contacts()){
                if(!edges.add(contact.edge()+(contact.path().isEmpty()?":"+contact.region():"")))continue;
                var point=contact.point().subtract(victim.position()).yRot(angle);
                var normal=contact.normal().yRot(angle).normalize();
                if(normal.lengthSqr()<.5)continue;
                // Distinct blades remain distinct even when their planes are close or nearly parallel.
                // The mesh clipper alone rejects a grazing/degenerate split; no three-blade truncation.
                planes.add(new RagdollImpactPayload.CutPlane(contact.path().isEmpty()&&legacyBody(victim)?contact.region():0,point,normal,contact.stroke(),contact.edge(),contact.path()));
                if(planes.size()==RagdollImpactPayload.MAX_CUT_PLANES)break;
            }
        }
        var wounds=new java.util.ArrayList<RagdollImpactPayload.Wound>();
        var woundedEdges=new java.util.HashSet<String>();float localYaw=(float)Math.toRadians(victim.yBodyRot-180);
        for(var contact:woundContacts.contacts()){
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
                Math.clamp(ticks,4,80),freezeTicks(refinement,victim.isDeadOrDying(),HitStop.remainingTicks(victim),corpseFreeze),sharp,refinement,mask,velocity.x,velocity.y,velocity.z,impulses,planes,wounds,victim.level().getGameTime()));
    }
}
