package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.animation.RagdollDrive;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Part;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Local damage to the posed receiver. Only the struck skin leaves the paired animation. */
public final class ConsumeTrauma {
    private record Contact(double time,Vec3[] palms,Vec3[] fists,Vec3[] sole,Vec3 knee,Vec3[] forearms,Vec3 chest) {}
    private static final Map<LivingEntity,Contact> CONTACTS=new WeakHashMap<>();
    static Vec3 chest(LivingEntity owner,float partial){
        Contact sample=CONTACTS.get(owner);
        return sample!=null && Math.abs(sample.time-owner.level().getGameTime()-partial)<=1.25?sample.chest:null;
    }
    private record HammerFace(double time,Vec3[] sites) {}
    private static final Map<LivingEntity,HammerFace> HAMMERS=new WeakHashMap<>();
    static void sampleHammer(LivingEntity owner,float partial,Vec3[] sites){
        if(sites.length>0)HAMMERS.put(owner,new HammerFace(owner.level().getGameTime()+partial,sites));
    }
    private double lashTime=Double.NEGATIVE_INFINITY;
    private Vec3[] lashSites=new Vec3[0];
    void sampleLash(double time,Map<String,List<Vec3>> profiles){
        var sites=new ArrayList<Vec3>();
        for(var edge:profiles.values()){
            for(int i=1;i<edge.size();i++){
                var a=edge.get(i-1);var b=edge.get(i);
                int steps=Math.min(16,Math.max(1,(int)Math.ceil(a.distanceTo(b)/.045)));
                for(int j=0;j<=steps;j++)sites.add(a.lerp(b,j/(double)steps));
            }
        }
        if(!sites.isEmpty()){lashTime=time;lashSites=sites.toArray(Vec3[]::new);}
    }
    private int lastHit=-1;
    private int landedBlows;
    private final List<Shard> shards=new ArrayList<>();
    private Part struck;
    private Vec3 localImpact;
    private boolean crushed,groundPulp;
    private double lastTime,impactTime;
    boolean open(){return struck!=null;}
    boolean groundAttachments(){return groundedScene || groundPulp;}
    private boolean groundedScene;
    double settleAfterCrush(double now){return crushed?RagdollDrive.smooth((float)((now-impactTime)/4)):0;}

