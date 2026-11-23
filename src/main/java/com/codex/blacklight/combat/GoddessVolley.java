package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.EvolvedQuill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Every visible needle uses the existing physical bone projectile and is obstructed by current terrain. */
final class GoddessVolley {
    private final EncounterMob boss;
    GoddessVolley(EncounterMob boss){this.boss=boss;}
    private Vec3 across(Vec3 aim){
        var forward=aim.subtract(boss.position()).multiply(1,0,1).normalize();
        return forward.lengthSqr()<.1?new Vec3(1,0,0):new Vec3(forward.z,0,-forward.x);
    }
    void quill(ServerLevel level,Vec3 origin,Vec3 direction,double speed,float damage,boolean spear){
        if(direction.lengthSqr()<.001)return;
        var bolt=new EvolvedQuill(level,boss);
        bolt.setPos(origin);bolt.damage(damage);
        bolt.getPersistentData().putInt("GoddessSequence",boss.goddess.attacks.sequence());
        if(spear)bolt.makeBloodSpear();
        bolt.shoot(direction.x,direction.y,direction.z,(float)speed,0);
        level.addFreshEntity(bolt);
    }
    void rain(ServerLevel level,Vec3 aim,int row){
        var hair=boss.hurtRegions(1).get(11).bounds().getCenter();
        var across=across(aim);
        var forward=new Vec3(-across.z,0,across.x);
        for(int i=-3;i<=3;i++){
            if(i==1-row%3)continue;
            var endpoint=aim.add(across.scale(i*2.3)).add(forward.scale((row-2)*1.65+.38*i));
            var origin=hair.add(across.scale(i*1.4));
            quill(level,origin,endpoint.subtract(origin),2.45,12,false);
        }
        SpiderFoley.play(boss,hair.add(across.scale((row-2)*1.5)),SpiderFoley.Cue.NEEDLE,1.15F);
    }
    void waves(ServerLevel level,Vec3 aim,boolean high){
        Vec3 side=across(aim),forward=new Vec3(-side.z,0,side.x);
        Vec3 first=aim.subtract(forward.scale(14));
        for(int i=-5;i<=5;i++){
            if(i==(high?1:-2))continue;
            Vec3 p=first.add(side.scale(i*1.6));
            var ground=level.clip(new ClipContext(p.add(0,4,0),p.add(0,-7,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(ground.getType()!=HitResult.Type.BLOCK)continue;
            var origin=ground.getLocation().add(0,high?2.30:.65,0);
            quill(level,origin,forward,high?1.25:1.55,16,false);
        }
        SpiderFoley.play(boss,first,high?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.STRAIN,1.3F);
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
        if (fired) SpiderFoley.play(boss, chest, SpiderFoley.Cue.TEAR, 1.25F);
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
