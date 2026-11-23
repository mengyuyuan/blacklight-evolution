package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The assembled 5x5 nest is built around a split square die. Dimensions are in blocks. */
final class WeaveMantle {
    private static final ResourceLocation MANTLE = new ResourceLocation("blacklight", "textures/block/weave_mantle.png");
    private static final ResourceLocation CUTICLE = new ResourceLocation("blacklight", "textures/block/weave_cuticle.png");
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final int BASALT = 0xff30363e;
    private static final int GRAPHITE = 0xff515d64;
    private static final int SHALE = 0xff76807d;
    private static final int BONE = 0xffb4aa91;
    private static final int MARROW = 0xff67232d;
    private static final int RECESS = 0xff201e26;
    private static final double DECK_HEIGHT = .825;
    private static final Face DECK = new Face(new Vec3(-2.5,DECK_HEIGHT,-2.5),new Vec3(-2.5,DECK_HEIGHT,2.5),
                new Vec3(2.5,DECK_HEIGHT,2.5),new Vec3(2.5,DECK_HEIGHT,-2.5),UP,0xffffffff);
    private static final Mesh FRAME = new Mesh();
    private static final Mesh JAW = new Mesh();
    private static final Mesh KERNEL = new Mesh();
    // Each arm's half of a pair. The channels follow the alleys between offering centers.
    private static final double[][] ROUTE = {{.75,-1.81},{.75,-1.52},{.46,-1.52},
            {.46,-.64},{.22,-.64},{.22,-.22}};
    private static final double[] DISTANCES = new double[ROUTE.length];
    private static final int FLOW_STEPS = 24;

    static {
        for (int i=1; i<ROUTE.length; i++) {
            DISTANCES[i] = DISTANCES[i-1] + Math.hypot(ROUTE[i][0]-ROUTE[i-1][0], ROUTE[i][1]-ROUTE[i-1][1]);
        }
        for(int quarter=0;quarter<4;quarter++){
            buildApron(quarter);
            buildCorner(quarter);
            for(int side:new int[]{-1,1}) buildChannel(quarter,side);
        }
        // The press has a deep garnet bed, undercut corners and four independent jaws.
        FRAME.box(0,-.48,.827,-.48,.48,.866,.48,RECESS);
        FRAME.box(0,-.365,.866,-.365,.365,.907,.365,MARROW);
        FRAME.box(0,-.28,.907,-.28,.28,.922,.28,BASALT);
        JAW.box(0,.235,.865,-.49,.49,.969,-.15,GRAPHITE);
        JAW.box(0,-.025,.866,-.495,.29,.960,-.315,GRAPHITE);
        JAW.box(0,.258,.969,-.453,.442,1.073,-.205,SHALE);
        JAW.box(0,.063,.961,-.449,.266,1.025,-.337,BASALT);
        JAW.box(0,.18,.972,-.36,.282,1.033,-.265,BONE);
        JAW.box(0,.186,.922,-.27,.274,.981,-.144,MARROW);
        // The four raised plates curl clockwise around a recessed, lobed red gland.
        // Shortening each successive block leaves the mouth and its contents visible.
        JAW.box(0,.266,1.073,-.44,.451,1.16,-.238,GRAPHITE);
        JAW.box(0,.24,1.16,-.43,.414,1.214,-.276,SHALE);
        JAW.box(0,.173,1.192,-.422,.34,1.26,-.30,0xffa59482);
        JAW.box(0,.12,1.219,-.409,.24,1.256,-.324,BONE);
        JAW.box(0,.14,1.15,-.392,.232,1.219,-.328,MARROW);
        KERNEL.box(0,-.205,.919,-.117,.205,1.054,.117,0xffcb8795);
        KERNEL.box(0,-.105,.921,-.213,.105,1.073,.208,0xffde8e9a);
        KERNEL.box(0,-.145,.973,-.145,.145,1.118,.145,0xffffffff);
        KERNEL.box(0,-.087,1.118,-.088,.089,1.157,.09,0xffd6aab2);
    }

    private static void buildApron(int q){
        // The overhang casts a long shadow over the exposed horizontal tissue slit.
        FRAME.box(q,-2.23,.085,-2.518,2.23,.183,-2.389,BASALT);
        FRAME.box(q,-2.24,.185,-2.504,2.24,.558,-2.43,RECESS);
        // Armor recedes here; the deep fluted tissue can be seen between the bony clamps.
        FRAME.box(q,-1.98,.231,-2.517,1.98,.521,-2.499,0xff712235);
        for (int side : new int[]{-1,1}) {
            for (int i=0;i<3;i++) {
                double root=side*(.12+i*.46);
                FRAME.box(q,root-.074,.253,-2.523,root+.074,.499,-2.515,0xffaa4554);
                double rib=root+side*.16;
                FRAME.box(q,rib-.065,.208,-2.541,rib+.065,.409,-2.513,GRAPHITE);
                rib+=side*.068;
                FRAME.box(q,rib-.095,.407,-2.544,rib+.095,.549,-2.512,SHALE);
                rib+=side*.071;
                FRAME.box(q,rib-.088,.548,-2.546,rib+.088,.603,-2.498,GRAPHITE);
            }
        }
        FRAME.box(q,-2.25,.548,-2.54,2.25,.751,-2.29,GRAPHITE);
        FRAME.box(q,-2.13,.751,-2.52,2.13,.851,-2.33,BASALT);
        FRAME.box(q,-1.48,.851,-2.49,1.48,.883,-2.373,SHALE);
        // A recessed hand rest is framed by two locking cheeks.
        for(int side:new int[]{-1,1}){
            double x=side*1.49;
            FRAME.box(q,x-.17,.146,-2.532,x+.17,.559,-2.33,BASALT);
            FRAME.box(q,x-.098,.365,-2.549,x+.098,.667,-2.47,GRAPHITE);
            FRAME.box(q,x-.078,.668,-2.547,x+.078,.703,-2.47,SHALE);
        }
    }

