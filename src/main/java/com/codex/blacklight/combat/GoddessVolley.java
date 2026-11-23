package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.EvolvedQuill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Every visible needle uses the existing physical bone projectile and is obstructed by current terrain. */
final class GoddessVolley {
    static final double NEEDLE_SPEED = 2.45;
    private final EncounterMob boss;
    GoddessVolley(EncounterMob boss){this.boss=boss;}
    private Vec3 across(Vec3 aim){
        var forward=aim.subtract(boss.position()).multiply(1,0,1).normalize();
        return forward.lengthSqr()<.1?new Vec3(1,0,0):new Vec3(forward.z,0,-forward.x);
    }
    void quill(ServerLevel level,Vec3 origin,Vec3 direction,double speed,float damage,boolean spear){
        quill(level,origin,direction,speed,damage,spear,0);
    }
    private void quill(ServerLevel level,Vec3 origin,Vec3 direction,double speed,float damage,boolean spear,int kind){
        if(direction.lengthSqr()<.001)return;
        var bolt=new EvolvedQuill(level,boss);
        bolt.setPos(origin);bolt.damage(damage);
        bolt.getPersistentData().putInt("GoddessSequence",boss.goddess.attacks.sequence());
        if(spear)bolt.makeBloodSpear();
        else if(kind!=0)bolt.barrage(kind);
        bolt.shoot(direction.x,direction.y,direction.z,(float)speed,0);
        level.addFreshEntity(bolt);
    }
    void rain(ServerLevel level,Vec3 aim,int row){
        var crown=boss.hurtRegions(1).get(11);
        if(crown.boxes().isEmpty())return;
        var across=across(aim);
        var forward=new Vec3(-across.z,0,across.x);
        var tufts=new java.util.ArrayList<>(crown.boxes());
        tufts.sort(java.util.Comparator.comparingDouble(p->p.center.dot(across)));
        int opening=2-row;
        for(int i=-3;i<=3;i++){
            if(i==opening || i==opening-1 && (boss.goddess.removed()&GoddessVitals.LEFT_ARM)!=0
                    || i==opening+1 && (boss.goddess.removed()&GoddessVitals.RIGHT_ARM)!=0)continue;
            // The middle spine crosses the led mark. The flanking spines rake front to back.
            double rake = i == 0 ? 0 : (row - 2) * 1.65 + .38 * i;
            var endpoint=aim.add(across.scale(i*2.3)).add(forward.scale(rake));
            var origin=tufts.get((i+3)*(tufts.size()-1)/6).nearest(endpoint);
            quill(level,origin,endpoint.subtract(origin),NEEDLE_SPEED,12,false,EvolvedQuill.CROWN_NEEDLE);
        }
        SpiderFoley.play(boss,tufts.get(row*(tufts.size()-1)/4).center,SpiderFoley.Cue.NEEDLE,3.6F);
    }
    boolean waves(ServerLevel level,Vec3 aim,boolean high){
        boolean fired=false;
        Vec3 side=across(aim),forward=new Vec3(-side.z,0,side.x);
        Vec3 first=aim.subtract(forward.scale(14));
        for(int i=-5;i<=5;i++){
            if(i==(high?1:-2))continue;
            Vec3 p=first.add(side.scale(i*1.6));
            var ground=level.clip(new ClipContext(p.add(0,4,0),p.add(0,-7,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(ground.getType()!=HitResult.Type.BLOCK)continue;
            var origin=ground.getLocation().add(0,high?2.30:.65,0);
            quill(level,origin,forward,high?1.25:1.55,16,false,high?EvolvedQuill.HIGH_SURF:EvolvedQuill.LOW_SURF);
            fired=true;
        }
        if(!fired)return false;
        int wrist=boss.goddess.supportingWrist();
        var joint=boss.hurtRegions(1).get(wrist>=0?wrist:1).bounds().getCenter();
        SpiderFoley.play(boss,joint,high?SpiderFoley.Cue.STRIKE:SpiderFoley.Cue.BRACE,3.7F);
        SpiderFoley.play(boss,first,high?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.STRAIN,1.3F);
        return true;
    }
    boolean spears(ServerLevel level, Vec3 aim) {
        Vec3 chest = boss.hurtRegions(1).get(1).bounds().getCenter();
        Vec3 side = across(aim);
        Vec3 forward = new Vec3(-side.z, 0, side.x);
        int missing = boss.goddess.removed();
        boolean fired = false;
        for (int lane = -3; lane <= 3; lane++) {
            if (lane == -2 && (missing & GoddessVitals.LEFT_ARM) != 0
                    || lane == 2 && (missing & GoddessVitals.RIGHT_ARM) != 0) continue;
            Vec3 direction = forward.add(side.scale(lane * .32)).normalize();
            Vec3 start = chest.add(side.scale(lane * 1.15));
            Vec3 pin = wallPin(level, start, aim, direction);
            if (pin == null) continue;
            quill(level, start, pin.subtract(start), 3.2, 18, true);
            fired = true;
        }
        if (fired) SpiderFoley.play(boss, chest, SpiderFoley.Cue.TEAR, 3.7F);
        return fired;
    }
    private Vec3 wallPin(ServerLevel level, Vec3 start, Vec3 aim, Vec3 direction) {
        // A line at ankle height can terminate on a step whose top conceals the side from the chest.
        // Climb the actual wall until both the target and the firing joint have a clear line.
        for (int rise = 0; rise <= 12; rise += 2) {
            Vec3 from = aim.add(0, rise, 0);
            var wall = level.clip(new ClipContext(from, from.add(direction.scale(72)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            if (wall.getType() != HitResult.Type.BLOCK || wall.getDirection().getAxis().isVertical() || wall.isInside()) continue;
            Vec3 inside = wall.getLocation().add(Vec3.atLowerCornerOf(wall.getDirection().getNormal()).scale(-.06));
            if (start.distanceToSqr(inside) > 72 * 72) continue;
            var approach = level.clip(new ClipContext(start, inside, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            if (approach.getType() == HitResult.Type.BLOCK && !approach.getDirection().getAxis().isVertical()
                    && !approach.isInside() && approach.getLocation().distanceToSqr(inside) < 4) {
                return approach.getLocation().add(Vec3.atLowerCornerOf(approach.getDirection().getNormal()).scale(-.04));
            }
        }
        return null;
    }

}
