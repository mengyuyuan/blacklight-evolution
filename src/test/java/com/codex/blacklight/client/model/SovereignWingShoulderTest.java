package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.SovereignWingMotion;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SovereignWingShoulderTest {
    private static final class Vertices extends com.codex.blacklight.forge.ForgeVertexConsumer {
        final ArrayList<Vector3f> points=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    @Test void actualRenderedAttachmentStaysOnChestThroughFoldingAndFlapping() throws Exception {
        var caps=new ArrayList<Integer>();
        try(var in=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            int offset=0;
            for(var value:data.getAsJsonArray("elements")) {
                var e=value.getAsJsonObject();
                int count=e.has("vertices")?e.getAsJsonObject("faces").size()*4:24;
                if(e.has("_root_skin"))for(int v=0;v<count;v++)caps.add(offset+v);
                offset+=count;
            }
        }
        assertFalse(caps.isEmpty());
        var model=new SovereignWingModel();Vertices rest=null;
        float wingTravel=0;
        for(int order=0;order<2;order++)for(float air:new float[]{0,.5F,1})for(int step=0;step<12;step++) {
            var motion=SovereignWingMotion.sample(step/12.,1,air,1,1,.5F,1,.5F,0);
            model.prepare(motion,new Vector3f(0,0,1),1,air,order);
            var stack=new PoseStack();model.prepareRender(stack);
            var drawn=new Vertices();model.render(stack,drawn,0);
            if(rest==null)rest=drawn;
            for(int index:caps)assertTrue(rest.points.get(index).distance(drawn.points.get(index))<.00001F,"Shoulder vertex moved: "+index);
            for(int i=0;i<drawn.points.size();i+=24)wingTravel=Math.max(wingTravel,rest.points.get(i).distance(drawn.points.get(i)));
        }
        assertTrue(wingTravel>.5F,"Wings must still articulate independently of the fixed shoulders");
    }
}