    private static void buildCorner(int q){
        // This L-shaped buttress climbs in three squared steps, leaving the corner slot clear.
        FRAME.box(q,2.16,.03,-2.525,2.526,.765,-2.15,BASALT);
        FRAME.box(q,2.238,.20,-2.545,2.547,.711,-2.238,GRAPHITE);
        FRAME.box(q,1.55,.744,-2.47,2.47,.886,-2.178,GRAPHITE);
        FRAME.box(q,2.178,.744,-2.47,2.47,.886,-1.55,GRAPHITE);
        FRAME.box(q,1.72,.886,-2.46,2.483,1.019,-2.235,BASALT);
        FRAME.box(q,2.235,.886,-2.463,2.483,1.019,-1.72,BASALT);
        FRAME.box(q,1.93,1.019,-2.443,2.462,1.132,-2.243,SHALE);
        FRAME.box(q,2.243,1.019,-2.439,2.462,1.132,-1.96,SHALE);
        FRAME.box(q,2.229,1.132,-2.447,2.448,1.219,-2.225,GRAPHITE);
        FRAME.box(q,2.24,1.219,-2.441,2.409,1.243,-2.285,BONE);
        FRAME.box(q,1.62,.885,-2.44,1.706,.917,-2.223,BONE);
        FRAME.box(q,2.224,.885,-1.704,2.438,.917,-1.62,BONE);
    }

    private static void buildChannel(int q,int side){
        // A dark casing flanks a narrow living bed. Both share the exact route of the moving bolus.
        for(int i=1;i<ROUTE.length;i++){
            double x0=ROUTE[i-1][0]*side,z0=ROUTE[i-1][1];
            double x1=ROUTE[i][0]*side,z1=ROUTE[i][1];
            channelBox(q,x0,z0,x1,z1,.073,.827,.849,BASALT);
            channelBox(q,x0,z0,x1,z1,.036,.850,.859,0xff753440);
        }
        double x=ROUTE[0][0]*side,z=ROUTE[0][1];
        FRAME.box(q,x-.213,.828,z-.168,x+.213,.864,z+.168,GRAPHITE);
        FRAME.box(q,x-.161,.864,z-.116,x+.161,.891,z+.116,MARROW);
        FRAME.box(q,x-.202,.865,z-.159,x-.138,.963,z+.137,SHALE);
        FRAME.box(q,x+.138,.865,z-.159,x+.202,.963,z+.137,SHALE);
        FRAME.box(q,x-.138,.867,z-.157,x+.138,.918,z-.111,BASALT);
    }

    private static void channelBox(int q,double x0,double z0,double x1,double z1,double w,double low,double high,int color){
        FRAME.box(q,Math.min(x0,x1)-w,low,Math.min(z0,z1)-w,Math.max(x0,x1)+w,high,Math.max(z0,z1)+w,color);
    }

    static Vec3 socket(Vec3 inward,int side){
        return point(inward,ROUTE[0][0]*side,ROUTE[0][1],.913);
    }

    private static Vec3 point(Vec3 inward,double x,double z,double y){
        return new Vec3(-inward.z*x+inward.x*z,y,inward.x*x+inward.z*z);
    }

