package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.util.RenderUtil;
import java.util.*;

/** A read-only anatomy view of real baked Geo cubes. Source bones and animation
 * remain in GeckoLib. Capture uses the actual posed joint, not a cached world box. */
public final class GeoNativeGeometry extends HierarchicalModel<Mob> implements NativeGeometryMetadata {
    private final ModelPart root;
    private final Map<GeoBone,ModelPart> parts=new IdentityHashMap<>();
    private int cubeCount;
    public GeoNativeGeometry(BakedGeoModel source){
        var children=new LinkedHashMap<String,ModelPart>();
        for(var bone:source.topLevelBones())children.put(bone.getName(),build(bone,0));
        root=new ModelPart(List.of(),children);
    }
    private ModelPart build(GeoBone bone,int depth){
        if(depth>128)return new ModelPart(List.of(),Map.of());
        var cubes=new ArrayList<ModelPart.Cube>();
        for(var cube:bone.getCubes()){
            var pose=new PoseStack();RenderUtil.translateToPivotPoint(pose,cube);RenderUtil.rotateMatrixAroundCube(pose,cube);RenderUtil.translateAwayFromPivotPoint(pose,cube);
            float x=Float.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=-x,Z=-x;
            for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()){
                var point=pose.last().pose().transformPosition(new Vector3f(vertex.position()));
                float a=point.x*16-bone.getPivotX(),b=bone.getPivotY()-point.y*16,c=point.z*16-bone.getPivotZ();
                if(!Float.isFinite(a)||!Float.isFinite(b)||!Float.isFinite(c))continue;
                x=Math.min(x,a);y=Math.min(y,b);z=Math.min(z,c);X=Math.max(X,a);Y=Math.max(Y,b);Z=Math.max(Z,c);
            }
            if(!Float.isFinite(x)||X-x<.01F||Y-y<.01F||Z-z<.01F)continue;
            cubes.add(new ModelPart.Cube(0,0,x,y,z,X-x,Y-y,Z-z,0,0,0,false,64,64,EnumSet.allOf(Direction.class)));cubeCount++;
        }
        var children=new LinkedHashMap<String,ModelPart>();
        for(var child:bone.getChildBones())children.put(child.getName(),build(child,depth+1));
        var part=new ModelPart(cubes,children);var parent=bone.getParent();var initial=bone.getInitialSnapshot();
        float px=parent==null?0:parent.getPivotX(),py=parent==null?0:parent.getPivotY(),pz=parent==null?0:parent.getPivotZ();
        float ox=initial==null?0:initial.getOffsetX(),oy=initial==null?0:initial.getOffsetY(),oz=initial==null?0:initial.getOffsetZ();
        var rest=PartPose.offsetAndRotation(bone.getPivotX()-px-ox,-(bone.getPivotY()-py+oy),bone.getPivotZ()-pz+oz,
                initial==null?0:-initial.getRotX(),initial==null?0:initial.getRotY(),initial==null?0:-initial.getRotZ());
        part.setInitialPose(rest);part.loadPose(rest);
        if(initial!=null){part.xScale=initial.getScaleX();part.yScale=initial.getScaleY();part.zScale=initial.getScaleZ();}
        parts.put(bone,part);return part;
    }
    public int cubeCount(){return cubeCount;}
    public int jointCount(){return parts.size();}
    public ModelPart joint(GeoBone bone){return parts.get(bone);}
    public boolean capture(GeoBone bone,PoseStack stack,int light,int overlay){
        var part=parts.get(bone);if(part==null||bone.isHidden())return false;
        stack.pushPose();
        try{
            // Gecko's layer callback follows translateAwayFromPivotPoint. Rebase to
            // the real joint and normalize its upward Y axis to ModelPart coordinates.
            RenderUtil.translateToPivotPoint(stack,bone);stack.scale(1,-1,1);
            InfectedMorphology.capture(part,stack.last(),light,overlay,0xffffffff);
        }finally{stack.popPose();}
        return true;
    }
    @Override public ModelPart root(){return root;}
    @Override public void setupAnim(Mob entity,float limb,float speed,float age,float yaw,float pitch){}
}
