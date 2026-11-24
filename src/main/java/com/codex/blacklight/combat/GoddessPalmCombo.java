package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One shared gesture clock drives Greene's wrist, the remote hand's skin and both physical strikes. */
final class GoddessPalmCombo {
    static final int MOVE=5, END=68;
    private Vec3 drop=Vec3.ZERO;
    private float stopped=-1;
    private boolean heavy;
    private final Set<UUID> hit=new HashSet<>();

    static float ease(double t){float v=(float)Mth.clamp(t,0,1);return v*v*(3-2*v);}
    static float sweep(float t){return ease((t-11)/9);}
    static float lift(float t){return ease((t-20)/15);}
    static float slam(float t){return ease((t-42)/7);}
    static float recover(float t){return ease((t-52)/16);}
    static float clock(float age,float stopped){
        if(stopped<0 || age<=stopped)return age;
        float dt=age-stopped;
        if(stopped>=42)return stopped+(END-stopped)*ease((dt-3)/19.);
        return Math.max(0,stopped-1.8F*Math.max(0,dt-3));
    }
    static double pitch(float t){
        return (1.39*ease(t/11.)+1.40*lift(t)+.38*slam(t))*(1-recover(t));
    }
    static Vec3 palm(Vec3 home,Vec3 aim,Vec3 drop,float yaw,int side,float t){
        Vec3 forward=Vec3.directionFromRotation(0,yaw);
        Vec3 across=new Vec3(forward.z,0,-forward.x).scale(side);
        Vec3 start=aim.add(across.scale(-8)).add(0,1.20,0);
        Vec3 finish=aim.add(across.scale(8)).add(0,1.20,0);
        Vec3 overhead=aim.lerp(drop,ease((t-27)/10.)).add(0,12,0);
        Vec3 low=drop.add(0,1.25,0);
        return home.lerp(start,ease(t/10.)).lerp(finish,sweep(t))
                .lerp(overhead,lift(t)).lerp(low,slam(t)).lerp(home,recover(t));
    }
    private static Matrix4f turn(Matrix4f[] bones,int pivot,float x,float y,float z){
        var point=bones[pivot].getTranslation(new Vector3f());
        return new Matrix4f().translate(point).rotateY(y).rotateZ(z).rotateX(x).translate(-point.x,-point.y,-point.z);
    }
    private static void rotate(Matrix4f[] bones,int first,int last,Matrix4f turn){
        for(int i=first;i<=last;i++)bones[i].mulLocal(turn);
    }
    static void conduct(Matrix4f[] bones,int arm,float t){
        float open=ease(t/10.)*(1-sweep(t)), whip=sweep(t)*(1-lift(t));
        float high=lift(t)*(1-slam(t)), punch=slam(t)*(1-recover(t));
        int side=arm==5?1:-1,end=arm+13;
        rotate(bones,0,bones.length-1,turn(bones,2,.10F*open-.14F*high+.21F*punch,
                side*(-.34F*open+.40F*whip-.18F*high+.19F*punch),side*(.10F*open-.15F*whip+.14F*punch)));
        var head=turn(bones,0,.20F*open+.23F*punch,side*(.18F*open-.19F*whip),0);
        bones[0].mulLocal(head);bones[33].mulLocal(head);bones[34].mulLocal(head);
        rotate(bones,arm,end,turn(bones,arm,-.55F*open-.40F*whip-1.68F*high+.74F*punch,
                side*(-.58F*open+1.13F*whip),side*(.65F*open-.54F*whip+.53F*high-.20F*punch)));
        rotate(bones,arm+1,end,turn(bones,arm+1,-.48F*open+.32F*whip-.82F*high+.34F*punch,
                side*.32F*whip,side*.21F*high));
        rotate(bones,arm+2,end,turn(bones,arm+2,.33F*open-.48F*whip+.46F*punch,
                side*(-.25F*open+.48F*whip),side*(-.32F*open+.36F*high)));
        for(int f=0;f<4;f++){
            int digit=arm+3+3*f,last=Math.min(digit+2,end);
            for(int j=digit;j<=last;j++)rotate(bones,j,last,
                    turn(bones,j,-.52F*(high+punch),0,side*(f-1.5F)*.09F*open));
        }
    }
    void begin(Vec3 target){drop=target;hit.clear();heavy=false;stopped=-1;}
    void aimDrop(Vec3 target){drop=target;}
    Vec3 drop(){return drop;}
    float stopped(){return stopped;}
    private static AABB bounds(GoddessHandPose pose){
        Vec3 p=pose.palm();
        AABB b=new AABB(p,p);
        for(var link:pose.links()){
            var rad=Math.max(link.width(),link.depth())*1.55;
            b=b.minmax(new AABB(link.base(),link.end()).inflate(rad));
        }
        return b;
    }
    boolean tick(ServerLevel l,EncounterMob boss,GoddessHand hand,int age){
        if(stopped>=42)return age>=stopped+22;
        if(stopped>=0)return clock(age,stopped)<=0 && age>stopped+3;
        if(age>=END)return true;
        var before=hand.comboPose(age-1);
        var after=hand.comboPose(age);
        AABB area=bounds(before).minmax(bounds(after)).inflate(.45);
        var terrain=new ArrayList<AABB>();
        for(var shape:l.getBlockCollisions(boss,area))terrain.addAll(shape.toAabbs());
        boolean sweep=age>=12 && age<=20, slam=age>=43 && age<=50;
        var players=sweep||slam?l.players().stream().filter(p->p.isAlive() && !p.isCreative() && !p.isSpectator()
                && area.intersects(p.getBoundingBox())).toList():List.<net.minecraft.server.level.ServerPlayer>of();
        if(slam && !heavy){heavy=true;hit.clear();}
        if(terrain.isEmpty() && players.isEmpty())return false;
        double travel=before.palm().distanceTo(after.palm())+Math.abs(pitch(age)-pitch(age-1))*6;
        int steps=Mth.clamp((int)Math.ceil(travel/.30),1,16);
        for(int i=0;i<=steps;i++){
            float sample=age-1+(float)i/steps;
            var pose=i==0?before:i==steps?after:hand.comboPose(sample);
            var surfaces=new HydraContact.Box[pose.links().size()];
            for(int k=0;k<pose.links().size();k++){
                var link=pose.links().get(k);
                AABB envelope=new AABB(link.base(),link.end()).inflate(Math.max(link.width(),link.depth())*1.55);
                for(var block:terrain){
                    if(!block.intersects(envelope))continue;
                    if(surfaces[k]==null)surfaces[k]=link.surface();
                    var skin=surfaces[k];
                    if(!skin.touches(block))continue;
                    stopped=Math.max(0,sample-1F/steps);
                    Vec3 contact=skin.contact(block);
                    SpiderFoley.play(boss,contact,slam?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.STRIKE,slam?2.3F:1.5F);
                    SpiderFoley.ground(boss,contact,slam?2.9F:1.5F,SpiderFoley.Cue.FRACTURE);
                    SpiderFoley.strands(boss,contact,pose.palm().subtract(contact),slam?13:6);
                    boss.goddess.publish();
                    return false;
                }
            }
            for(var p:players){
                if(hit.contains(p.getUUID()) || boss.goddess.attacks.safe(p))continue;
                for(int k=1;k<pose.links().size();k++){
                    var link=pose.links().get(k);
                    if(!new AABB(link.base(),link.end()).inflate(Math.max(link.width(),link.depth())*1.55)
                            .intersects(p.getBoundingBox()))continue;
                    if(surfaces[k]==null)surfaces[k]=link.surface();
                    var skin=surfaces[k];
                    if(!skin.touches(p.getBoundingBox()))continue;
                    var point=skin.contact(p.getBoundingBox());
                    if(l.clip(new ClipContext(point,p.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS)continue;
                    hit.add(p.getUUID());
                    float hp=p.getHealth();
                    float amount=(slam ? GoddessImpact.HAND_SLAM : GoddessImpact.HAND_SWEEP).incoming(p);
                    if(p.hurt(boss.damageSources().mobAttack(boss),amount) && p.getHealth()<hp){
                        Vec3 velocity=after.palm().subtract(before.palm()).multiply(1,0,1);
                        if(slam || velocity.lengthSqr()<.01)velocity=p.position().subtract(pose.palm()).multiply(1,0,1);
                        velocity=velocity.normalize().scale(slam?.90:1.10);
                        p.push(velocity.x,slam?.26:.22,velocity.z);p.hurtMarked=true;
                        SpiderFoley.play(boss,point,slam?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.CATCH,1.7F);
                        SpiderFoley.strands(boss,point,velocity,7);
                    }
                    break;
                }
            }
        }
        return false;
    }
    void save(CompoundTag t){
        t.putDouble("DropX",drop.x);t.putDouble("DropY",drop.y);t.putDouble("DropZ",drop.z);
        t.putFloat("Stop",stopped);t.putBoolean("Heavy",heavy);
        var targets=new CompoundTag();
        for(UUID id:hit)targets.putBoolean(id.toString(),true);
        t.put("ComboTargets",targets);
    }
    void load(CompoundTag t){
        drop=new Vec3(t.getDouble("DropX"),t.getDouble("DropY"),t.getDouble("DropZ"));
        stopped=t.contains("Stop")?t.getFloat("Stop"):-1;heavy=t.getBoolean("Heavy");hit.clear();
        for(var id:t.getCompound("ComboTargets").getAllKeys())try{hit.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}
    }
}