    private record GripSample(int tick,double time,Vec3[] palms,Vec3[] velocity) {}
    private static final Map<LivingEntity,GripSample> MUSCLE_GRIPS=new WeakHashMap<>();
    static Vec3 musclePalm(LivingEntity owner,int side,float partial){
        var grip=MUSCLE_GRIPS.get(owner);
        return grip!=null && owner.tickCount-grip.tick<=1?grip.palms[side]:null;
    }
    static Vec3 releaseMotion(LivingEntity owner,int side){
        var grip=MUSCLE_GRIPS.get(owner);
        return grip==null || owner.tickCount-grip.tick>1?Vec3.ZERO:grip.velocity[side];
    }
    public static void sampleActor(LivingEntity actor,PoseStack stack,Matrix4f[] bones,Matrix4f[] skin,float partial){
        if(actor==null || ModAttachments.getData(actor, ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME)return;
        var skeleton=PlayerMotionController.skeleton();
        var palms=new Vec3[2];var fists=new Vec3[2];var forearms=new Vec3[10];
        for(int i=0;i<2;i++){
            String side=i==0?"L":"R";
            var wrist=skeleton.searchJointByName("Tool_"+side);
            fists[i]=BlacklightBoneRendering.worldPoint(stack,bones[wrist.getId()].getTranslation(new Vector3f()));
            var elbow=BlacklightBoneRendering.worldPoint(stack,bones[skeleton.searchJointByName("Hand_"+side).getId()].getTranslation(new Vector3f()));
            for(int j=0;j<5;j++)forearms[i*5+j]=elbow.lerp(fists[i],j/4.0);
            var weapon=new Matrix4f(bones[wrist.getId()]).mul(new Matrix4f(wrist.getLocalTransform()).invert());
            var palm=com.codex.blacklight.client.animation.WeaponModelAnchors.get(PowerForm.CLAWS,"palm_"+side,
                    new Vector3f(i==0?.00267F:-.00267F,.32362F,.07666F));
            palms[i]=BlacklightBoneRendering.worldPoint(stack,weapon.transformPosition(palm));
        }
        if(ModAttachments.getData(actor,ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.MUSCLE_MASS){
            var previous=MUSCLE_GRIPS.get(actor);
            var velocity=new Vec3[]{Vec3.ZERO,Vec3.ZERO};
            double now=actor.level().getGameTime()+partial;
            for(int i=0;i<2;i++){
                String side=i==0?"L":"R";
                var hand=bones[skeleton.searchJointByName("Hand_"+side).getId()];
                var grip=com.codex.blacklight.client.animation.WeaponModelAnchors.get(PowerForm.MUSCLE_MASS,
                        "palm_"+side,new Vector3f(0,.66F,0));
                palms[i]=BlacklightBoneRendering.worldPoint(stack,hand.transformPosition(grip));
                if(previous!=null && now>=previous.time && now-previous.time<2){
                    double dt=now-previous.time;
                    velocity[i]=dt>.03?com.codex.blacklight.client.animation.RagdollDrive.limit(
                            palms[i].subtract(previous.palms[i]).scale(1/dt),.7):previous.velocity[i];
                }
            }
            MUSCLE_GRIPS.put(actor,new GripSample(actor.tickCount,now,palms,velocity));
        }
        var leg=skin[skeleton.searchJointByName("Leg_L").getId()];
        var sole=new Vec3[3];for(int i=0;i<3;i++)sole[i]=BlacklightBoneRendering.worldPoint(stack,leg.transformPosition(new Vector3f(-.119F,.001F,(i-1)*.12F)));
        var knee=BlacklightBoneRendering.worldPoint(stack,bones[skeleton.searchJointByName("Leg_R").getId()].getTranslation(new Vector3f()));
        CONTACTS.put(actor,new Contact(actor.level().getGameTime()+partial,palms,fists,sole,knee,forearms,
                BlacklightBoneRendering.worldPoint(stack,bones[skeleton.searchJointByName("Chest").getId()].getTranslation(new Vector3f()))));
    }
    static boolean applies(PowerForm form,int variant){
        return ConsumeVariant.stealth(form,variant) || form==PowerForm.WHIPFIST && variant==ConsumeVariant.CLASSIC || form==PowerForm.HAMMERFISTS || form==PowerForm.MUSCLE_MASS && variant==ConsumeVariant.CLASSIC || form==PowerForm.CLAWS && variant==ConsumeVariant.CLASSIC ||
                form==PowerForm.NONE && (variant==ConsumeVariant.DEFAULT || variant==ConsumeVariant.HEAD_STOMP || variant==ConsumeVariant.KNEE_HEAD || variant==ConsumeVariant.BEATDOWN);
    }
    Vec3 struckPoint(List<Part> parts,Vec3[] positions,float partial){
        int i=struck==null?-1:parts.indexOf(struck);
        if(i<0)return null;
        var rotation=new Quaternionf(struck.oldRotation).slerp(struck.rotation,partial);
        return positions[i].add(rotate(localImpact.subtract(struck.center).scale(struck.scale),rotation));
    }
    boolean hides(Part part){return crushed && part.bind.region()==1;}
    private static Vec3 rotate(Vec3 p,Quaternionf q){return new Vec3(q.transform(p.toVector3f()));}

    void contact(LivingEntity e,ConsumeVisual visual,ConsumeFracture.Phase phase,List<Part> parts,Vec3[] positions,float partial){
        if(!applies(phase.form(),visual.variant()) || !phase.nativeEvents())return;
        var profile=ConsumeReceiverScene.profile(phase.form(),false,visual.variant());
        double now=e.level().getGameTime()+partial;
        int event=-1;
        for(int i=lastHit+1;i<profile.hitSeconds().size();i++){
            double age=visual.elapsed(e.level().getGameTime(),partial)-20*profile.hitSeconds().get(i);
            if(age<0)break;
            if(age<=4)event=i;
        }
        if(event<0)return;
        if(!(e.level().getEntity(visual.consumerId()) instanceof LivingEntity owner))return;
        boolean stomp=visual.variant()==ConsumeVariant.HEAD_STOMP;
        boolean hammer=phase.form()==PowerForm.HAMMERFISTS;
        boolean knee=visual.variant()==ConsumeVariant.KNEE_HEAD;
        boolean punch=visual.variant()==ConsumeVariant.BEATDOWN;
        boolean heart=phase.form()==PowerForm.MUSCLE_MASS;
        boolean hold=ConsumeVariant.stealth(phase.form(),visual.variant());
        boolean lash=phase.form()==PowerForm.WHIPFIST;
        boolean torsoHold=visual.variant()==ConsumeVariant.STEALTH_BEARHUG;
        boolean tear=lash || visual.variant()==ConsumeVariant.STEALTH_JUGULAR;
        groundedScene=stomp||punch;
        boolean finishingPunch=punch && event==profile.hitSeconds().size()-1;
        boolean blunt=knee||punch&&!finishingPunch||heart&&event==1;
        boolean head=stomp||hammer||punch||blunt||phase.form()==PowerForm.CLAWS;
        var pose=CONTACTS.get(owner);
        Vec3[] sites;
        var face=hammer?HAMMERS.get(owner):null;
        if(lash){
            if(Math.abs(now-lashTime)>1.5)return;
            sites=lashSites;
        }else if(hammer && face!=null && Math.abs(now-face.time)<1.5)sites=face.sites;
        else if(!hammer && pose!=null && now>=pose.time-.01 && now-pose.time<2){
            if(hold)sites=pose.forearms;
            else if(heart)sites=new Vec3[]{pose.palms[0]};
            else if(knee)sites=new Vec3[]{pose.knee};
            else if(punch)sites=new Vec3[]{pose.fists[ConsumeReceiverScene.beatdownRightHand(event)?1:0]};
            else sites=stomp?pose.sole:head?pose.palms:new Vec3[]{pose.fists[0]};
        }
        else {
            // First-person and culled actors still have the authored striking socket.
            var cue=ConsumePlacementTrack.impactSocket(owner,visual,partial);
            if(cue==null)return;
            sites=new Vec3[]{cue};
        }
        int region=head || hold&&!torsoHold?1:32;
        var hit=nearest(parts,positions,sites,region,partial);
        double contactRadius=blunt||punch||hold?.14:.065;
        if(hit==null || hit.distanceSquared()>contactRadius*contactRadius)return;
        lastHit=event;
        struck=hit.part();localImpact=hit.local();lastTime=impactTime=now;crushed=head&&!blunt;
        Vec3 world=hit.world();
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing());
        Vec3 jet=stomp?new Vec3(0,.22,0).add(forward.scale(-.32)).normalize():forward;
        if(blunt||hold||lash){
            var center=positions[parts.indexOf(struck)];
            Vec3 incoming=center.subtract(world).normalize();
            if(incoming.lengthSqr()<.5)incoming=forward;
            var turn=new Quaternionf(struck.oldRotation).slerp(struck.rotation,partial);
            if(punch){
                chipHead(parts,positions,world,incoming,Math.min(4,++landedBlows),e.getBbWidth(),partial);
            }else if(blunt){
                // One physical press crosses the head, its outer skin and any
                // overlapping layers. Each layer keeps its own posed bind frame.
                double radius=Math.min(.5,Math.max(.12,e.getBbWidth()*.68));
                for(int i=0;i<parts.size();i++){
                    var part=parts.get(i);
                    if(part.bind.region()!=region || part.scale<.5F)continue;
                    var inverse=new Quaternionf(part.oldRotation).slerp(part.rotation,partial).conjugate();
                    Vec3 centerLocal=part.center.add(rotate(world.subtract(positions[i]),inverse).scale(1/part.scale));
                    double localRadius=radius/part.scale;
                    if(AdaptiveRagdoll.bounds(part.mesh).distanceToSqr(centerLocal)<localRadius*localRadius)
                        part.press(centerLocal,rotate(incoming,inverse),localRadius,now,part==struck);
                }
            }else struck.imprintContact(localImpact,rotate(incoming,new Quaternionf(turn).conjugate()),
                    tear?FatalWeaponImpact.WHIP:FatalWeaponImpact.FIST);
            if(punch)struck.velocity=struck.velocity.add(incoming.scale(.075));
            if(tear)WoundBlood.rupture(e,region,world,incoming.scale(-1),false);
            else if(!hold || torsoHold)WoundBlood.blunt(e,world,incoming.scale(-1));
        }else if(head){
            // Only compress material against a real collider. An overhead
            // hammer or a head caught in the air still breaks freely.
            var floor=e.level().clip(new ClipContext(world.add(0,.05,0),world.add(0,-.8,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            groundPulp=(finishingPunch||stomp||hammer) && floor.getType()==HitResult.Type.BLOCK;
            double pavement=groundPulp?floor.getLocation().y:Double.NaN;
            breakHead(parts,positions,world,pavement,stomp||groundPulp,partial);
            var stump=nearest(parts,positions,new Vec3[]{world},32,partial);
            if(stump!=null){struck=stump.part();localImpact=stump.local();}
        }else{
            Vec3 direction=world.subtract(owner.getPosition(partial).add(0,owner.getBbHeight()*.65,0)).normalize();
            if(direction.lengthSqr()<.5)direction=forward;
            if(ConsumePlacementTrack.villagerReceiver(e)){
                var turn=new Quaternionf(struck.oldRotation).slerp(struck.rotation,partial);
                direction=rotate(new Vec3(0,0,1),turn.mul(struck.bind.rotation())).normalize();
                var chest=AdaptiveRagdoll.villagerGrip(e,0,partial);
                if(chest!=null)world=chest;
            }
            jet=direction;
            for(int i=0;i<parts.size();i++){
                var p=parts.get(i);if(p.bind.region()!=32)continue;
                var rotation=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
                var inv=new Quaternionf(rotation).conjugate();
                Vec3 center=p.center.add(rotate(world.subtract(positions[i]),inv).scale(1/p.scale));
                if(center.distanceTo(p.center)>p.half.length()+.32)continue;
                puncture(p,center,rotate(direction,inv),Math.min(.20,Math.max(.085,e.getBbWidth()*.25)),p==hit.part());
            }
        }
        com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Consume impact form={} variant={} age={} region={} gap={} shards={}",
                phase.form(),visual.variant(),now-visual.started(),hit.part().bind.region(),Math.sqrt(hit.distanceSquared()),shards.size());
        if(!blunt&&!hold&&!lash)WoundBlood.rupture(e,head?1:32,world,jet,head);
    }

    private record SurfaceHit(Part part,Vec3 local,Vec3 world,double distanceSquared){}
    private static SurfaceHit nearest(List<Part> parts,Vec3[] positions,Vec3[] sites,int region,float partial){
        double best=Double.POSITIVE_INFINITY;
        Vec3 local=null,world=null;Part hit=null;
        for(int i=0;i<parts.size();i++){
            Part p=parts.get(i);if(p.bind.region()!=region || p.scale<.5)continue;
            var rotation=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
            var inverse=new Quaternionf(rotation).conjugate();
            for(Vec3 site:sites){
                Vec3 near=p.center.add(rotate(site.subtract(positions[i]),inverse).scale(1/p.scale));
                var skin=p.visibleSkin();
                for(int j=0;j+3<skin.size();j+=4)for(int tri=1;tri<=2;tri++){
                    Vec3 at=SweptBladeSurface.closest(near,p.skinPoint(skin.get(j)),p.skinPoint(skin.get(j+tri)),p.skinPoint(skin.get(j+tri+1)));
                    double d=at.distanceToSqr(near)*p.scale*p.scale;
                    if(d<best){best=d;hit=p;local=at;world=positions[i].add(rotate(at.subtract(p.center).scale(p.scale),rotation));}
                }
            }
        }
        return hit==null?null:new SurfaceHit(hit,local,world,best);
    }

    private void chipHead(List<Part> parts,Vec3[] positions,Vec3 impact,Vec3 inward,int count,double width,float partial){
        double radius=Math.min(.28,Math.max(.09,width*(.18+.035*count)));
        var lining=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        for(int i=0;i<parts.size();i++){
            var part=parts.get(i);
            if(part.bind.region()!=1 || part.scale<.5)continue;
            var rotation=new Quaternionf(part.oldRotation).slerp(part.rotation,partial);
            var inverse=new Quaternionf(rotation).conjugate();
            Vec3 local=part.center.add(rotate(impact.subtract(positions[i]),inverse).scale(1/part.scale));
            double r=radius/part.scale;
            if(AdaptiveRagdoll.bounds(part.mesh).distanceToSqr(local)>4*r*r)continue;
            var cut=ImpactChip.carve(part.mesh,local,rotate(inward,inverse).normalize(),r,part==struck?count:0,lining);
            if(!cut.cut())continue;
            part.mesh.clear();
            part.mesh.addAll(cut.skin());
            part.meshRevision++;
            for(int j=0;j<cut.chips().size();j++){
                var mesh=cut.chips().get(j);
                Vec3 pivot=AdaptiveRagdoll.bounds(mesh).getCenter();
                Vec3 origin=positions[i].add(rotate(pivot.subtract(part.center).scale(part.scale),rotation));
                Vec3 outward=origin.subtract(impact).subtract(inward.scale(radius));
                double angle=(j+.5)*2*Math.PI/count+count*.87;
                Vec3 kick=new Vec3(outward.x,0,outward.z).normalize().scale(.09)
                        .add(.065*Math.cos(angle),.095+.014*(j%3),.065*Math.sin(angle));
                shards.add(new Shard(mesh,pivot,origin,rotation,kick,(j%2==0?1:-1)*.17,part.scale,false));
            }
        }
    }

    private void breakHead(List<Part> parts,Vec3[] positions,Vec3 impact,double pavement,boolean stomp,float partial){
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i);if(p.bind.region()!=1)continue;
            var rotation=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
            var inv=new Quaternionf(rotation).conjugate();
            Vec3 vertical=rotate(new Vec3(.16,1,-.08).normalize(),inv);
            Vec3 side=rotate(new Vec3(.90,-.12,.36).normalize(),inv);
            for(int s:new int[]{-1,1}){
                var half=AdaptiveRagdoll.traumaClip(p,p.mesh,p.center,side,s);
                for(int t:new int[]{-1,1}){
                    var piece=AdaptiveRagdoll.traumaClip(p,half,p.center,vertical,t);if(piece.size()<4)continue;
                    Vec3 middle=AdaptiveRagdoll.bounds(piece).getCenter();
                    Vec3 pos=positions[i].add(rotate(middle.subtract(p.center).scale(p.scale),rotation));
                    Vec3 outward=pos.subtract(positions[i]).normalize().add(pos.subtract(impact).normalize().scale(.28));
                    Vec3 radial=new Vec3(outward.x,s*.035+t*.03,outward.z).normalize();
                    Vec3 kick=radial.scale(stomp?.145:.125).add(s*.022,stomp?.055:.10,t*.025);
                    if(groundPulp){
                        // Compression and lateral spread happen on the impact surface.
                        // Preserve the faceted outline rather than stacking red sheets in air.
                        pos=new Vec3(pos.x,pavement+.018+(t+1)*.005,pos.z);
                        kick=new Vec3(radial.x*.045,0,radial.z*.045);
                    }
                    shards.add(new Shard(piece,middle,pos,rotation,kick,s*(.09+t*.025),p.scale,groundPulp));
                }
            }
        }
    }
    private static final class Shard {
        final List<Vertex> mesh;final Vec3 pivot;final Quaternionf initial;final double spin,scale;final boolean pulp;
        Vec3 at,velocity;double angle;
        Shard(List<Vertex> mesh,Vec3 pivot,Vec3 at,Quaternionf rot,Vec3 velocity,double spin,double scale,boolean pulp){
            this.mesh=mesh;this.pivot=pivot;this.at=at;this.initial=new Quaternionf(rot);this.velocity=velocity;this.spin=spin;this.scale=scale;this.pulp=pulp;
        }
    }
    void fragments(LivingEntity e,double now,float flow,Vec3 sink,MultiBufferSource buffers,Matrix4f matrix,Matrix3f normals,Vec3 offset,int light){
        if(shards.isEmpty())return;
        double dt=Math.max(0,Math.min(2,now-lastTime));lastTime=now;
        for(var s:shards){
            s.velocity=flow>.04?RagdollDrive.pull(s.at,s.velocity,sink,flow,dt):s.velocity.add(0,-.034*dt,0);
            Vec3 to=s.at.add(s.velocity.scale(dt));
            double radius=(s.pulp?.024:.055)*s.scale*(1-flow);
            var floor=e.level().clip(new ClipContext(s.at.add(0,radius,0),to.add(0,-radius,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(floor.getType()==HitResult.Type.BLOCK && flow<.3){
                to=floor.getLocation().add(Vec3.atLowerCornerOf(floor.getDirection().getNormal()).scale(radius));
                s.velocity=s.velocity.multiply(.57,0,.57);
            }else s.angle+=s.spin*dt;
            s.at=to;
            var rot=new Quaternionf(s.initial).rotateXYZ((float)s.angle,(float)(s.angle*.67),0);
            double scale=s.scale*(1-flow)*.67;
            for(var v:s.mesh){
                Vec3 local=rotate(v.p.subtract(s.pivot).scale(scale),rot);
                if(s.pulp)local=local.multiply(1.22,.105,1.2);
                Vec3 pos=to.add(local).subtract(offset);
                Vec3 n=new Vec3(normals.transform(rotate(v.n,rot).toVector3f())).normalize();
                var material=s.pulp?net.minecraft.client.renderer.RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE):v.type;
                int color=s.pulp?(s.spin>0?0xff6a202a:0xff923339):v.color;
                v.emit(buffers.getBuffer(material),new Vec3(matrix.transformPosition(pos.toVector3f())),n,color,v.overlay,light);
            }
        }
    }
    void gatheringFragments(Map<Integer,ConsumeTendrils.Attachment> roots,Vec3 offset,float flow){
        if(flow<=.05F || flow>=.9F || shards.size()<2)return;
        // Two opposing pieces stay individually tethered as the larger fragments return.
        for(int i=0;i<2;i++){
            var shard=shards.get(i==0?0:shards.size()/2);
            roots.put(-1-i,new ConsumeTendrils.Attachment(shard.at.subtract(offset),.065*(1-flow)));
        }
    }
    // Subtract a hexagonal tunnel from every surface, including front, back and clothing.
    private static void puncture(Part p,Vec3 entry,Vec3 axis,double radius,boolean lining){
        Vec3 u=axis.cross(Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),v=axis.cross(u);
        var kept=new ArrayList<Vertex>();
        for(int j=0;j+3<p.mesh.size();j+=4){
            List<Vertex> inside=p.mesh.subList(j,j+4);
            for(int k=0;k<6 && !inside.isEmpty();k++){
                double a=k*Math.PI/3;Vec3 n=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                var out=new ArrayList<Vertex>();var next=new ArrayList<Vertex>();
                var prev=inside.get(inside.size()-1);double before=prev.p.subtract(entry).dot(n)-radius;
                for(var cur:inside){double after=cur.p.subtract(entry).dot(n)-radius;
                    if((before>0)!=(after>0)){var mid=prev.mix(cur,before/(before-after));out.add(mid);next.add(mid);}
                    (after>0?out:next).add(cur);prev=cur;before=after;}
                for(int t=1;t+1<out.size();t++){kept.add(out.get(0));kept.add(out.get(t));kept.add(out.get(t+1));kept.add(out.get(t+1));}
                inside=next;
            }
            // Trace the actual clipped face, including the outer robe and far
            // panel. Recess the rim into the opening so it cannot hang off the skin.
            if(inside.size()>=3){
                Vec3 center=Vec3.ZERO;
                for(var corner:inside)center=center.add(corner.p);
                center=center.scale(1.0/inside.size());
                for(int i=0;i<inside.size();i++){
                    Vec3 a=inside.get(i).p,b=inside.get((i+1)%inside.size()).p;
                    if(a.distanceToSqr(b)<1e-9)continue;
                    tissue(kept,p,a,b,b.lerp(center,.20),a.lerp(center,.20),i%2==0?0xff9b3642:0xff692132);
                }
            }
        }
        if(lining){
            double near=Double.POSITIVE_INFINITY,far=Double.NEGATIVE_INFINITY;
            for(var vertex:p.mesh){
                double t=vertex.p.subtract(entry).dot(axis);
                near=Math.min(near,t);far=Math.max(far,t);
            }
            entry=entry.add(axis.scale(near));
            double depth=far-near;
            for(int k=0;k<6;k++){
                double a=(k+.5)*Math.PI/3,b=a+Math.PI/3;
                Vec3 first=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))),second=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
                double r=radius/Math.cos(Math.PI/6);
                Vec3 front=entry.add(axis.scale(.012)),back=entry.add(axis.scale(depth-.008));
                tissue(kept,p,front.add(first.scale(r)),front.add(second.scale(r)),back.add(second.scale(r)),back.add(first.scale(r)),k%2==0?0xff721a29:0xff4b101d);
            }
            // Two curved folds leave a clear line through the torn chest.
            for(int k=-1;k<=1;k+=2){
                Vec3 prior=entry.add(u.scale(k*radius*.82));
                for(int j=1;j<=5;j++){double t=j/5.0;
                    Vec3 next=entry.add(u.scale(k*radius*(.82-.42*Math.sin(t*Math.PI)))).add(v.scale(-radius*.65*Math.sin(t*Math.PI))).add(axis.scale(depth*t-radius*.4*Math.sin(t*Math.PI)));
                    Vec3 w=u.scale(radius*.19);tissue(kept,p,prior.subtract(w),next.subtract(w),next.add(w),prior.add(w),j%2==0?0xffa73e4d:0xff862a39);prior=next;
                }
            }
        }
        p.mesh.clear();p.mesh.addAll(kept);p.meshRevision++;
    }
    private static void tissue(List<Vertex> mesh,Part p,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
        var tissue=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        for(Vec3 point:List.of(a,b,c,d))mesh.add(new Vertex(p.key,tissue,point,normal,.5F,.5F,color,OverlayTexture.NO_OVERLAY,0));
    }
}
