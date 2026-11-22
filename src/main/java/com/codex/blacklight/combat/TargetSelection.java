package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.GreenePart;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Stable network identity: a persistent actor plus a logical damage region.
 * Boss subtargets share their parent's identity across both sides of the connection. */
public record TargetSelection(Entity target,int region) {
    public boolean regional(){
        return target instanceof EncounterMob mob && (mob.kind()==EncounterKind.ELIZABETH_GREENE || mob.kind()==EncounterKind.GOLIATH);
    }
    public GoliathLockPart goliathPart(){
        return target instanceof EncounterMob mob && mob.kind()==EncounterKind.GOLIATH ? GoliathLockPart.byId(region) : null;
    }
    public static TargetSelection fromHit(Entity entity){
        if(entity instanceof GreenePart part && part.getParent().kind()==EncounterKind.GOLIATH){
            var subtarget=GoliathLockPart.forSkin(GoliathContact.regionBones().get(part.slot));
            return new TargetSelection(part.getParent(),subtarget==null?-1:subtarget.ordinal());
        }
        if(entity instanceof GreenePart part&&part.getParent().kind()==EncounterKind.ELIZABETH_GREENE)
            return new TargetSelection(part.getParent(),part.getParent().spider.first()||part.slot<3?part.slot:-1);
        return new TargetSelection(ExplicitTarget.resolve(entity),-1);
    }
    /** The vanilla crosshair only picks entities within interaction reach.
     * Inspect the first actual skin on the longer lock ray before scoring nearby targets. */
    public static TargetSelection aimedBoss(Player player,List<Entity> candidates,Vec3 from,Vec3 to,float partial,double range){
        double closest=Double.POSITIVE_INFINITY;
        TargetSelection picked=null;
        for(var entity:candidates){
            if(!(entity instanceof EncounterMob mob) || (mob.kind()!=EncounterKind.GOLIATH && mob.kind()!=EncounterKind.ELIZABETH_GREENE))continue;
            var hit=HydraHurtContact.ray(mob.hurtRegions(partial),from,to,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
            if(mob.spider.first()){
                var throat=mob.spider.aperture(from,to,Vec3.ZERO,partial);
                if(throat!=null && throat.fraction()<=1 && (hit==null||from.distanceTo(throat.center())<=from.distanceTo(hit.point())+.10
                     || (hit.bone().equals("rim")||hit.bone().equals("shell")||hit.bone().startsWith("feed_")) && hit.point().distanceToSqr(throat.center())<2.5*2.5)){
                    hit=new HydraHurtContact.Contact("mouth",throat.center(),throat.normal(),com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
                }
            }
            if(hit==null)continue;
            double distance=from.distanceToSqr(hit.point());
            if(distance>=closest || !CombatTargeting.clearRay(player,from,hit.point()))continue;
            int slot;
            if(mob.kind()==EncounterKind.ELIZABETH_GREENE){
                slot=mob.spider.first()?SpiderAnatomy.NAMES.indexOf(hit.bone()):hit.bone().startsWith("weak_")?Integer.parseInt(hit.bone().substring(5)):-1;
            }else{
                var part=GoliathLockPart.forSkin(hit.bone());
                slot=part==null?-1:part.ordinal();
            }
            var selection=new TargetSelection(mob,slot);
            if(!selection.visibleWithinRange(player,range)){
                // The torso and plated left arm have no independent P2 subtarget.
                // Aim at the visible joint on this actor closest to the crosshair.
                var direction=to.subtract(from).normalize();
                selection=choices(player,mob).stream().filter(s->s.visibleWithinRange(player,range))
                        .max(Comparator.comparingDouble(s->s.point(player,partial).subtract(from).normalize().dot(direction)))
                        .orElse(null);
            }
            closest=distance;
            picked=selection;
        }
        if(picked!=null)for(var entity:candidates){
            if(entity==picked.target || entity instanceof EncounterMob mob && (mob.kind()==EncounterKind.GOLIATH || mob.kind()==EncounterKind.ELIZABETH_GREENE))continue;
            var interception=entity.getBoundingBox().clip(from,to);
            if(interception.isPresent() && from.distanceToSqr(interception.get())<closest)return null;
        }
        return picked;
    }
    public boolean selectable(Player player){
        if(!ExplicitTarget.selectable(player,target))return false;
        if(!regional())return region==-1;
        var boss=(EncounterMob)target;
        if(boss.kind()==EncounterKind.GOLIATH){
            var part=goliathPart();
            return part!=null && part.available(boss);
        }
        if(boss.spider.first())return boss.spider.live() && region>=0 && region<SpiderAnatomy.NAMES.size() && !boxes().isEmpty();
        return region>=-1&&region<3&&boss.greeneBattle().targetable()
                &&(region<0?boss.greeneBattle().coreDamageable():!boss.broken(region))
                &&!boxes().isEmpty();
    }
    public static List<TargetSelection> choices(Player player,Entity entity){
        var result=new ArrayList<TargetSelection>();var whole=new TargetSelection(entity,-1);
        int count=entity instanceof EncounterMob boss && boss.spider.first()?SpiderAnatomy.NAMES.size():3;
        if(whole.regional())for(int i=0;i<count;i++){var part=new TargetSelection(entity,i);if(part.selectable(player))result.add(part);}
        if(whole.selectable(player))result.add(whole);
        return List.copyOf(result);
    }
    /** Cycle the currently visible logical regions, including wraparound. */
    public TargetSelection nextVisible(Player player,double range){
        var visible=choices(player,target).stream().filter(s->s.visibleWithinRange(player,range)).toList();
        if(visible.isEmpty())return null;
        int at=visible.indexOf(this);return visible.get((at+1)%visible.size());
    }
    public List<HydraContact.Box> boxes(){
        if(!regional())return List.of();
        if(((EncounterMob)target).kind()==EncounterKind.GOLIATH){
            var part=goliathPart();
            return part==null || !part.available((EncounterMob)target) ? List.of()
                    : GoliathContact.lockSurface((EncounterMob)target,part,0).boxes();
        }
        if(((EncounterMob)target).spider.first())return region>=0 && region<SpiderAnatomy.NAMES.size()?((EncounterMob)target).hurtRegions(0).get(region).boxes():List.of();
        var result=new ArrayList<HydraContact.Box>();
        for(var surface:((EncounterMob)target).hurtRegions(0)){
            boolean selected=region<0?!surface.bone().startsWith("weak_"):surface.bone().equals("weak_"+region);
            if(selected)result.addAll(surface.boxes());
        }
        return List.copyOf(result);
    }
    private List<Vec3> points(Player player){
        var eye=player.getEyePosition();var result=new ArrayList<Vec3>();
        for(var box:boxes()){
            var nearest=box.nearest(eye);if(CombatTargeting.clearRay(player,eye,nearest))result.add(nearest);
            else for(var corner:box.points)if(CombatTargeting.clearRay(player,eye,corner))result.add(corner);
        }
        result.sort(Comparator.comparingDouble(eye::distanceToSqr));return result;
    }
    public Vec3 point(Player player){return point(player,0);}
    public Vec3 point(Player player,float partial){
        var part=goliathPart();
        if(part!=null){
            var surface=GoliathContact.lockSurface((EncounterMob)target,part,partial);
            var point=surface.focus(player,Double.MAX_VALUE);
            return point!=null?point:surface.joint();
        }
        if(!regional() || target instanceof EncounterMob mob && mob.kind()==EncounterKind.GOLIATH)return ExplicitTarget.point(player,target);
        if(region>=0){
            var mob=(EncounterMob)target;
            if(region==SpiderAnatomy.MOUTH && mob.spider.first()){
                var center=mob.spider.visibleMouth(player,partial,Double.MAX_VALUE);
                if(center!=null)return center;
            }
            var skin=mob.hurtRegions(partial);
            var visible=GreeneContact.focus(skin,region,player,Double.MAX_VALUE);
            if(visible!=null)return visible;
            if(mob.spider.first() && SpiderAnatomy.tendon(region) && !mob.spider.exposedTendon(SpiderAnatomy.legForRegion(region))){
                var plate=GreeneContact.focus(skin,region-1,player,Double.MAX_VALUE);
                if(plate!=null)return plate;
            }
            // Keep a retained lock on its own limb behind cover. Attack
            // paths independently check reach, skin and block visibility.
            return skin.get(region).boxes().stream().map(b->b.nearest(player.getEyePosition()))
                    .min(Comparator.comparingDouble(player.getEyePosition()::distanceToSqr)).orElse(target.position());
        }
        var visible=points(player);if(!visible.isEmpty())return visible.get(0);
        return boxes().stream().map(b->b.nearest(player.getEyePosition()))
                .min(Comparator.comparingDouble(player.getEyePosition()::distanceToSqr)).orElse(target.position());
    }
    public double distanceSquared(Player player){
        if(!regional())return ExplicitTarget.distanceSquared(player,target);
        return boxes().stream().mapToDouble(b->player.getEyePosition().distanceToSqr(b.nearest(player.getEyePosition())))
                .min().orElse(Double.POSITIVE_INFINITY);
    }
    public boolean visibleWithinRange(Player player,double range){
        if(!Double.isFinite(range)||range<0||!selectable(player))return false;
        if(!regional())return ExplicitTarget.visibleWithinRange(player,target,range);
        var part=goliathPart();
        if(part!=null)return GoliathContact.lockSurface((EncounterMob)target,part,0).focus(player,range)!=null;
        if(region==SpiderAnatomy.MOUTH && ((EncounterMob)target).spider.first() && ((EncounterMob)target).spider.visibleMouth(player,0,range)!=null)return true;
        if(region>=0){
            var mob=(EncounterMob)target;var skin=mob.hurtRegions(0);
            if(GreeneContact.focus(skin,region,player,range)!=null)return true;
            return mob.spider.first() && SpiderAnatomy.tendon(region) && !mob.spider.exposedTendon(SpiderAnatomy.legForRegion(region)) && GreeneContact.focus(skin,region-1,player,range)!=null;
        }
        return points(player).stream().anyMatch(p->player.getEyePosition().distanceToSqr(p)<=range*range+1e-6);
    }
    public String label(){
        if(target instanceof EncounterMob boss && boss.spider.first() && region>=0 && region<SpiderAnatomy.NAMES.size())return net.minecraft.network.chat.Component.translatable(SpiderAnatomy.label(region)).getString()
            +(SpiderAnatomy.tendon(region)?net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_spider."+
              (boss.spider.exposedTendon(SpiderAnatomy.legForRegion(region))?"exposed_tendon":"covered_tendon")).getString():"")
            +(region==SpiderAnatomy.RIM && boss.spider.feeding()?net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_spider.aim_inside").getString():"")
              +(region==SpiderAnatomy.MOUTH?net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_spider."+(boss.spider.sealed()?"sealed":boss.spider.healingSpent()?"spent":boss.spider.feeding()?"open":"closed")).getString():"");
        var part=goliathPart();
        if(part!=null)return target.getDisplayName().getString()+" · "+net.minecraft.network.chat.Component.translatable(part.labelKey).getString();
        return target.getDisplayName().getString()+(target instanceof EncounterMob mob&&mob.kind()==EncounterKind.ELIZABETH_GREENE?(region<0?" · 母体核心":" · 防御节段 "+(region+1)):"");}
}
