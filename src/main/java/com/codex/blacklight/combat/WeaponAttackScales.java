package com.codex.blacklight.combat;

import java.util.*;
import org.joml.Vector3f;

/** Nonuniform arm scales, mapped to the mesh's elbow-to-palm (+Y) axis. */
public final class WeaponAttackScales {
    public record Shape(float fx,float fy,float fz,float px,float py,float pz) {
        public static final Shape IDENTITY=new Shape(1,1,1,1,1,1);
        public Shape blend(Shape b,float t) {
            t=Math.max(0,Math.min(1,t));
            return new Shape(fx+(b.fx-fx)*t,fy+(b.fy-fy)*t,fz+(b.fz-fz)*t,
                    px+(b.px-px)*t,py+(b.py-py)*t,pz+(b.pz-pz)*t);
        }
        private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
        private static float slope(float y,float width){float t=y/width;return t<=0||t>=1?0:6*t*(1-t)/width;}
        private static float integral(float y,float width){
            if(y<=0)return 0;if(y>=width)return y-width*.5F;
            float t=y/width;return width*(t*t*t-.5F*t*t*t*t);
        }
        private float scale(float y,float f,float p){return 1+(f-1)*smooth(y/.30F)+(p-f)*smooth((y-.28F)/.12F);}
        private float derivative(float y,float f,float p){return (f-1)*slope(y,.30F)+(p-f)*slope(y-.28F,.12F);}
        public Vector3f point(Vector3f v){
            float y=v.y;
            return v.set(v.x*scale(y,fx,px),y+(fy-1)*integral(y,.30F)+(py-fy)*integral(y-.28F,.12F),v.z*scale(y,fz,pz));
        }
        /** Inverse-transpose Jacobian of point(), preserving lighting and elbow seams. */
        public Vector3f normal(float x,float y,float z,Vector3f n){
            float sx=scale(y,fx,px),sy=scale(y,fy,py),sz=scale(y,fz,pz);
            float nx=n.x/sx,nz=n.z/sz;
            return n.set(nx,(n.y-x*derivative(y,fx,px)*nx-z*derivative(y,fz,pz)*nz)/sy,nz).normalize();
        }
    }
    private record Clip(float[] times,Shape[][] frames){}
    private static Map<String,Clip> clips;
    private WeaponAttackScales(){}
    public static void preload(){data();}
    private static synchronized Map<String,Clip> data(){
        if(clips!=null)return clips;
        var resource=MotionResources.read("weapon-attack-scales.data",4*1024*1024);
        if(resource.isEmpty())return clips=Map.of();
        var root=resource.orElseThrow();
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Weapon scale schema");
        String[] bones={"Forarm_R","Wrist_R","Forarm_L","Wrist_L"};
        var names=root.getAsJsonArray("bones");
        if(names.size()!=4)throw new IllegalArgumentException("Weapon scale bones");
        for(int i=0;i<4;i++)if(!bones[i].equals(names.get(i).getAsString()))throw new IllegalArgumentException("Weapon scale order");
        Map<String,Clip> result=new HashMap<>();
        for(var entry:root.getAsJsonObject("clips").entrySet()){
            var obj=entry.getValue().getAsJsonObject();var ts=obj.getAsJsonArray("times");var rows=obj.getAsJsonArray("frames");
            if(ts.size()<2||ts.size()>4000||ts.size()!=rows.size())throw new IllegalArgumentException("Weapon scale count");
            float[] times=new float[ts.size()];Shape[][] frames=new Shape[ts.size()][2];
            for(int i=0;i<times.length;i++){
                times[i]=ts.get(i).getAsFloat();
                if(!Float.isFinite(times[i])||times[i]<0||times[i]>30||(i>0&&times[i]<=times[i-1]))throw new IllegalArgumentException("Weapon scale time");
                var row=rows.get(i).getAsJsonArray();if(row.size()!=12)throw new IllegalArgumentException("Weapon scale width");
                float[] values=new float[12];
                for(int j=0;j<12;j++){
                    values[j]=row.get(j).getAsFloat();
                    if(!Float.isFinite(values[j])||values[j]<.1F||values[j]>8)throw new IllegalArgumentException("Weapon scale value");
                }
                for(int side=0;side<2;side++){int k=side*6;frames[i][side]=new Shape(values[k],values[k+1],values[k+2],values[k+3],values[k+4],values[k+5]);}
            }
            result.put(entry.getKey(),new Clip(times,frames));
        }
        return clips=Map.copyOf(result);
    }
    public static Shape sample(String key,float seconds,boolean left){
        if(key==null||!Float.isFinite(seconds))return Shape.IDENTITY;
        var c=data().get(key);
        if(c==null){var old=HammerScales.sample(key,seconds,left);return new Shape(old.forearm(),old.forearm(),old.forearm(),old.fist(),old.fist(),old.fist());}
        int found=Arrays.binarySearch(c.times,seconds),a=found>=0?found:Math.max(0,-found-2),b=Math.min(a+1,c.times.length-1),side=left?1:0;
        float t=a==b?0:(seconds-c.times[a])/(c.times[b]-c.times[a]);
        return c.frames[a][side].blend(c.frames[b][side],t);
    }
}
