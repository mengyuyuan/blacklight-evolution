package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Warp the actual posed skin and armor into the wound. The near ribs collapse before the distal limbs. */
final class QuillHostAbsorption {
    private final Vector3f wound;
    private final float age,height,width,side;
    private final QuillTendrils tendrils;

    QuillHostAbsorption(LivingEntity host,QuillOrgan organ,float partial){
        wound=organ.getPosition(partial).subtract(host.getPosition(partial)).toVector3f();
        age=organ.age()+partial;
        height=Math.max(.15F,host.getBbHeight());
        width=Math.max(.15F,host.getBbWidth());
        side=wound.x>=0?1:-1;
        tendrils=QuillTendrils.at(organ,host,new net.minecraft.world.phys.Vec3(wound.x,wound.y,wound.z));
    }
    private static float ease(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    private float reach(Vector3f p){
        float x=(p.x-wound.x)/width,z=(p.z-wound.z)/width,y=(p.y-wound.y)/height;
        return Mth.clamp((float)Math.sqrt(.49F*(x*x+z*z)+1.22F*y*y),0,1.32F);
    }
    boolean exposed(Vector3f p){
        if(age<QuillOrgan.PIERCE_TICK)return false;
        double closest=Double.MAX_VALUE;
        for(var strand:tendrils.strands){
            var entry=strand.puncture();
            double dx=(p.x-entry.x)/width,dy=(p.y-entry.y)/height,dz=(p.z-entry.z)/width;
            closest=Math.min(closest,dx*dx+dy*dy+dz*dz);
            if(age>=QuillOrgan.FIRST_DRAW_TICK){
                var exit=strand.exit();
                dx=(p.x-exit.x)/width;dy=(p.y-exit.y)/height;dz=(p.z-exit.z)/width;
                closest=Math.min(closest,dx*dx+dy*dy+dz*dz);
            }
        }
        if(closest<.045*ease((age-QuillOrgan.PIERCE_TICK)/1.4F))return true;
        float seam=(float)Math.sin(p.y/height*19+p.x/width*5+p.z/width*7);
        return age>QuillOrgan.FIRST_DRAW_TICK+4.6F*reach(p)+.60F*seam;
    }
    int color(Vector3f p){
        float distance=reach(p);
        float direction=(float)Math.atan2((p.z-wound.z)/width,(p.x-wound.x)/width);
        float stripe=(float)Math.sin(6*direction+p.y/height*17+4*distance);
        float wave=(float)Math.exp(-Math.pow((age-QuillOrgan.FIRST_DRAW_TICK-.8+1.9*distance)/1.0,2))
                +(float)Math.exp(-Math.pow((age-QuillOrgan.SECOND_DRAW_TICK-.8+1.9*distance)/1.1,2));
        float red=Mth.clamp(.22F+.45F*Math.max(0,stripe)+.43F*wave,0,1);
        return ((int)(39+130*red)<<16)|((int)(12+23*red)<<8)|(int)(23+28*red);
    }
    void pull(Vector3f p){
        if(age<=QuillOrgan.PIERCE_TICK)return;
        float reach=reach(p);
        float collapse=ease((age-QuillOrgan.FIRST_DRAW_TICK-2.9F*reach)/4.1F);
        // First buckle locally towards the buried tips; only the later draw converges on the carrier.
        double sum=0,tx=0,ty=0,tz=0;
        for(var strand:tendrils.strands){
            var entry=strand.puncture();
            double dx=(p.x-entry.x)/width,dy=(p.y-entry.y)/height,dz=(p.z-entry.z)/width;
            double falloff=1+12*(dx*dx+dy*dy+dz*dz);
            double weight=1/(falloff*falloff*falloff);
            var inner=strand.buried();
            sum+=weight;tx+=weight*inner.x;ty+=weight*inner.y;tz+=weight*inner.z;
        }
        float tug=(float)Math.min(1,sum)*ease((age-QuillOrgan.PIERCE_TICK)/1.65F)*.74F;
        p.x+=((float)(tx/sum)-p.x)*tug;
        p.y+=((float)(ty/sum)-p.y)*tug;
        p.z+=((float)(tz/sum)-p.z)*tug;
        float lower=Mth.clamp(1-p.y/height,0,1);
        float brace=(float)Math.sin(Math.PI*ease((age-QuillOrgan.PIERCE_TICK)/7));
        float x=p.x-wound.x,y=p.y-wound.y,z=p.z-wound.z;
        float twist=side*(.25F+.83F*lower)*collapse;
        float sin=(float)Math.sin(twist),cos=(float)Math.cos(twist);
        float radius=1-collapse;
        p.x=wound.x+(x*cos-z*sin)*radius+side*width*.12F*lower*brace*radius;
        p.z=wound.z+(z*cos+x*sin)*radius;
        p.y=wound.y+y*radius-height*(.05F+.10F*lower)*brace*radius
                +height*.13F*lower*(float)Math.sin(Math.PI*collapse);
    }
}
