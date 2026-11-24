package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.GoddessPressurePose;
import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shape and support points for the refracting, world-space pressure membrane. */
final class GoddessBubbleRenderer {
    record Shell(Vec3 center,Vec3 axes,Vec3 right,Vec3 palmR,Vec3 palmL,Vec3 knee,Vec3 shoulder,float contact,float age,float rupture) {
        Vec3 toUnit(Vec3 delta){
            Vec3 z=right.cross(new Vec3(0,1,0));
            return new Vec3(delta.dot(right)/axes.x,delta.y/axes.y,delta.dot(z)/axes.z);
        }
        private double footprint(Vec3 direction,Vec3 point){
            double d=Math.max(0,direction.dot(toUnit(point).normalize()));
            double d2=d*d,d4=d2*d2;return d4*d4;
        }
        private double height(Vec3 point){return Mth.clamp(toUnit(point).length()+.08,.68,1.31);}
        /** Corresponds to membrane() in greene_refraction; project a moving drop onto its inner face. */
        Vec3 inside(Vec3 position){
            Vec3 radial=position.subtract(center);
            var unit=toUnit(radial);
            var d=unit.normalize();
            double drift=age*.057;
            double free=.99+.085*Math.sin(3.6*d.x+2.1*d.z+drift)*Math.cos(3.1*d.y-drift*.37)
                    +.058*Math.sin(4.7*d.y-2.8*d.z-drift*.54)*Math.cos(2.6*d.x);
            double r=footprint(d,palmR),l=footprint(d,palmL),k=footprint(d,knee),s=footprint(d,shoulder);
            double sum=r+l+k+s, weighted=(r*height(palmR)+l*height(palmL)+k*height(knee)+s*height(shoulder))/Math.max(.0001,sum);
            double skin=Mth.lerp(contact*Math.min(1,sum),free,weighted)-.03;
            return unit.length()<=skin?position:center.add(radial.scale(skin/unit.length()));
        }
    }

    static void collect(EncounterMob boss,float partial){
        var attack=boss.goddess.attacks;
        if(attack.move()!=GoddessAttacks.Move.VACUOLE)return;
        float age=attack.age(partial);
        if(age<20 || age>=62)return;
        float strength=(.20F+.80F*smooth((age-20)/7))*(1-smooth((age-54)/8));
        if(age>=28 && !attack.caught())strength*=1-smooth((age-28)/10);
        if(strength<.005F)return;
        SpiderPainEffect.shell(boss.getId(),shape(boss,partial),strength,age);
    }
    static void playerField(int id,Vec3 center,float age,net.minecraft.world.entity.LivingEntity victim,boolean preview){
        float squeeze=preview?0:smooth((age-6)/23);
        float pop=preview?0:smooth((age-32)/9);
        double minimum=victim==null?.8:Mth.clamp(victim.getBbWidth()*.55,.53,1.35);
        Vec3 axes=new Vec3(2.6-(2.6-minimum)*squeeze+1.9*pop,2.55-1.62*squeeze+1.2*pop,2.62-(2.62-minimum)*squeeze+1.6*pop);
        var shape=new Shell(center,axes,new Vec3(1,0,0),Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,0,age,pop);
        SpiderPainEffect.shell(id,shape,preview?.23F:(.35F+.65F*smooth(age/7))*(1-pop),age);
    }
    static Shell shape(EncounterMob boss,float partial){
        var attack=boss.goddess.attacks;
        float age=attack.age(partial);
        float close=smooth((age-24)/25);
        float rupture=attack.ruptured()?Mth.clamp((age-52)/9,0F,1F):0;
        float release=smooth(rupture);
        float grip=attack.caught()?smooth((age-28)/8)*(1-release):0;
        boolean ground=attack.grounded();
        Vec3 center=attack.center().add(0,-(ground?.33:.13)*close*grip,0);
        double yaw=Math.toRadians(attack.captureYaw());
        Vec3 right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
        Vec3 axes=new Vec3(2.75-2.03*close+.40*release,2.55-(ground?1.73:1.66)*close+.3*release,2.63-1.95*close+.4*release);
        // Impulse travels across the short axis first. Broken lobes then spread at different speeds.
        float rebound=1-(1-rupture)*(1-rupture)*(1-rupture);
        axes=axes.add(1.45*rebound,.83*rebound,1.21*rebound);
        Vec3 handR=Vec3.ZERO,handL=Vec3.ZERO,knee=Vec3.ZERO,shoulder=Vec3.ZERO;
        var id=attack.victim();
        var player=id==null?null:boss.level().getPlayerByUUID(id);
        if(grip>0 && player!=null){
            var pose=GoddessPressurePose.sample(attack,partial);
            handR=GoddessPressurePose.contact(player,pose,"Tool_R",attack.captureYaw(),partial).subtract(center);
            handL=GoddessPressurePose.contact(player,pose,"Tool_L",attack.captureYaw(),partial).subtract(center);
            knee=GoddessPressurePose.contact(player,pose,"Leg_R",attack.captureYaw(),partial).subtract(center);
            shoulder=GoddessPressurePose.contact(player,pose,"Arm_L",attack.captureYaw(),partial).subtract(center);
        }else grip=0;
        return new Shell(center,axes,right,handR,handL,knee,shoulder,grip,age,rupture);
    }
    private static float smooth(float x){float t=Mth.clamp(x,0F,1F);return t*t*(3-2*t);}
    private GoddessBubbleRenderer(){}
}
