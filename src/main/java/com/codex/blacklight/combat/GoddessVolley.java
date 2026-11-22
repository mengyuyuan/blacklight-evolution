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
    void spears(ServerLevel level,Vec3 aim){
        Vec3 chest=boss.hurtRegions(1).get(1).bounds().getCenter(),side=across(aim);
        for(int i=-3;i<=3;i++){
            var dir=aim.add(side.scale(2.4*i)).subtract(chest).normalize();
            quill(level,chest.add(side.scale(i*.7)),dir,2.6,18,true);
        }
        SpiderFoley.play(boss,chest,SpiderFoley.Cue.TEAR,1.25F);
    }
}
