package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

public final class GoddessCrownCast {
    public static final int FIRST_ROW=19, SPIT_A=38, SPIT_B=58, FLIGHT=20;
    public static final int OPEN=SPIT_B+FLIGHT, RISE=OPEN+60, ACID_END=RISE+20;
    public static final int BLADES=12, BURST=28, WITHDRAW=53, ERUPTION_END=82;
    public static final int SEGMENTS=10;
    private static final Vec3 UP=new Vec3(0,1,0),RIGHT=new Vec3(1,0,0),FRONT=new Vec3(0,0,1);
    private final EncounterMob boss;
    private final GoddessHoverClearance hoverClearance = new GoddessHoverClearance();
    private int serial=-1,cut;
    private Vec3 landing;
    private final List<Glob> globs=new ArrayList<>();
    private final Map<UUID,Integer> hits=new HashMap<>();
    private final Vec3[] rim=new Vec3[BLADES];
    public static final class Glob {
        public final Vec3 from,to;
        public final int start,row;
        private int stopped=FLIGHT;
        Glob(Vec3 from,Vec3 to,int start,int row){this.from=from;this.to=to;this.start=start;this.row=row;}
        public Vec3 point(float age){return arc(from,to,(age-start)/FLIGHT);}
        public boolean visible(float age){return age>=start && age<start+stopped;}
    }
    public GoddessCrownCast(EncounterMob boss){this.boss=boss;}
    public static boolean active(GoddessAttacks.Move move){return move==GoddessAttacks.Move.INVERTED_ACID || move==GoddessAttacks.Move.TENDRIL_ERUPTION;}
    public boolean acting(){return boss.goddess.action()==GoddessEncounter.Action.FLYING && active(boss.goddess.attacks.move()) && serial==boss.goddess.attacks.sequence();}
    public boolean inverted(){return boss.goddess.attacks.move()==GoddessAttacks.Move.INVERTED_ACID;}
    public List<Glob> globs(){return Collections.unmodifiableList(globs);}
    public Vec3 tip(int lane){return rim[lane];}
    public static float smooth(float age,float a,float b){
        float t=Math.max(0,Math.min(1,(age-a)/(b-a)));return t*t*(3-2*t);
    }
    public static float inversion(float age){return smooth(age,0,27)*(1-smooth(age,RISE,ACID_END));}
    public static float eruption(float age){return smooth(age,BURST,BURST+10)*(1-smooth(age,WITHDRAW,73));}
    public static Vec3 arc(Vec3 start,Vec3 end,float fraction){
        double f=Math.max(0,Math.min(1,fraction)),height=Math.min(5,.65+.15*start.distanceTo(end));
        return start.lerp(end,f).add(0,4*height*f*(1-f),0);
    }
    public static boolean rowOpen(int row,float age,int mask){
        return row>=0 && row<2 && (mask&(1<<row))==0 && age>=8+row*17 && age<(row==0?SPIT_A:SPIT_B);
    }
    float colonyFold(GoddessCrownColonies.Actor actor,float partial){
        float age=boss.goddess.attacks.age(partial);
        if(boss.goddess.attacks.move()==GoddessAttacks.Move.TENDRIL_ERUPTION){
            float delay=actor.index()%5;
            return .75F*smooth(age,3+delay,22+delay)*(1-smooth(age,29+delay,43+delay))
                    -.28F*smooth(age,26+delay,31+delay)*(1-smooth(age,40,59));
        }
        if(actor.row()<0)return 0;
        int row=actor.row(),fire=row==0?SPIT_A:SPIT_B;
        float lag=(actor.order()%6)*1.2F;
        float fold=smooth(age,9+row*17+lag,26+row*17+lag);
        if((cut&(1<<row))!=0)return fold*(1-smooth(age,fire-1,fire+7))*.22F;
        return fold*(1-smooth(age,fire+7+lag,fire+22+lag))-.22F*smooth(age,fire-1,fire+2)*(1-smooth(age,fire+2,fire+8));
    }
    public void pose(Matrix4f[] joints,float partial){
        if(!acting())return;
        float age=boss.goddess.attacks.age(partial);
        if(boss.goddess.attacks.move()==GoddessAttacks.Move.TENDRIL_ERUPTION){
            float gather=smooth(age,1,24)*(1-smooth(age,27,33)),kick=smooth(age,27,32)*(1-smooth(age,43,66));
            var hip=joints[2].getTranslation(new Vector3f());
            for(int j=0;j<joints.length;j++){
                float weight=j==3||j==4?.18F:j==2?.45F:1;
                var bend=new Matrix4f().translate(hip).rotateX(weight*(.32F*gather-.24F*kick)).translate(-hip.x,-hip.y,-hip.z);
                joints[j].mulLocal(bend);
            }
            for(int first:new int[]{5,19}){
                var shoulder=joints[first].getTranslation(new Vector3f());
                var arm=new Matrix4f().translate(shoulder).rotateZ((first==5?1:-1)*(.32F*gather-.64F*kick))
                      .translate(-shoulder.x,-shoulder.y,-shoulder.z);
                for(int j=first;j<first+14;j++)joints[j].mulLocal(arm);
            }
            return;
        }
        if(landing==null)return;
        float w=inversion(age);
        // The hips hold the suspension first. Ribs arch, elbows hang and the tail uncoils after them.
        float arch=(float)Math.sin(Math.PI*w), lag=smooth(age,5,25)*(1-smooth(age,RISE+3,ACID_END));
        var ribs=joints[1].getTranslation(new Vector3f());
        var back=new Matrix4f().translate(ribs).rotateX(-.40F*arch-.14F*w).rotateZ(.14F*arch)
                .translate(-ribs.x,-ribs.y,-ribs.z);
        for(int j=0;j<joints.length;j++)if(j<=1 || j>=5)joints[j].mulLocal(back);
        for(int first:new int[]{5,19}){
            var shoulder=joints[first].getTranslation(new Vector3f());
            var arm=new Matrix4f().translate(shoulder).rotateZ((first==5?1:-1)*(.39F*arch-.27F*lag))
                    .rotateX(.44F*lag).translate(-shoulder.x,-shoulder.y,-shoulder.z);
            for(int j=first;j<first+14;j++)joints[j].mulLocal(arm);
        }
        var tail=joints[3].getTranslation(new Vector3f());
        var sweep=new Matrix4f().translate(tail).rotateZ(-.24F*arch).rotateX(.39F*arch+.12F*w)
                .translate(-tail.x,-tail.y,-tail.z);
        joints[3].mulLocal(sweep);joints[4].mulLocal(sweep);
        Vector3f cheek=new Vector3f(-.15F,.85F,-1.18F);
        joints[0].transformPosition(cheek);
        var root=GoddessRig.root(boss,partial);
        var origin=boss.skinOrigin(partial);
        var inverseRoot=new Matrix4f(root).invert();
        float settle=smooth(age,17,38)*(1-smooth(age,RISE-4,ACID_END));
        float breath=(float)Math.sin((age-27)*.099F);
        var target=inverseRoot.transformPosition(landing.add(0,5.2+.24*breath*settle,0).subtract(origin).toVector3f());
        // Roll at altitude, then glide into reach. The low pass keeps drifting while the face is exposed.
        float descend=smooth(age,6,34)*(1-smooth(age,RISE-1,ACID_END-3));
        var hinge=new Vector3f(cheek).lerp(target,descend);
        hinge.x+=.83F*arch+.17F*settle*(float)Math.sin((age-22)*.065F);
        hinge.y+=arch*3.2F;
        var roll=new Matrix4f().translate(hinge).rotate((float)Math.PI*.92F*w+.03F*settle*breath,.42262F,0,-.90631F)
                .translate(-cheek.x,-cheek.y,-cheek.z);
        for(var m:joints)m.mulLocal(roll);
        // Measured where the crown meets the scalp. Preserve strand length as the suspended mass
        // trails the roll, floats out behind the face, then curls back from the distal joint.
        float trail=smooth(age,3,30)*(1-smooth(age,RISE+2,ACID_END));
        float tipTrail=smooth(age,9,35)*(1-smooth(age,RISE+5,ACID_END));
        var scalp=joints[33].transformPosition(new Vector3f(.70F,-.66F,-.425F));
        var hair=new Matrix4f().translate(scalp).rotate(-2.52F*trail+.055F*breath*settle,.42262F,0,-.90631F)
                .translate(-scalp.x,-scalp.y,-scalp.z);
        joints[33].mulLocal(hair);joints[34].mulLocal(hair);
        var knot=joints[34].getTranslation(new Vector3f());
        var tipCurl=new Matrix4f().translate(knot).rotate(-.12F*tipTrail+.18F*(trail-tipTrail)
                +.045F*settle*(float)Math.sin((age-34)*.099F),.42262F,0,-.90631F)
                .translate(-knot.x,-knot.y,-knot.z);
        joints[34].mulLocal(tipCurl);
        UUID victim=boss.goddess.attacks.victim();
        var player=victim==null?null:boss.level().getPlayerByUUID(victim);
        if(player!=null && w>0){
            var look=inverseRoot.transformPosition(player.getEyePosition(partial).subtract(boss.skinOrigin(partial)).toVector3f()).sub(hinge);
            var front=joints[0].transformDirection(new Vector3f(.421F,-.087F,-.903F));
            double side=(front.z*look.x-front.x*look.z)/(Math.max(.01,Math.hypot(front.x,front.z))*Math.max(1.5,Math.hypot(look.x,look.z)));
            var glance=new Matrix4f().translate(hinge).rotateY((float)(.56*side)*w)
                    .translate(-hinge.x,-hinge.y,-hinge.z);
            joints[0].mulLocal(glance);joints[33].mulLocal(glance);joints[34].mulLocal(glance);
        }
        if(w>0){
            double rise=hoverClearance.lift(boss,joints,root,origin,landing.y,1.05);
            if(rise>0){
                var up=inverseRoot.transformDirection(new Vector3f(0,(float)rise,0));
                for(var m:joints)m.translateLocal(up.x,up.y,up.z);
            }
        }
    }
    private Vec3 floor(Vec3 around,double above,double below){
        var a=around.add(0,above,0);var b=around.add(0,-below,0);
        var hit=wall(a,b);
        return hit.getType()!=HitResult.Type.MISS && hit.getDirection()==Direction.UP?hit.getLocation():null;
    }
    private BlockHitResult wall(Vec3 a,Vec3 b){
        return boss.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
    }
    private static boolean eligible(ServerPlayer player){return player.isAlive() && !player.isSpectator() && !player.isCreative();}
    private void clear(int sequence){serial=sequence;cut=0;landing=null;hits.clear();globs.clear();Arrays.fill(rim,null);}
    private boolean prepare(ServerPlayer target,GoddessAttacks.Move move){
        Vec3 base=boss.position(),opponent=target.position();
        Vec3 direction=new Vec3(opponent.x-base.x,0,opponent.z-base.z).normalize();
        if(direction.lengthSqr()<.1)direction=FRONT;
        // Approach the ground the opponent can actually stand on, even if the cave is excavated.
        Vec3 near=opponent.subtract(direction.scale(2.4));
        for(Vec3 point:List.of(near,opponent,near.add(direction.z*2,0,-direction.x*2),base)){
            landing=floor(point,1.4,12);
            if(landing!=null)break;
        }
        if(landing==null)return false;
        if(move==GoddessAttacks.Move.TENDRIL_ERUPTION){
            double bearing=Math.atan2(direction.z,direction.x);
            for(int i=0;i<BLADES;i++){
                double angle=bearing+2*Math.PI*i/BLADES;
                Vec3 at=new Vec3(base.x+38*Math.cos(angle),landing.y,base.z+38*Math.sin(angle));
                var surface=floor(at,4,10);rim[i]=(surface==null?at:surface).add(0,.75,0);
            }
        }
        return true;
    }
    public boolean tick(ServerLevel level,ServerPlayer target,GoddessAttacks.Move move,int age){
        if(serial!=boss.goddess.attacks.sequence()){
            clear(boss.goddess.attacks.sequence());
            if(!prepare(target,move))return true;
            boss.goddess.publish();
        }
        if(landing==null)return true;
        if(move==GoddessAttacks.Move.INVERTED_ACID){
            if(age==10||age==29)SpiderFoley.play(boss,landing,SpiderFoley.Cue.LOAD,2);
            for(int row=0;row<2;row++)if(age==(row==0?SPIT_A:SPIT_B) && (cut&(1<<row))==0)spit(level,target,row,age);
            if(!globs.isEmpty())fly(level,age);
            if(age==OPEN)SpiderFoley.play(boss,landing,SpiderFoley.Cue.BRACE,2.4F);
            return age>=ACID_END;
        }
        if(age==7||age==24)SpiderFoley.play(boss,base(1),SpiderFoley.Cue.STRAIN,2.5F);
        if(age==BURST)SpiderFoley.play(boss,base(1),SpiderFoley.Cue.RUPTURE,4.4F);
        if(age>=BURST && age<WITHDRAW)strike(level,age);
        if(age==WITHDRAW)SpiderFoley.play(boss,base(1),SpiderFoley.Cue.SCRAPE,2.8F);
        return age>=ERUPTION_END;
    }
    private Matrix4f[] corrections(Matrix4f[] posed,float ticks){
        var corrections=new Matrix4f[35];
        for(int j:new int[]{0,33,34})corrections[j]=new Matrix4f(posed[j]).mul(GoddessRig.idle(j,ticks).invert());
        return corrections;
    }
    private Vec3 mouth(GoddessCrownColonies.Actor a,float partial,Matrix4f[] offsets,Matrix4f root,Vec3 origin){
        return GoddessCrownColonies.position(a,boss.tickCount+partial,colonyFold(a,partial),offsets[a.bone()],root,origin);
    }
    public void addRegions(List<List<HydraContact.Box>> slots,Vec3 origin,Matrix4f root,Matrix4f[] posed,float partial){
        if(!acting() || boss.goddess.attacks.move()!=GoddessAttacks.Move.INVERTED_ACID)return;
        float age=boss.goddess.attacks.age(partial);if(age>=SPIT_B)return;
        var offsets=corrections(posed,boss.tickCount+partial);
        for(var a:GoddessCrownColonies.SPITTERS)if(rowOpen(a.row(),age,cut)){
            var p=mouth(a,partial,offsets,root,origin);
            slots.get(FIRST_ROW+a.row()).add(HydraContact.oriented(p,new Vec3(.42,.38,.42),RIGHT,UP,FRONT));
        }
    }
    public boolean interrupt(int slot){
        int row=slot-FIRST_ROW;
        if(!acting() || boss.goddess.attacks.move()!=GoddessAttacks.Move.INVERTED_ACID || !rowOpen(row,boss.goddess.attacks.age(0),cut))return false;
        cut|=1<<row;SpiderFoley.play(boss,landing,SpiderFoley.Cue.TEAR,2.3F);boss.goddess.publish();return true;
    }
    private void spit(ServerLevel level,ServerPlayer player,int row,int age){
        var root=GoddessRig.root(boss,0);var pose=corrections(GoddessRig.pose(boss,0),boss.tickCount);
        var actors=GoddessCrownColonies.SPITTERS.stream().filter(a->a.row()==row).toList();
        Vec3 aim=player.position().add(0,.75,0),sight=aim.subtract(landing);
        var side=new Vec3(-sight.z,0,sight.x).normalize();
        int volley=5;
        for(int i=0;i<volley;i++){
            var a=actors.get(i*(actors.size()-1)/(volley-1));
            Vec3 src=mouth(a,0,pose,root,boss.skinOrigin(0)),dst=aim.add(side.scale((i-2)*1.45));
            globs.add(new Glob(src,dst,age,row));
            level.sendParticles(ParticleTypes.ITEM_SLIME,src.x,src.y,src.z,5,.13,.12,.13,.035);
        }
        SpiderFoley.play(boss,globs.get(globs.size()-1).from,SpiderFoley.Cue.GULP,2.9F);
        boss.goddess.publish();
    }
    private void fly(ServerLevel level,int age){
        boolean changed=false;
        for(var g:globs){
            if(age<=g.start || age>g.start+g.stopped)continue;
            Vec3 prev=g.point(age-1),next=g.point(age);var terrain=wall(prev,next);
            double distance=prev.distanceToSqr(terrain.getLocation());Vec3 hit=terrain.getType()==HitResult.Type.MISS?null:terrain.getLocation();
            ServerPlayer victim=null;AABB bounds=new AABB(prev,next).inflate(.42);
            for(var player:level.getEntitiesOfClass(ServerPlayer.class,bounds,GoddessCrownCast::eligible)){
                AABB receiver=player.getBoundingBox().inflate(.25);
                Vec3 contact=receiver.contains(prev)?prev:receiver.clip(prev,next).orElse(null);
                if(contact!=null && prev.distanceToSqr(contact)<=distance){distance=prev.distanceToSqr(contact);victim=player;hit=contact;}
            }
            if(hit==null && age==g.start+FLIGHT)hit=next;
            if(hit!=null){
                if(victim!=null)hurt(victim,1<<g.row,GoddessImpact.CROWN_ACID);
                g.stopped=age-g.start;level.sendParticles(ParticleTypes.ITEM_SLIME,hit.x,hit.y,hit.z,13,.3,.15,.3,.055);
                level.playSound(null,hit.x,hit.y,hit.z,net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.HOSTILE,.32F,1.3F+.14F*g.row);
                changed=true;
            }
        }
        if(changed)boss.goddess.publish();
    }
    public Vec3 base(float partial){
        var pose=GoddessRig.pose(boss,partial);
        var root=GoddessRig.root(boss,partial);
        return boss.skinOrigin(partial).add(new Vec3(root.mul(pose[2]).transformPosition(new Vector3f(0,-2,0))));
    }
    /** Shared polyline for impact and shader/vanilla rendering. A solid block terminates a strand. */
    public Vec3[] strand(Vec3 start,int i,float age,boolean clip){
        if(rim[i]==null)return new Vec3[0];
        Vec3 toward=rim[i].subtract(start);var planar=new Vec3(toward.x,0,toward.z).normalize();
        var side=new Vec3(-planar.z,0,planar.x);Vec3 root=start.add(planar.scale(1.4)).add(0,(i%3-1)*.75,0);
        float extend=eruption(age);
        double visible=.047+.953*extend;
        var points=new ArrayList<Vec3>(SEGMENTS+1);points.add(root);
        for(int j=1;j<=SEGMENTS;j++){
            double t=visible*j/SEGMENTS, sag=smooth((float)t,0,.48F);
            var p=new Vec3(root.x+(rim[i].x-root.x)*t,root.y+(rim[i].y-root.y)*sag,root.z+(rim[i].z-root.z)*t)
                    .add(side.scale(Math.sin(Math.PI*2*t+i)*Math.sin(Math.PI*t)*.92))
                    .add(0,Math.sin(Math.PI*t)*(.45+1.8*(1-sag)),0);
            if(clip){
                var solid=wall(points.get(points.size()-1),p);
                if(solid.getType()!=HitResult.Type.MISS){points.add(solid.getLocation());break;}
            }
            points.add(p);
        }
        return points.toArray(Vec3[]::new);
    }
    public static double width(int step){return .73-.48*step/SEGMENTS;}
    private boolean hurt(ServerPlayer player,int mask,GoddessImpact impact){
        int struck=hits.getOrDefault(player.getUUID(),0);if((struck&mask)!=0)return false;
        hits.put(player.getUUID(),struck|mask);
        return player.hurt(boss.damageSources().mobAttack(boss),impact.incoming(player));
    }
    private void strike(ServerLevel level,int age){
        Vec3 core=base(0);
        List<ServerPlayer> targets=level.getEntitiesOfClass(ServerPlayer.class,boss.getBoundingBox().inflate(42,70,42),GoddessCrownCast::eligible);
        if(targets.isEmpty())return;
        for(int i=0;i<BLADES;i++){
            var path=strand(core,i,age,true);
            for(int j=1;j<path.length;j++){
                Vec3 a=path[j-1],b=path[j];double radius=width(j-1);
                for(var player:targets){
                    if((hits.getOrDefault(player.getUUID(),0)&4)!=0)continue;
                    var box=player.getBoundingBox().inflate(radius);
                    if((box.contains(a)||box.clip(a,b).isPresent())&&hurt(player,4,GoddessImpact.TENDRIL_ERUPTION)){
                        var push=b.subtract(a).multiply(1,0,1).normalize().scale(.8);
                        player.push(push.x,.28,push.z);player.hurtMarked=true;
                    }
                }
            }
        }
    }
    private static CompoundTag vec(Vec3 p){var t=new CompoundTag();t.putDouble("X",p.x);t.putDouble("Y",p.y);t.putDouble("Z",p.z);return t;}
    private static Vec3 read(CompoundTag t){return new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
    public CompoundTag save(){
        var t=new CompoundTag();if(!acting())return t;
        t.putInt("Sequence",serial);t.putInt("Cut",cut);
        if(landing!=null)t.put("Landing",vec(landing));
        var ends=new ListTag();for(var v:rim)if(v!=null)ends.add(vec(v));t.put("Rim",ends);
        var shots=new ListTag();
        for(var g:globs){var shot=vec(g.from);shot.put("To",vec(g.to));shot.putInt("Start",g.start);shot.putInt("Row",g.row);shot.putInt("Stop",g.stopped);shots.add(shot);}
        t.put("Shots",shots);
        var ledger=new ListTag();hits.forEach((id,mask)->{var entry=new CompoundTag();entry.putUUID("Player",id);entry.putInt("Mask",mask);ledger.add(entry);});t.put("Hits",ledger);
        return t;
    }
    public void load(CompoundTag t){
        clear(t.contains("Sequence")?t.getInt("Sequence"):-1);cut=t.getInt("Cut");
        if(t.contains("Landing"))landing=read(t.getCompound("Landing"));
        var ends=t.getList("Rim",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(BLADES,ends.size());i++)rim[i]=read(ends.getCompound(i));
        var shots=t.getList("Shots",Tag.TAG_COMPOUND);
        for(int i=0;i<shots.size();i++){var s=shots.getCompound(i);var g=new Glob(read(s),read(s.getCompound("To")),s.getInt("Start"),s.getInt("Row"));g.stopped=s.getInt("Stop");globs.add(g);}
        var ledger=t.getList("Hits",Tag.TAG_COMPOUND);
        for(int i=0;i<ledger.size();i++){var e=ledger.getCompound(i);if(e.hasUUID("Player"))hits.put(e.getUUID("Player"),e.getInt("Mask"));}
    }
}
