package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Server-owned connected live body. Solver units are MC tuning, not decoded P2 constants.
 * The same synchronized local transforms drive Gecko, wounds and native hit surfaces.
 * Death/consumption transfer the last skin to the existing mesh-cut body. */
public final class FlyerPhysics {
    private final EncounterMob mob;
    private final List<Part> parts=new ArrayList<>();
    private final List<Joint> joints=new ArrayList<>();
    private final Map<Integer,Part> bones=new HashMap<>();
    private Part root;
    private Vec3 carrierOffset=Vec3.ZERO;
    private Vec3 anchor=Vec3.ZERO;
    private float yaw;
    private long expires,freezeUntil,recovery=-1;
    private boolean controlled,landed;
    private Vec3 floorPoint;
    private float[][] recoveryPose;
    private CompoundTag cachedSnapshot;
    private float cachedPartial=Float.NaN;
    private long cachedAt;
    private Vec3 cachedOrigin;
    public FlyerPhysics(EncounterMob mob){this.mob=mob;}
    public static boolean ownsLive(LivingEntity e){return e instanceof EncounterMob m&&m.kind()==EncounterKind.FLYER&&e.isAlive()&&!ModAttachments.peekData(e,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active();}
    public boolean active(){return !parts.isEmpty();}
    public static boolean hasPose(EncounterMob m){return m.flyerBodyData().contains("pose");}
    /** Pose and root position share one snapshot clock, independently of vanilla's
     * three-tick entity interpolation. Mixing those clocks buries recovering wings. */
    public static Vec3 origin(EncounterMob m,float partial){
        if(!hasPose(m))return m.getPosition(partial);
        return m.flyerPhysics().interpolatedOrigin(partial);
    }
    private Vec3 interpolatedOrigin(float partial){
        var tag=mob.flyerBodyData();long tick=mob.level().getGameTime();
        if(tag==cachedSnapshot&&partial==cachedPartial&&tick==cachedAt)return cachedOrigin;
        float weight=Math.max(0,Math.min(1,mob.level().isClientSide?partial:partial+1));
        var origin=new Vec3(tag.getDouble("px"),tag.getDouble("py"),tag.getDouble("pz")).lerp(new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z")),weight);
        var pose=sample(mob,partial);float facing=tag.getFloat("yaw");var body=FlyerContact.body(pose,Vec3.ZERO,facing);
        AABB bounds=body.get(0).box().bounds;for(var surface:body)bounds=bounds.minmax(surface.box().bounds);
        var terrain=new ArrayList<AABB>();for(var shape:mob.level().getBlockCollisions(mob,bounds.move(origin).inflate(.1)))for(var box:shape.toAabbs())terrain.add(box.move(origin.scale(-1)));
        terrain=coalesce(terrain);
        Vec3 offset=Vec3.ZERO;
        // Two collision-free endpoints can still put a rotating wing through a
        // slab between ticks. Project the interpolated connected body once for
        // both drawing and fractional weapon/projectile contact queries.
        for(int pass=0;pass<8;pass++){
            Vec3 correction=Vec3.ZERO;
            for(var surface:body)for(var shape:terrain){var shift=surface.box().separation(shape);if(shift!=null&&shift.lengthSqr()>correction.lengthSqr())correction=shift;}
            if(correction.lengthSqr()<1e-12)break;offset=offset.add(correction);body=FlyerContact.body(pose,offset,facing);
        }
        cachedSnapshot=tag;cachedPartial=partial;cachedAt=tick;return cachedOrigin=origin.add(offset);
    }
    public static float[][] sample(EncounterMob m,float partial){
        var tag=m.flyerBodyData();var now=unpack(tag.getIntArray("pose"));var old=unpack(tag.getIntArray("previous"));
        if(now==null)return null;if(old==null)old=now;
        float t=m.level().isClientSide?partial:partial+1;t=Math.max(0,Math.min(1,t));
        if(t==0)return old;if(t==1)return now;
        return FlyerContact.blend(old,now,t);
    }
    private static int[] pack(float[][] pose){var data=new int[pose.length*6];for(int i=0;i<pose.length;i++)for(int j=0;j<6;j++)data[i*6+j]=Float.floatToIntBits(pose[i][j]);return data;}
    private static float[][] unpack(int[] data){int n=FlyerMotion.table().bones.length;if(data.length!=n*6)return null;var pose=new float[n][6];for(int i=0;i<n;i++)for(int j=0;j<6;j++){float v=Float.intBitsToFloat(data[i*6+j]);if(!Float.isFinite(v))return null;pose[i][j]=v;}return pose;}
    private static Vec3 vec(Vector3f p){return new Vec3(p.x,p.y,p.z);}
    private static Vec3 rotate(Vec3 p,Quaternionf q){return vec(q.transform(p.toVector3f()));}
    private static Vec3 limit(Vec3 v,double max){return v.lengthSqr()>max*max?v.scale(max/v.length()):v;}
    private Matrix4f base(Vec3 origin){var local=origin.subtract(anchor);return new Matrix4f().translation((float)local.x,(float)(local.y+.01),(float)local.z).rotateY((float)Math.toRadians(180-yaw));}
    private static final class Part {
        final FlyerContact.BodyMass mass;final double inverse;
        Vec3 pos,velocity=Vec3.ZERO,spin=Vec3.ZERO;Quaternionf rotation;
        Part(FlyerContact.BodyMass mass,Matrix4f matrix){this.mass=mass;inverse=1/Math.max(.035,mass.volume());pos=vec(matrix.transformPosition(mass.center().toVector3f()));rotation=matrix.getUnnormalizedRotation(new Quaternionf()).normalize();}
        Matrix4f matrix(){return new Matrix4f().translation((float)pos.x,(float)pos.y,(float)pos.z).rotate(rotation).translate(mass.center().scale(-1).toVector3f());}
    }
    private static final class Joint {
        final Part parent,child;final Vec3 a,b;final Quaternionf rest;
        Joint(Part p,Part c,Vec3 pivot){parent=p;child=c;a=rotate(pivot.subtract(p.pos),new Quaternionf(p.rotation).conjugate());b=rotate(pivot.subtract(c.pos),new Quaternionf(c.rotation).conjugate());rest=new Quaternionf(p.rotation).conjugate().mul(c.rotation).normalize();}
        void solve(){
            var ra=rotate(a,parent.rotation);var rb=rotate(b,child.rotation);var error=limit(child.pos.add(rb).subtract(parent.pos.add(ra)),.15);
            double w=parent.inverse/(parent.inverse+child.inverse);parent.pos=parent.pos.add(error.scale(w*.8));child.pos=child.pos.add(error.scale(-(1-w)*.8));
            torque(parent,ra,error);torque(child,rb,error.scale(-1));
            var desired=new Quaternionf(parent.rotation).mul(rest);var relative=new Quaternionf(desired).conjugate().mul(child.rotation).normalize();
            double angle=2*Math.acos(Math.max(0,Math.min(1,Math.abs(relative.w))));
            if(angle>1.6)child.rotation.slerp(desired,(float)Math.min(.18,(angle-1.6)*.15));
        }
        void close(){child.pos=parent.pos.add(rotate(a,parent.rotation)).subtract(rotate(b,child.rotation));}
        private static void torque(Part p,Vec3 arm,Vec3 error){var axis=arm.cross(error);double n=axis.length();if(n<1e-7)return;p.rotation.premul(new Quaternionf().rotationAxis((float)Math.min(.045,n/Math.max(.02,arm.lengthSqr())*.1),axis.scale(1/n).toVector3f())).normalize();}
    }
    public void impact(Vec3 velocity,Vec3 point,int ticks,int freeze){
        if(mob.level().isClientSide||!ownsLive(mob))return;
        if(!active()){
            anchor=mob.position();yaw=mob.yBodyRot;var pose=mob.flyerPose(0);var world=FlyerMotion.table().transforms(pose);var base=base(mob.position());
            for(var mass:FlyerContact.masses()){var p=new Part(mass,new Matrix4f(base).mul(world[mass.bone()]));parts.add(p);bones.put(mass.bone(),p);}
            root=parts.stream().max(Comparator.comparingDouble(p->p.mass.volume())).orElseThrow();
            for(var p:parts){int parent=FlyerMotion.table().parents[p.mass.bone()];while(parent>=0&&!bones.containsKey(parent))parent=FlyerMotion.table().parents[parent];
                if(parent>=0){float[] pivot=FlyerMotion.table().pivots[p.mass.bone()];var at=vec(new Matrix4f(base).mul(world[p.mass.bone()]).transformPosition(new Vector3f(pivot[0]/16,pivot[1]/16,pivot[2]/16)));joints.add(new Joint(bones.get(parent),p,at));}
            }
            carrierOffset=root.pos;publish(pose,pose);landed=false;
        }
        recovery=-1;controlled=ImpactControl.stunned(mob);long now=mob.level().getGameTime();expires=now+Math.max(12,Math.min(80,ticks));freezeUntil=now+Math.max(0,Math.min(8,freeze));
        for(var p:parts)p.velocity=limit(p.velocity.scale(.3).add(velocity),3);
        var localPoint=point.subtract(anchor);
        Part struck=parts.stream().min(Comparator.comparingDouble(p->p.pos.distanceToSqr(localPoint))).orElse(root);
        var kick=limit(velocity.scale(.65),.7);struck.velocity=limit(struck.velocity.add(kick),3);
        struck.spin=limit(struck.spin.add(localPoint.subtract(struck.pos).cross(kick).scale(.6/Math.max(.05,struck.mass.radius()*struck.mass.radius()))),.35);
        mob.fallDistance=0;mob.setDeltaMovement(Vec3.ZERO);mob.setOnGround(false);
    }
    private void publish(float[][] previous,float[][] pose){
        var old=mob.flyerBodyData();var tag=new CompoundTag();tag.putIntArray("previous",pack(previous));tag.putIntArray("pose",pack(pose));tag.putFloat("yaw",yaw);tag.putLong("tick",mob.level().getGameTime());
        tag.putDouble("px",old.contains("pose")?old.getDouble("x"):mob.getX());tag.putDouble("py",old.contains("pose")?old.getDouble("y"):mob.getY());tag.putDouble("pz",old.contains("pose")?old.getDouble("z"):mob.getZ());
        tag.putDouble("x",mob.getX());tag.putDouble("y",mob.getY());tag.putDouble("z",mob.getZ());mob.flyerBodyData(tag);
    }
    /** Convert absolute rigid transforms back into the existing authored hierarchy. */
    private float[][] pose(Vec3 origin){
        var table=FlyerMotion.table();var pose=new float[table.bones.length][6];var world=new Matrix4f[table.bones.length];var inverse=new Matrix4f(base(origin)).invert();
        for(int i=0;i<pose.length;i++){
            var p=bones.get(i);world[i]=p==null?(table.parents[i]<0?new Matrix4f():new Matrix4f(world[table.parents[i]])):new Matrix4f(inverse).mul(p.matrix());
            var local=table.parents[i]<0?new Matrix4f(world[i]):new Matrix4f(world[table.parents[i]]).invert().mul(world[i]);
            var q=local.getUnnormalizedRotation(new Quaternionf()).normalize();float x=q.x,y=q.y,z=q.z,w=q.w;
            pose[i][0]=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));pose[i][1]=(float)Math.asin(Math.max(-1,Math.min(1,2*(w*y-z*x))));pose[i][2]=(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));
            float[] bind=table.pivots[i];var pivot=new Vector3f(bind[0]/16,bind[1]/16,bind[2]/16);var shift=local.getTranslation(new Vector3f()).sub(pivot).add(q.transform(new Vector3f(pivot))).mul(16);
            pose[i][3]=shift.x;pose[i][4]=shift.y;pose[i][5]=shift.z;
        }return pose;
    }
    private List<AABB> terrain(){var at=anchor.add(root.pos);var bounds=new AABB(at,at).inflate(4);var shapes=new ArrayList<AABB>();for(var shape:mob.level().getBlockCollisions(mob,bounds))for(var box:shape.toAabbs())shapes.add(box.move(anchor.scale(-1)));return coalesce(shapes);}
    /** Remove internal coplanar voxel seams, never fill holes or staircase gaps.
     * Otherwise a wing straddling two floor blocks can be pushed horizontally
     * out of each block in turn instead of resting on their shared top face. */
    static ArrayList<AABB> coalesce(List<AABB> input){
        var boxes=new ArrayList<>(input);boolean changed;
        do{changed=false;outer:for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++){
            var a=boxes.get(i);var b=boxes.get(j);
            for(var axis:net.minecraft.core.Direction.Axis.values()){
                boolean same=true;for(var other:net.minecraft.core.Direction.Axis.values())if(other!=axis&&(Math.abs(a.min(other)-b.min(other))>1e-7||Math.abs(a.max(other)-b.max(other))>1e-7)){same=false;break;}
                if(same&&a.min(axis)<=b.max(axis)+1e-7&&b.min(axis)<=a.max(axis)+1e-7){boxes.set(i,a.minmax(b));boxes.remove(j);changed=true;break outer;}
            }
        }}while(changed);return boxes;
    }
    private boolean collision(Part p,List<AABB> terrain){boolean floor=false;
        Vec3 correction=Vec3.ZERO;
        for(var box:FlyerContact.worldBoxes(p.mass.bone(),p.matrix()))for(var shape:terrain){var shift=box.separation(shape);if(shift==null)continue;
            if(shift.lengthSqr()>correction.lengthSqr())correction=shift;
            if(shift.normalize().y>.65){floor=true;var at=box.contact(shape);floorPoint=new Vec3(at.x,shape.maxY,at.z);}
        }
        if(correction.lengthSqr()>0){p.pos=p.pos.add(correction);var normal=correction.normalize();double speed=p.velocity.dot(normal);if(speed<0)p.velocity=p.velocity.subtract(normal.scale(speed*1.08)).scale(.88);p.spin=p.spin.scale(.72);}
        return floor;
    }
    private void closeAndSeparate(List<AABB> terrain){
        for(var j:joints)j.close();
        // Keep all joints connected after terrain projection. A collision lifts
        // the connected body, never leaves wings at unrelated render positions.
        for(int pass=0;pass<4;pass++){
            Vec3 correction=Vec3.ZERO;
            for(var p:parts)for(var box:FlyerContact.worldBoxes(p.mass.bone(),p.matrix()))for(var shape:terrain){var shift=box.separation(shape);if(shift!=null&&shift.lengthSqr()>correction.lengthSqr())correction=shift;}
            if(correction.lengthSqr()<1e-12)break;for(var p:parts)p.pos=p.pos.add(correction);
        }
    }
    public boolean tick(){
        if(!active()||mob.level().isClientSide)return false;
        if(!mob.isAlive()){parts.clear();joints.clear();bones.clear();return false;} // retain last skin for lethal capture
        if(ModAttachments.peekData(mob,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active()||mob.isPassenger()){clear();return false;}
        var previous=sample(mob,0);if(previous==null)previous=pose(mob.position());
        long now=mob.level().getGameTime();var reaction=ModAttachments.peekData(mob,ModAttachments.HIT_REACTION,HitReaction.EMPTY);
        mob.setYRot(yaw);mob.yBodyRot=mob.yBodyRotO=yaw;mob.yHeadRot=yaw;mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        if(now<=freezeUntil){publish(previous,previous);return true;}
        if(recovery<0&&(reaction.kind()==HitReaction.GETUP&&reaction.duration()>0||controlled&&!ImpactControl.stunned(mob)||now>expires+40)){recovery=now;recoveryPose=previous;}
        if(recovery>=0){
            float weight=Math.min(1,(now-recovery+1)/16F);weight=weight*weight*(3-2*weight);
            var target=mob.flyerSourcePose(0);var blended=FlyerContact.blend(recoveryPose,target,weight);var origin=mob.position();var shapes=terrain();
            for(int pass=0;pass<4;pass++){Vec3 correction=Vec3.ZERO;for(var surface:FlyerContact.body(blended,origin.subtract(anchor),yaw))for(var shape:shapes){var shift=surface.box().separation(shape);if(shift!=null&&shift.lengthSqr()>correction.lengthSqr())correction=shift;}if(correction.lengthSqr()<1e-12)break;origin=origin.add(correction);}
            mob.setPos(origin);publish(previous,blended);if(now-recovery>=16){clear();return false;}return true;
        }
        var shapes=terrain();boolean floor=false;double falling=root.velocity.y;floorPoint=null;
        for(int sub=0;sub<4;sub++){
            for(var p:parts){p.velocity=limit(p.velocity.add(0,-.02,0).scale(.995),3);p.pos=p.pos.add(p.velocity.scale(.25));double angle=p.spin.length();if(angle>1e-7)p.rotation.premul(new Quaternionf().rotationAxis((float)(angle*.25),p.spin.scale(1/angle).toVector3f())).normalize();p.spin=p.spin.scale(.985);}
            for(int iteration=0;iteration<6;iteration++){for(var j:joints)j.solve();for(var p:parts)floor|=collision(p,shapes);}
            closeAndSeparate(shapes);
        }
        var origin=anchor.add(root.pos).subtract(carrierOffset);mob.setPos(origin);mob.setOnGround(floor);publish(previous,pose(origin));
        if(floor&&!landed&&falling<-.12&&floorPoint!=null){
            landed=true;var at=anchor.add(floorPoint);var hit=mob.level().clip(new net.minecraft.world.level.ClipContext(at.add(0,.04,0),at.add(0,-.04,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP)TerrainImpactEffects.emit(mob,hit,.7F,false);
        }
        if(!controlled&&now>=expires){recovery=now;recoveryPose=pose(origin);}return true;
    }
    private void clear(){parts.clear();joints.clear();bones.clear();recovery=-1;mob.flyerBodyData(new CompoundTag());mob.setDeltaMovement(Vec3.ZERO);}
}
