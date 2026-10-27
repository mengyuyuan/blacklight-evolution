package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueVitalMotion;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Procedural tissue on posed native parts, with legacy assets as a compatibility fallback. */
public final class InfectedMorphology implements AutoCloseable {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/infected_morphology.png");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final ThreadLocal<InfectedMorphology> ACTIVE = new ThreadLocal<>();
    private static final Map<EntityModel<?>,Map<String,Map<ModelPart,Attachment>>> CACHE = new WeakHashMap<>();
    private static final JsonObject DEFINITIONS = definitions();
    private record Bounds(float x,float y,float z,float w,float h,float d) {
        float volume(){return w*h*d;}
    }
    private record Box(float x,float y,float z,float w,float h,float d,int material) { }
    private record Attachment(int anatomy,List<Box> boxes,InfectedBite wound) { }
    private record Pending(Attachment attachment,PoseStack.Pose pose,int light,int overlay,int color) { }
    private final LivingEntity proxy;
    private final BlacklightInfectedHost trailHost;
    private final Map<ModelPart,Integer> trailParts=new IdentityHashMap<>();
    private final SurfaceTissue surface;
    private final AnatomicalTissue anatomical;
    private final AnatomicalTissue lateOrgans;
    private final ChimeraTissue chimera;
    private final List<AnatomicalTissue> anatomicalCoats=new ArrayList<>();
    private final float tissueGrowth;
    private final long tissueSeed;
    private final String speciesId;
    private final AuthoredInfectionParts.Binding authored;
    private final java.util.Set<AuthoredInfectionParts.Binding> coats=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final String profile;
    private final InfectedMorphology previous;
    private final java.util.Set<AuthoredInfectionParts.Binding> coatGrowth=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final Map<ResourceLocation,ResourceLocation> skins=new HashMap<>();
    private final Map<ModelPart,Attachment> attachments;
    private final Map<ModelPart,Pending> pending = new IdentityHashMap<>();
    private final Map<ModelPart,PoseStack.Pose> nativeChimeraPoses=new IdentityHashMap<>();

