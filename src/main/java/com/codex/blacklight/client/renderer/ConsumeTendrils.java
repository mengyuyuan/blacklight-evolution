package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Continuous tapered tissue, grown from the consumer and wrapped around posed anatomy. */
public final class ConsumeTendrils {
    public record Attachment(Vec3 center,double radius) {}
    private ConsumeTendrils(){}
    public static void draw(PoseStack stack,VertexConsumer out,int light,List<Attachment> body,Vec3 chest,ConsumeFracture.Phase phase,int seed){
        boolean connected=phase.style()==ConsumeFracture.Style.LASH;
        double matter=connected?1-smooth(phase.returnFlow()/.7):1;
        if(matter<=.001||body.isEmpty())return;
        if(connected){
            // A connected receiver contracts as one body. Emitting two full-width
            // cables per limb produced a parallel rack after that body disappeared.
            Vec3 center=Vec3.ZERO;double total=0,radius=0;
            for(var part:body){double weight=Math.max(.0001,part.radius*part.radius);center=center.add(part.center.scale(weight));total+=weight;radius=Math.max(radius,part.radius);}
            body=List.of(new Attachment(center.scale(1/total),radius));
        }
        float age=phase.progress()-phase.contact();
        boolean source=phase.nativeEvents();
        double reach=smooth((age+(source?0:.08))/.115),pull=smooth((age-.10)/Math.max(.12,.86-phase.contact()-.10));
        double fade=1-smooth((phase.returnFlow()-.72)/.28);
        if(reach<=.001||fade<=.001)return;
        for(int i=0;i<body.size();i++){
            Attachment part=body.get(i);Vec3 delta=part.center.subtract(chest);double length=delta.length();
            if(length<.035||length>5)continue;
            Vec3 axis=delta.scale(1/length),u=axis.cross(new Vec3(0,1,0)).normalize();if(u.lengthSqr()<.1)u=new Vec3(1,0,0);
            Vec3 v=axis.cross(u).normalize();
            for(int strand=0;strand<(connected?1:2);strand++){
                double angle=i*2.399963+seed*.19+strand*Math.PI;
                Vec3 radial=u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
                Vec3 start=chest.add(radial.scale(.08*fade));
                double radius=net.minecraft.util.Mth.clamp(part.radius,.035,.55),coil=radius*(1-.28*pull);
                Vec3 latch=part.center.add(radial.scale(coil)).subtract(axis.scale(radius*.18));
                Vec3 c1=start.add(axis.scale(length*.27)).add(radial.scale((.13+length*.10)*(1-pull*.72)));
                Vec3 c2=latch.subtract(axis.scale(length*.23)).add(radial.scale(.12*(1-pull)));
                Vec3[] points=new Vec3[29];double[] widths=new double[points.length];
                double base=(connected?.052:strand==0?.073:.043)*com.codex.blacklight.forge.WingMath.clamp(.8+radius,.9,1.3)*fade*Math.sqrt(matter);
                for(int j=0;j<points.length;j++){
                    double fraction=j/(double)(points.length-1),t=fraction*reach;
                    Vec3 p;
                    if(t<=.68){p=bezier(start,c1,c2,latch,t/.68);}
                    else {
                        double wrap=(t-.68)/.32,a=angle+wrap*Math.PI*(1.65-.65*pull)*(strand==0?1:-1);
                        Vec3 around=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                        p=part.center.add(around.scale(coil*(1-.12*wrap))).add(axis.scale(radius*(wrap*.36-.18)));
                    }
                    double ripple=Math.sin(t*13+phase.progress()*36+i)*.014*Math.sin(Math.PI*t)*(1-pull);
                    points[j]=p.add(v.scale(ripple));
                    // The traveling bulge moves toward the consumer as tissue retracts.
                    double pulse=1+.15*Math.sin(t*18+phase.progress()*55+i);
                    widths[j]=base*Math.pow(1-fraction,.65)*pulse;
                }
                sweep(stack,out,light,points,widths,u);
            }
        }
    }
    private static Vec3 bezier(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double t){double s=1-t;return a.scale(s*s*s).add(b.scale(3*s*s*t)).add(c.scale(3*s*t*t)).add(d.scale(t*t*t));}
    private static double smooth(double t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    /** Transport one frame along the curve: adjacent rings share vertices and never
     * leave the gaps produced by independently oriented constant-width cylinders. */
    private static void sweep(PoseStack stack,VertexConsumer out,int light,Vec3[] points,double[] widths,Vec3 initial){
        int sides=10;Vec3[][] rings=new Vec3[points.length][sides],normals=new Vec3[points.length][sides];Vec3 u=initial;
        for(int j=0;j<points.length;j++){
            Vec3 tangent=points[Math.min(j+1,points.length-1)].subtract(points[Math.max(0,j-1)]).normalize();
            if(tangent.lengthSqr()<.1)tangent=new Vec3(0,1,0);
            u=u.subtract(tangent.scale(u.dot(tangent))).normalize();
            if(u.lengthSqr()<.1){u=tangent.cross(new Vec3(0,1,0)).normalize();if(u.lengthSqr()<.1)u=new Vec3(1,0,0);}
            Vec3 v=tangent.cross(u).normalize();
            for(int k=0;k<sides;k++){
                double angle=k*Math.PI*2/sides;Vec3 n=u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
                normals[j][k]=n;rings[j][k]=points[j].add(n.scale(widths[j]));
            }
        }
        for(int j=1;j<points.length;j++)for(int k=0;k<sides;k++){
            int next=(k+1)%sides;int color=k==2?0xff692630:k==3?0xff46202a:k==7?0xff393039:0xff20171d;
            vertex(stack,out,light,rings[j-1][k],normals[j-1][k],color);vertex(stack,out,light,rings[j][k],normals[j][k],color);
            vertex(stack,out,light,rings[j][next],normals[j][next],color);vertex(stack,out,light,rings[j-1][next],normals[j-1][next],color);
        }
    }
    private static void vertex(PoseStack stack,VertexConsumer out,int light,Vec3 p,Vec3 n,int color){
        out.vertex((stack.last()).pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal((stack.last()).normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
