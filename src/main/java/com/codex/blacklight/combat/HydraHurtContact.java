package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.infection.*;
import java.util.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Receive damage on the same authored cuboids as the visible body. No regional damage bonus. */
public final class HydraHurtContact {
    public record Contact(String bone,Vec3 point,Vec3 normal,InfectedAttackStyle style,FleshStrike strike,boolean charged) {
        public Contact(String bone,Vec3 point,Vec3 normal,InfectedAttackStyle style){
            this(bone,point,normal,style,FleshStrike.GENERIC,false);
        }
        Contact withStrike(FleshStrike weapon,boolean fullCharge){
            return new Contact(bone,point,normal,style,weapon,fullCharge);
        }
        public int appearance(){
            return com.codex.blacklight.network.InfectedImpactPayload.appearance(style,strike,charged);
        }
    }
    public static Contact nativeHit(AnimatedHurtBody body,int slot,DamageSource source){
        var hydra=(LivingEntity)body;var regions=body.hurtRegions(0);if(slot<0||slot>=regions.size())return null;
        var region=regions.get(slot);var direct=source.getDirectEntity();Vec3 from,to;
        if(direct instanceof Player p){
            from=p.getEyePosition();to=from.add(p.getLookAngle().scale(p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())+1));
        }else if(direct instanceof Projectile){
            from=direct.position();var travel=direct.getDeltaMovement();if(travel.lengthSqr()<1e-10)return null;
            to=from.add(travel).add(travel.normalize().scale(.05));
        }else{
            from=direct!=null?direct.getEyePosition():source.getSourcePosition();if(from==null)from=region.bounds().getCenter();
            to=null; // Vanilla mob damage supplies a selected part, without a weapon ray.
        }
        final Vec3 start=from;
        Vec3 point;
        if(to==null){
            // The union of articulated cubes can have an empty centre. Use a visible
            // surface of the selected part when the caller did not supply a ray.
            point=region.boxes().stream().map(box->box.nearest(start))
                    .sorted(Comparator.comparingDouble(start::distanceToSqr))
                    .filter(candidate->clear(hydra,start,candidate)).findFirst().orElse(null);
        }else{
            point=region.boxes().stream().map(box->box.clip(start,to)).flatMap(Optional::stream)
                    .min(Comparator.comparingDouble(start::distanceToSqr)).orElse(null);
            if(point!=null&&!clear(hydra,start,point))return null;
        }
        if(point==null)return null;
        var normal=from.subtract(point).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
        InfectedAttackStyle style=direct instanceof Projectile?InfectedAttackStyle.PIERCE:direct instanceof Mob mob?InfectedAttackStyle.of(mob,source):InfectedAttackStyle.CRUSH;
        if(direct instanceof LivingEntity living&&(living.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS)||living.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)))style=InfectedAttackStyle.TEAR;
        var hit=new Contact(region.bone(),point,normal,style);
        if(direct instanceof Player player&&player.getMainHandItem().isEmpty()
                &&source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK))return hit.withStrike(FleshStrike.FIST,false);
        return hit;
    }
    public static boolean clear(LivingEntity h,Vec3 from,Vec3 to){
        return h.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h)).getType()==HitResult.Type.MISS;
    }
    /** Exact first skin contact on an already obstruction-limited gun ray.
     * The caller retains allegiance, damage permission and world obstruction checks. */
    public static Contact ray(List<HydraContact.Region> regions,Vec3 from,Vec3 to,InfectedAttackStyle style){
        if(!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr())||from.distanceToSqr(to)<1e-12)return null;
        Contact best=null;double distance=Double.POSITIVE_INFINITY;Vec3 normal=from.subtract(to).normalize();
        var path=new AABB(from,to).inflate(1e-6);
        for(var region:regions){
            if(!region.bounds().intersects(path))continue;
            for(var box:region.boxes()){
                if(!box.bounds.intersects(path))continue;
                var point=box.clip(from,to);
                if(point.isEmpty())continue;
                double d=from.distanceToSqr(point.get());
                if(d<distance){
                    distance=d;
                    best=new Contact(region.bone(),point.get(),normal,style);
                }
            }
        }
        return best;
    }
    public static Contact triangle(List<HydraContact.Region> regions,Vec3 a,Vec3 b,Vec3 c,Vec3 travel,InfectedAttackStyle style){
        var bounds=new AABB(a,b).minmax(new AABB(c,c)).inflate(.00001);
        for(var region:regions)if(region.bounds().intersects(bounds))for(var box:region.boxes()){
            if(!box.bounds.intersects(bounds))continue;
            var at=box.triangle(a,b,c);if(at.isPresent())return new Contact(region.bone(),at.get(),travel.normalize().scale(-1),style);
        }return null;
    }
    public static Contact volume(List<HydraContact.Region> regions,HydraContact.Box volume,Vec3 from,InfectedAttackStyle style){
        Contact best=null;double distance=Double.POSITIVE_INFINITY;
        for(var r:regions)for(var b:r.boxes())if(b.touches(volume)){
            var point=b.contact(volume);double d=from.distanceToSqr(point);if(d<distance){distance=d;best=new Contact(r.bone(),point,from.subtract(point).normalize(),style);}
        }return best;
    }
    /** The swept sample already resolved which moving skin was touched. Only
     * confirm that this logical receiver still exists; using today's nearest
     * bone would redirect yesterday's contact into another Slug or the core. */
    static Contact reportedContact(List<HydraContact.Region> regions,WeaponCutContact.Report report){
        for(var c:report.contacts()){
            String bone=c.receivingBone();
            if(bone!=null){
                String exact=bone;
                if(regions.stream().noneMatch(r->r.bone().equals(exact)&&!r.boxes().isEmpty()))continue;
            }else{
                var nearest=regions.stream().filter(r->!r.boxes().isEmpty()).min(Comparator.comparingDouble(r->r.boxes().stream()
                        .mapToDouble(b->b.nearest(c.point()).distanceToSqr(c.point())).min().orElse(Double.MAX_VALUE)));
                if(nearest.isEmpty())continue;bone=nearest.orElseThrow().bone();
            }
            return new Contact(bone,c.point(),c.travel().normalize().scale(-1),FleshStrike.EDGE.style(report.kind()));
        }return null;
    }
    /** Shared weapon-session receiver. Sharp weapons require their actual moving triangles. */
    public static Contact weapon(AnimatedHurtBody body,net.minecraft.server.level.ServerPlayer p,WeaponCutContact.Report report,boolean sharp,
            LocalWeaponMotion.ContactBounds bounds,Vec3 previousFeet,float oldYaw,float yaw,boolean flight,double reach,Vec3 direction,double arc){
        var h=(LivingEntity)body;
        if(!report.contacts().isEmpty()){
            var contact=reportedContact(body.hurtRegions(0),report);
            return contact!=null&&clear(h,p.getEyePosition(),contact.point())?contact:null;
        }
        if(sharp&&!flight)return null;
        if(bounds==null&&!flight){
            var from=p.position().add(0,p.getBbHeight()*.5,0);
            for(var r:body.hurtRegions(0))for(var b:r.boxes()){
                var point=b.nearest(from);var d=point.subtract(from);double horizontal=d.horizontalDistance();
                if(horizontal<=reach&&Math.abs(d.y)<=2&&(arc>=360||horizontal<.05||direction.dot(d.multiply(1,0,1).scale(1/horizontal))>=Math.cos(Math.toRadians(arc*.5)))&&clear(h,p.getEyePosition(),point))
                    return new Contact(r.bone(),point,from.subtract(point).normalize(),InfectedAttackStyle.CRUSH);
            }return null;
        }
        for(int i=0;i<=16;i++){
            float alpha=i/16F;var feet=previousFeet.lerp(p.position(),alpha);float facing=net.minecraft.util.Mth.rotLerp(alpha,oldYaw,yaw);
            var forward=Vec3.directionFromRotation(0,facing);var right=new Vec3(-forward.z,0,forward.x);var up=new Vec3(0,1,0);
            var center=flight?feet.add(0,.9,0):feet.add(right.scale((bounds.minRight()+bounds.maxRight())*.5)).add(up.scale((bounds.minUp()+bounds.maxUp())*.5)).add(forward.scale((bounds.minForward()+bounds.maxForward())*.5));
            var half=flight?new Vec3(p.getBbWidth()*.5,p.getBbHeight()*.5,p.getBbWidth()*.5):new Vec3((bounds.maxRight()-bounds.minRight())*.5,(bounds.maxUp()-bounds.minUp())*.5,(bounds.maxForward()-bounds.minForward())*.5);
            var hit=volume(body.hurtRegions(alpha-1),HydraContact.oriented(center,half,right,up,forward),p.getEyePosition(),InfectedAttackStyle.CRUSH);
            if(hit!=null&&clear(h,p.getEyePosition(),hit.point()))return hit;
        }return null;
    }
    private HydraHurtContact(){}
}