    static void draw(PoseStack pose,MultiBufferSource buffers,int light,Vec3 activeSide,double time){
        pose.pushPose();
        pose.translate(.5,0,.5);
        var top=buffers.getBuffer(RenderType.entityCutoutNoCull(MANTLE));
        DECK.draw(pose,top,light,0xffffffff);
        var plate=buffers.getBuffer(RenderType.entityCutoutNoCull(CUTICLE));
        FRAME.draw(pose,plate,light,0xffffffff);
        double pressure=activeSide==null?0:Math.max(WeaveTransport.surge(time,-1,1,2),WeaveTransport.surge(time,1,1,2));
        for(int turn=0;turn<4;turn++){
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(turn*90));
            pose.translate(.062*pressure,.024*pressure,.062*pressure);
            JAW.draw(pose,plate,light,0xffffffff);
            pose.popPose();
        }
        var tissue=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        pose.pushPose();
        pose.translate(0,pressure*.047,0);
        KERNEL.draw(pose,tissue,pressure>.08?0xf000f0:light,pigment(.43+.57*pressure));
        pose.popPose();
        if(activeSide!=null){
            for(int side:new int[]{-1,1}) drawPulse(pose,tissue,activeSide,side,time);
        }
        pose.popPose();
    }

    private static void drawPulse(PoseStack pose,VertexConsumer out,Vec3 inward,int side,double time){
        double total=DISTANCES[DISTANCES.length-1];
        for(int i=1;i<ROUTE.length;i++){
            double length=DISTANCES[i]-DISTANCES[i-1];
            int samples=(int)Math.ceil(FLOW_STEPS*length/total);
            for(int j=0;j<samples;j++){
                double a=j/(double)samples,b=(j+1)/(double)samples;
                double distance=(DISTANCES[i-1]+length*(a+b)*.5)/total;
                double pressure=WeaveTransport.surge(time,side,1,1+distance);
                if(pressure<.016)continue;
                double[] start=ROUTE[i-1],end=ROUTE[i];
                double x0=(start[0]+a*(end[0]-start[0]))*side, z0=start[1]+a*(end[1]-start[1]);
                double x1=(start[0]+b*(end[0]-start[0]))*side, z1=start[1]+b*(end[1]-start[1]);
                double w=.023+.011*pressure;
                rectangle(pose,out,inward,Math.min(x0,x1)-w,Math.min(z0,z1)-w,
                        Math.max(x0,x1)+w,Math.max(z0,z1)+w,.863,pigment(pressure));
            }
        }
        double pressure=WeaveTransport.surge(time,side,1,1);
        if(pressure>.025){
            double x=side*ROUTE[0][0],z=ROUTE[0][1];
            rectangle(pose,out,inward,x-.134,z-.104,x+.134,z+.103,.893,pigment(pressure));
        }
    }

    private static void rectangle(PoseStack pose,VertexConsumer out,Vec3 inward,double x0,double z0,double x1,double z1,double y,int color){
        new Face(point(inward,x0,z0,y),point(inward,x0,z1,y),point(inward,x1,z1,y),point(inward,x1,z0,y),UP,color)
                .draw(pose,out,0xf000f0,0xffffffff);
    }

    static int pigment(double amount){
        return 0xff000000 | ((int)(90+155*amount)<<16) | ((int)(15+56*amount)<<8) | (int)(32+57*amount);
    }

    private static int multiply(int a,int b){
        return 0xff000000 | ((((a>>16&255)*(b>>16&255))/255)<<16)
                | ((((a>>8&255)*(b>>8&255))/255)<<8) | ((a&255)*(b&255)/255);
    }

    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,int color){
        void draw(PoseStack pose,VertexConsumer out,int light,int tint){
            int shaded=tint==0xffffffff?color:multiply(color,tint);
            vertex(pose,out,light,a,shaded);vertex(pose,out,light,b,shaded);
            vertex(pose,out,light,c,shaded);vertex(pose,out,light,d,shaded);
        }
        private void vertex(PoseStack pose,VertexConsumer out,int light,Vec3 v,int shaded){
            float u=(float)(.5+(normal.x==0?v.x:v.z)/5);
            float texV=normal.y==0?(float)(.92-v.y*.64):(float)(.5+v.z/5);
            ConsumedMobVisuals.vertex(pose,out,light,v,normal,shaded,u,texV);
        }
    }

    private static final class Mesh {
        private final List<Face> faces=new ArrayList<>();

        void box(int quarter,double x0,double y0,double z0,double x1,double y1,double z1,int color){
            Vec3 f=switch(quarter){case 1->new Vec3(-1,0,0);case 2->new Vec3(0,0,-1);case 3->new Vec3(1,0,0);default->SOUTH;};
            Vec3 a=point(f,x0,z0,y0), b=point(f,x1,z1,y1);
            double west=Math.min(a.x,b.x),east=Math.max(a.x,b.x),north=Math.min(a.z,b.z),south=Math.max(a.z,b.z);
            Vec3 nw=new Vec3(west,y1,north),ne=new Vec3(east,y1,north),se=new Vec3(east,y1,south),sw=new Vec3(west,y1,south);
            double dy=y0-y1;
            Vec3 nw0=nw.add(0,dy,0),ne0=ne.add(0,dy,0),se0=se.add(0,dy,0),sw0=sw.add(0,dy,0);
            faces.add(new Face(nw,sw,se,ne,UP,color));
            faces.add(new Face(sw0,sw,nw,nw0,new Vec3(-1,0,0),multiply(color,0xffafb5bb)));
            faces.add(new Face(ne0,ne,se,se0,new Vec3(1,0,0),multiply(color,0xffced0cd)));
            faces.add(new Face(nw0,nw,ne,ne0,new Vec3(0,0,-1),multiply(color,0xffb4b7bb)));
            faces.add(new Face(se0,se,sw,sw0,SOUTH,multiply(color,0xffdbd8d4)));
            faces.add(new Face(nw0,ne0,se0,sw0,new Vec3(0,-1,0),multiply(color,0xff77767f)));
        }

        void draw(PoseStack pose,VertexConsumer out,int light,int tint){
            for(var face:faces)face.draw(pose,out,light,tint);
        }
    }
    private WeaveMantle() {}
}