    private static JsonObject definitions(){
        try(var stream=InfectedMorphology.class.getResourceAsStream("/assets/blacklight/models/infected_morphology.json")){
            if(stream==null)throw new IllegalStateException("Missing authored infection morphology");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new IllegalStateException("Cannot load infection morphology",e);}
    }
    public InfectedMorphology(BlacklightInfectedHost host,LivingEntity proxy,EntityModel<?> model,ResourceLocation nativeTexture,PoseStack.Pose root,float partial,org.joml.Matrix4f worldRelativePose){
        this.proxy=proxy;trailHost=host;previous=ACTIVE.get();
        var anatomy=ConsumeAnatomy.model(model);var chosen=new HashMap<Integer,ModelPart>();
        anatomy.forEach((part,role)->{if(role==ConsumeAnatomy.RIGHT_ARM||role==ConsumeAnatomy.LEFT_ARM||role==ConsumeAnatomy.HEAD){var b=bounds(part);var old=chosen.get(role);if(b!=null&&(old==null||b.volume()>bounds(old).volume()))chosen.put(role,part);}});
        chosen.forEach((role,part)->trailParts.put(part,role));
        chimera=ChimeraTissue.bind(host,model,partial);
        String type=host.hostData().getString("Type");speciesId=type;
        var approved=Boolean.getBoolean("blacklight.proceduralTissueOnly")?new AuthoredInfectionParts.Binding():AuthoredInfectionParts.bind(type,model);
        anatomical=approved.available()?null:AnatomicalTissue.bind(host,model,nativeTexture,partial);
        authored=approved;
        lateOrgans=authored.available()?AnatomicalTissue.grafts(host,model,nativeTexture,partial):null;
        if(anatomical!=null)anatomical.worldPose(worldRelativePose);
        if(lateOrgans!=null)lateOrgans.worldPose(worldRelativePose);
        if(chimera!=null)chimera.worldPose(worldRelativePose);
        tissueGrowth=.12F+.88F*com.codex.blacklight.infection.TissueGrowth.progress(host.tissueAge());
        tissueSeed=host.getUUID().getMostSignificantBits()^host.getUUID().getLeastSignificantBits();
        authored.growth(tissueGrowth,tissueSeed);
        authored.evolution(host.tissueAge(),host.tissueTentacles(),ChimeraTissue.receiverEvolution(host,true,partial));
        authored.digestion(host.digestiveProfile(),host.digestiveVisual(),com.codex.blacklight.infection.TissueDigestiveMotion.clock(host.level().getGameTime(),partial));
        authored.vitals(host.vitalsProfile(),host.vitalsVisual(),TissueVitalMotion.clock(host.level().getGameTime(),partial));
        surface=new SurfaceTissue(host,model,root,partial,anatomical,authored);
        if(anatomical==null)skins.put(nativeTexture,InfectedWoundTextures.get(nativeTexture,type,authored.wounds()));
        var species=DEFINITIONS.getAsJsonObject("species");
        profile=species.has(type)?species.get(type).getAsString():switch(type){
            case "minecraft:skeleton_horse","minecraft:zombie_horse"->"equine";
            case "minecraft:giant"->"humanoid";
            case "minecraft:shulker"->"shelled";
            case "minecraft:tadpole"->"aquatic";
            case "minecraft:blaze","minecraft:breeze"->"winged";
            default->host.family().equals("air")?"winged":host.family().equals("water")?"aquatic":
                    host.humanoid()?"humanoid":host.quadruped()?"bovine":"generic";
        };
        attachments=anatomical!=null||authored.available()?new IdentityHashMap<>():new IdentityHashMap<>(CACHE.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(profile,k->bind(model,k)));
        ACTIVE.set(this);
    }
    @Override public void close(){pending.clear();authored.clear();coats.forEach(AuthoredInfectionParts.Binding::clear);if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    /** Fur must reveal the same bite instead of covering it with an intact outer coat.
     * Only clip the copied coat; do not emit a second set of body mutations. */
    public static void copiedSkinLayer(LivingEntity entity,EntityModel<?> model,ResourceLocation texture){
        var context=ACTIVE.get();
        if(context==null||context.proxy!=entity)return;
        String path=texture.getPath();
        if(!path.contains("sheep_fur")&&!path.contains("slime")&&!path.contains("drowned_outer_layer")
                &&!path.contains("stray_overlay")&&!path.contains("bogged_overlay"))return;
        if(context.anatomical!=null){
            var coat=context.anatomical.coat(model,texture);if(coat!=null)context.anatomicalCoats.add(coat);
            return;
        }
        if(context.authored.available()){
            var coat=AuthoredInfectionParts.bind(context.speciesId,model,path.contains("sheep_fur"));coat.growth(context.tissueGrowth,context.tissueSeed);coat.evolution(context.authored.age(),context.authored.tentacles(),context.authored.development());coat.digestion(context.authored);coat.vitals(context.authored);context.coats.add(coat);
            if(path.contains("sheep_fur")||path.contains("slime"))context.coatGrowth.add(coat);
            context.skins.put(texture,InfectedWoundTextures.get(texture,context.speciesId,coat.wounds()));return;
        }
        var layer=CACHE.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(context.profile,k->bind(model,k));
        layer.forEach((part,attachment)->context.attachments.putIfAbsent(part,
                new Attachment(attachment.anatomy,List.of(),attachment.wound)));
    }
    private static Bounds bounds(ModelPart part){
        var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
        if(cubes.isEmpty())return null;
        float x=Float.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=-x,Z=-x;
        for(var c:cubes){x=Math.min(x,c.minX);y=Math.min(y,c.minY);z=Math.min(z,c.minZ);X=Math.max(X,c.maxX);Y=Math.max(Y,c.maxY);Z=Math.max(Z,c.maxZ);}
        return new Bounds(x,y,z,Math.max(.5F,X-x),Math.max(.5F,Y-y),Math.max(.5F,Z-z));
    }
    private static Map<ModelPart,Attachment> bind(EntityModel<?> model,String profile){
        var anatomy=ConsumeAnatomy.model(model);Map<Integer,ModelPart> largest=new HashMap<>();
        for(var entry:anatomy.entrySet()){
            Bounds b=bounds(entry.getKey());if(b==null)continue;
            ModelPart old=largest.get(entry.getValue());
            if(old==null||b.volume()>bounds(old).volume())largest.put(entry.getValue(),entry.getKey());
        }
        // The humanoid hat may have the same cube size as the head but be hidden.
        if(model instanceof HumanoidModel<?> h){largest.put(ConsumeAnatomy.HEAD,h.head);largest.put(ConsumeAnatomy.BODY,h.body);}
        else {
            ModelPart root=com.codex.blacklight.client.animation.ReceiverModelPose.root(model);
            if(root!=null){
                preferNamed(largest,root,"head",ConsumeAnatomy.HEAD);
                preferNamed(largest,root,"body",ConsumeAnatomy.BODY);
            }
        }
        var definition=DEFINITIONS.getAsJsonObject("profiles").getAsJsonObject(profile);
        Map<ModelPart,Attachment> result=new IdentityHashMap<>();
        for(var entry:largest.entrySet()){
            String role=switch(entry.getKey()){case ConsumeAnatomy.BODY->"body";case ConsumeAnatomy.HEAD->"head";case ConsumeAnatomy.TAIL->"tail";default->"limb";};
            Bounds b=bounds(entry.getValue());if(b==null)continue;
            boolean horizontalBody=entry.getKey()==ConsumeAnatomy.BODY
                    && Set.of("bovine","porcine","woolly","canine","feline","equine","small_quadruped").contains(profile)
                    && Math.abs(Math.sin(entry.getValue().getInitialPose().xRot))<.5;
            result.put(entry.getValue(),attachment(entry.getValue(),b,entry.getKey(),definition.getAsJsonArray(role),horizontalBody,profile));
        }
        // Spiders have eight articulated legs, not four humanoid limb slots.
        if(profile.equals("arthropod"))for(var entry:anatomy.entrySet()){
            if(entry.getValue()<ConsumeAnatomy.RIGHT_ARM||entry.getValue()>ConsumeAnatomy.LEFT_LEG)continue;
            Bounds b=bounds(entry.getKey());if(b!=null)result.put(entry.getKey(),attachment(entry.getKey(),b,entry.getValue(),definition.getAsJsonArray("limb"),false,profile));
        }
        // Native outer hats and coats use the same body animation, but must not
        // cover its bite with another intact quad or emit duplicate bone growth.
        if(model instanceof HumanoidModel<?> h&&!result.containsKey(h.hat))
            result.put(h.hat,new Attachment(ConsumeAnatomy.HEAD,List.of(),InfectedBite.create(h.hat,ConsumeAnatomy.HEAD,profile)));
        ModelPart root=com.codex.blacklight.client.animation.ReceiverModelPose.root(model);
        if(root!=null)bindCoats(root,result,anatomy,profile);
        return result;
    }
    private static void preferNamed(Map<Integer,ModelPart> largest,ModelPart root,String name,int anatomy){
        var children=((ModelPartChildrenAccessor)(Object)root).blacklight$children();
        ModelPart part=children.get(name);
        if(part!=null&&bounds(part)!=null){largest.put(anatomy,part);return;}
        for(var child:children.values())preferNamed(largest,child,name,anatomy);
    }
    private static void bindCoats(ModelPart root,Map<ModelPart,Attachment> result,Map<ModelPart,Integer> anatomy,String profile){
        for(var child:((ModelPartChildrenAccessor)(Object)root).blacklight$children().entrySet()){
            String name=child.getKey();ModelPart part=child.getValue();
            if(name.equals("hat")||name.equals("jacket")||name.endsWith("_sleeve")||name.endsWith("_pants")){
                int role=anatomy.getOrDefault(part,ConsumeAnatomy.BODY);
                if(!result.containsKey(part))result.put(part,new Attachment(role,List.of(),InfectedBite.create(part,role,profile)));
            }
            bindCoats(part,result,anatomy,profile);
        }
    }
    private static Attachment attachment(ModelPart part,Bounds b,int anatomy,JsonArray source,boolean horizontalBody,String profile){
        List<Box> boxes=new ArrayList<>();
        InfectedBite wound=InfectedBite.create(part,anatomy,profile);
        if(wound!=null)addWoundInterior(boxes,wound);
        return new Attachment(anatomy,List.copyOf(boxes),wound);
    }
    private static void addWoundInterior(List<Box> boxes,InfectedBite w){
        float depth=w.depth,edge=Math.max(.16F,Math.min(.4F,depth*.24F));
        for(int band=0;band<3;band++){
            float lowY=w.y0+(w.y1-w.y0)*(band==0?0:band==1?.25F:.75F);
            float highY=w.y0+(w.y1-w.y0)*(band==0?.25F:band==1?.75F:1);
            float lowZ=w.z0+(w.z1-w.z0)*(band==0?.16F:0);
            float highZ=w.z0+(w.z1-w.z0)*(band==2?.82F:1);
            float middleX=w.face+w.inward*depth*.5F;
            // The original skin is cut away. These are recessed surfaces inside
            // the removed volume, rather than a red plate hiding an intact face.
            boxes.add(new Box(w.face+w.inward*depth,(lowY+highY)*.5F,(lowZ+highZ)*.5F,.10F,highY-lowY,highZ-lowZ,1));
            boxes.add(new Box(middleX,lowY+edge*.5F,(lowZ+highZ)*.5F,depth,edge,highZ-lowZ,0));
            boxes.add(new Box(middleX,highY-edge*.5F,(lowZ+highZ)*.5F,depth,edge,highZ-lowZ,1));
            boxes.add(new Box(middleX,(lowY+highY)*.5F,lowZ+edge*.5F,depth,highY-lowY,edge,0));
            boxes.add(new Box(middleX,(lowY+highY)*.5F,highZ-edge*.5F,depth,highY-lowY,edge,1));
        }
        for(int i=0;i<2;i++)boxes.add(new Box(w.face+w.inward*depth*.55F,w.y0+(w.y1-w.y0)*(.40F+i*.23F),
                w.z0+(w.z1-w.z0)*(.35F+i*.27F),Math.max(.2F,depth*.42F),Math.max(.25F,(w.y1-w.y0)*.11F),
                Math.max(.3F,(w.z1-w.z0)*.32F),2));
    }
    /** Compile only selected native cubes through a local face cutter. No shared model mutation. */
    public static boolean renderWounded(ModelPart part,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
        var context=ACTIVE.get();if(context==null)return false;
        var originalPose=pose;
        if(context.chimera!=null){if(context.chimera.nativeWeight(part)<=.0001F)return true;pose=context.chimera.nativePose(part,pose);context.nativeChimeraPoses.put(part,pose);}
        EvolvedDonorCapture.joint(part,pose);
        context.trail(part,pose);
        if(context.chimera!=null)context.chimera.capture(part,pose,light,overlay);
        if(context.anatomical!=null){
            boolean handled=context.anatomical.renderSkin(part,pose,out,light,overlay,color);
            if(handled)context.surface.capture(part,pose,light,overlay,color);
            if(!handled)for(var coat:context.anatomicalCoats)if(coat.renderSkin(part,pose,out,light,overlay,color))return true;
            if(!handled&&pose!=originalPose){for(var cube:((ModelPartChildrenAccessor)(Object)part).blacklight$cubes())cube.compile(pose,out,light,overlay,color);capture(part,pose,light,overlay,color);return true;}
            return handled;
        }
        pose=context.surface.mutatedPose(part,pose);
        if(context.surface.replacesBody(part)) {
            capture(part,pose,light,overlay,color);return true;
        }
        if(context.authored.renderSkin(part,pose,out,light,overlay,color)){
            if(context.lateOrgans!=null)context.lateOrgans.capture(part,context.authored.pose(part,pose),light,overlay,color);
            context.surface.capture(part,pose,light,overlay,color);return true;
        }
        for(var coat:context.coats)if(coat.renderSkin(part,pose,out,light,overlay,color)){if(!context.coatGrowth.contains(coat))coat.clear();return true;}
        Attachment attachment=context.attachments.get(part);
        // Slime's translucent shell bypasses RenderLayer's copied-cutout helper.
        // Its full cuboid is still a ModelPart: open the matching recess there,
        // keeping the slime's eyes/mouth and native transparency untouched.
        if(attachment==null&&context.proxy instanceof net.minecraft.world.entity.monster.Slime){
            Bounds b=bounds(part);
            if(b!=null&&b.volume()>=64){
                attachment=new Attachment(ConsumeAnatomy.BODY,List.of(),InfectedBite.create(part,ConsumeAnatomy.BODY,"soft"));
                context.attachments.put(part,attachment);
            }
        }
        if(attachment==null||attachment.wound==null){
            if(pose==originalPose)return false;
            for(var cube:((ModelPartChildrenAccessor)(Object)part).blacklight$cubes())cube.compile(pose,out,light,overlay,color);
            capture(part,pose,light,overlay,color);return true;
        }
        for(var cube:((ModelPartChildrenAccessor)(Object)part).blacklight$cubes()){
            if(cube==attachment.wound.cube)attachment.wound.compile(pose,out,light,overlay,color);
            else cube.compile(pose,out,light,overlay,color);
        }
        // Cancelling ModelPart.compile skips its TAIL hook; record the exact same posed
        // attachment here so wound walls still participate in absorption/fracture.
        capture(part,pose,light,overlay,color);
        return true;
    }
    /** Called inside ModelPart.compile, after its complete animated transform. */
    public static void capture(ModelPart part,PoseStack.Pose pose,int light,int overlay,int color){
        var context=ACTIVE.get();if(context==null||context.pending.containsKey(part))return;
        if(context.chimera!=null){if(context.chimera.nativeWeight(part)<=.0001F)return;var adjusted=context.nativeChimeraPoses.get(part);pose=adjusted==null?context.chimera.nativePose(part,pose):adjusted;}
        EvolvedDonorCapture.joint(part,pose);
        context.trail(part,pose);
        if(context.chimera!=null)context.chimera.capture(part,pose,light,overlay);
        if(context.anatomical!=null){
            context.anatomical.capture(part,pose,light,overlay,color);
            if(context.anatomical.owns(part))context.surface.capture(part,pose,light,overlay,color);
            for(var coat:context.anatomicalCoats)coat.capture(part,pose,light,overlay,color);return;
        }
        if(context.surface!=null)context.surface.capture(part,pose,light,overlay,color);
        context.authored.capture(part,pose,light,overlay,color);
        if(context.lateOrgans!=null)context.lateOrgans.capture(part,context.authored.pose(part,pose),light,overlay,color);
        for(var coat:context.coatGrowth)coat.capture(part,pose,light,overlay,color);
        Attachment attachment=context.attachments.get(part);if(attachment==null||attachment.boxes.isEmpty())return;
        PoseStack copy=new PoseStack();copy.last().pose().set(pose.pose());copy.last().normal().set(pose.normal());
        context.pending.put(part,new Pending(attachment,copy.last(),light,overlay,color));
    }
    private void trail(ModelPart part,PoseStack.Pose pose){var slot=trailParts.get(part);if(slot==null)return;var b=bounds(part);if(b!=null)InfectedLimbTrails.capture(trailHost,slot,pose,new net.minecraft.world.phys.Vec3(b.x/16,(b.y+b.h*.45F)/16,b.z/16));}
    /** Flush before the current renderer's consume fracture wrapper finishes.
     * Deferred emission avoids switching a live vanilla BufferBuilder midway
     * through its model; context never mutates the shared species skeleton. */
    public static void flush(LivingEntity entity,MultiBufferSource buffers){
        var context=ACTIVE.get();if(context==null||context.proxy!=entity)return;
        if(context.chimera!=null)context.chimera.flush(buffers);
        if(context.anatomical!=null){context.anatomical.flush(buffers);context.anatomicalCoats.forEach(coat->coat.flush(buffers));context.surface.flush(buffers);return;}
        context.authored.flush(buffers,!context.coatGrowth.isEmpty(),false);
        context.coatGrowth.forEach(coat->coat.flush(buffers,false,true));
        if(context.lateOrgans!=null)context.lateOrgans.flush(buffers);
        if(context.surface!=null)context.surface.flush(buffers);
        if(context.pending.isEmpty())return;
        var out=buffers.getBuffer(TYPE);
        for(var p:context.pending.values()){
            if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(p.attachment.anatomy);
            for(var box:p.attachment.boxes)draw(box,p,out);
        }
        context.pending.clear();
    }
    public static RenderType skinType(RenderType type){
        var context=ACTIVE.get();if(context==null)return type;
        for(var entry:context.skins.entrySet()){
            var a=entry.getKey();var b=entry.getValue();if(a.equals(b))continue;
            if(type==RenderType.entityCutoutNoCull(a))return RenderType.entityCutoutNoCull(b);
            if(type==RenderType.entityCutoutNoCull(a,false))return RenderType.entityCutoutNoCull(b,false);
            if(type==RenderType.entityCutout(a))return RenderType.entityCutout(b);
            if(type==RenderType.entityTranslucent(a))return RenderType.entityTranslucent(b);
            if(type==RenderType.entityTranslucentCull(a))return RenderType.entityTranslucentCull(b);
        }
        return type;
    }
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{1,2,6,5},{0,4,7,3},{3,7,6,2},{0,1,5,4}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
    private static final int[][] CORNERS={{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1},{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}};
    private static void draw(Box b,Pending p,VertexConsumer out){
        float u=(b.material*16+.25F)/64F,v=.25F/64F,span=15.5F/64F;
        for(int face=0;face<6;face++)for(int corner=0;corner<4;corner++){
            int[] c=CORNERS[FACES[face][corner]];float[] n=NORMALS[face];
            out.addVertex(p.pose,(b.x+c[0]*b.w*.5F)/16,(b.y+c[1]*b.h*.5F)/16,(b.z+c[2]*b.d*.5F)/16)
                    .setColor(p.color).setUv(u+(corner>=2?span:0),v+(corner==0||corner==3?span:0))
                    .setOverlay(p.overlay).setLight(p.light).setNormal(p.pose,n[0],n[1],n[2]);
        }
    }
}
