package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GreeneProjectileAppearance;
import com.codex.blacklight.entity.GreeneProjectile;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;

/** Authored block geometry, using original torso/asphalt template proportions. */
public final class GreeneProjectileRenderer extends EntityRenderer<GreeneProjectile> {
    private record Cube(Vec3 from,Vec3 to,ResourceLocation texture,int color) {}
    private static final Map<String,List<Cube>> SHAPES=load();
    public GreeneProjectileRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.25F;}
    @Override public ResourceLocation getTextureLocation(GreeneProjectile projectile){return SHAPES.get(GreeneProjectileAppearance.of(projectile.ammo()).shape()).get(0).texture;}
    @Override public void render(GreeneProjectile projectile,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var profile=GreeneProjectileAppearance.of(projectile.ammo());
        if(!GreeneProjectileAppearance.meshVisible(projectile.ammo(),projectile.finished()))return;
        stack.pushPose();
        stack.mulPose(GreeneProjectileAppearance.orientation(projectile.ammo(),projectile.getUUID(),projectile.renderHeading(partial),projectile.renderFlightSeconds(partial)));
        stack.scale(profile.scale(),profile.scale(),profile.scale());
        for(var cube:SHAPES.get(profile.shape()))box(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(cube.texture)),light,authoredBounds(profile.shape()),cube);
        stack.popPose();super.render(projectile,yaw,partial,stack,buffers,light);
    }
    private static AABB authoredBounds(String shape) {
        return shape.equals("torso") ? new AABB(-.31,-.50,-.20,.31,.50,.20)
                : new AABB(-.75,-.42,-.25,.75,.42,.25);
    }
    private static void box(PoseStack stack,VertexConsumer out,int light,AABB bounds,Cube cube){
        Vec3 a=point(bounds,cube.from),b=point(bounds,cube.to);
        Vec3[] p={new Vec3(a.x,a.y,a.z),new Vec3(b.x,a.y,a.z),new Vec3(b.x,b.y,a.z),new Vec3(a.x,b.y,a.z),
            new Vec3(a.x,a.y,b.z),new Vec3(b.x,a.y,b.z),new Vec3(b.x,b.y,b.z),new Vec3(a.x,b.y,b.z)};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        float[][] uv={{0,1},{0,0},{1,0},{1,1}};
        for(int[] face:faces){Vec3 normal=p[face[1]].subtract(p[face[0]]).cross(p[face[2]].subtract(p[face[0]])).normalize();
            for(int i=0;i<4;i++){Vec3 v=p[face[i]];
                out.vertex(stack.last().pose(),(float)v.x,(float)v.y,(float)v.z).color(cube.color).uv(uv[i][0],uv[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(stack.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
            }
        }
    }
    private static Vec3 point(AABB b,Vec3 p){return new Vec3(b.minX+p.x*b.getXsize(),b.minY+p.y*b.getYsize(),b.minZ+p.z*b.getZsize());}
    private static Map<String,List<Cube>> load(){
        try(var in=Objects.requireNonNull(GreeneProjectileRenderer.class.getResourceAsStream("/assets/blacklight/models/greene_projectile_shapes.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var materials=root.getAsJsonObject("materials");var result=new HashMap<String,List<Cube>>();
            for(var entry:root.getAsJsonObject("shapes").entrySet()){
                var boxes=new ArrayList<Cube>();
                for(var value:entry.getValue().getAsJsonArray()){
                    var c=value.getAsJsonObject();String[] path=materials.get(c.get("material").getAsString()).getAsString().split(":",2);
                    boxes.add(new Cube(vector(c.getAsJsonArray("from")),vector(c.getAsJsonArray("to")),new ResourceLocation(path[0],path[1]),(int)Long.parseLong(c.get("color").getAsString(),16)));
                }result.put(entry.getKey(),List.copyOf(boxes));
            }return Map.copyOf(result);
        }catch(Exception ex){throw new IllegalStateException("Invalid authored Greene projectile model",ex);}
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
