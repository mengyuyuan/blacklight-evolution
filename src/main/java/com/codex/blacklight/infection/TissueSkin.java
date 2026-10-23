package com.codex.blacklight.infection;

import java.util.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Bind-time sections let a native cuboid bend between its unchanged joints.
 * Native UVs are retained by the caller; age never adds geometry to the cache. */
public final class TissueSkin {
    private TissueSkin() {}
    public record Triangle(V a,V b,V c) {}
    public static List<Triangle> sections(Bone bone,V a,V b,V c) {
        var box=bone.box();int axis=box.span(0)>box.span(1)&&box.span(0)>box.span(2)?0:box.span(1)>box.span(2)?1:2;
        List<List<V>> polygons=new ArrayList<>();polygons.add(List.of(a,b,c));
        for(int cut=1;cut<3;cut++) {
            float plane=box.low().get(axis)+box.span(axis)*cut/3F;
            List<List<V>> next=new ArrayList<>();
            for(var polygon:polygons) {
                float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
                for(var point:polygon){lo=Math.min(lo,point.get(axis));hi=Math.max(hi,point.get(axis));}
                if(lo>=plane-.00001F||hi<=plane+.00001F){next.add(polygon);continue;}
                next.add(clip(polygon,axis,plane,false));next.add(clip(polygon,axis,plane,true));
            }
            polygons=next;
        }
        var result=new ArrayList<Triangle>();
        for(var polygon:polygons)for(int i=1;i<polygon.size()-1;i++) {
            var x=polygon.getFirst();var y=polygon.get(i);var z=polygon.get(i+1);
            if(y.sub(x).cross(z.sub(x)).length()>.00001F)result.add(new Triangle(x,y,z));
        }
        return List.copyOf(result);
    }
    private static List<V> clip(List<V> polygon,int axis,float plane,boolean positive) {
        var result=new ArrayList<V>();V previous=polygon.getLast();
        boolean wasInside=positive?previous.get(axis)>=plane:previous.get(axis)<=plane;
        for(var point:polygon) {
            boolean inside=positive?point.get(axis)>=plane:point.get(axis)<=plane;
            if(inside!=wasInside){float t=(plane-previous.get(axis))/(point.get(axis)-previous.get(axis));result.add(previous.add(point.sub(previous).mul(t)));}
            if(inside)result.add(point);previous=point;wasInside=inside;
        }
        return result;
    }
    /** Retain texture markings while developed tissue darkens from its own base. */
    public static int tint(int color,long seed,int role,float development) {
        float amount=TissueAnatomy.smooth(development)*(.36F+.20F*TissueGrowth.sample(seed,920+role));
        if(role==1)amount*=1.15F;
        int red=Math.round((color>>>16&255)*(1-.64F*amount));
        int green=Math.round((color>>>8&255)*(1-.84F*amount));
        int blue=Math.round((color&255)*(1-.77F*amount));
        return color&0xff000000|red<<16|green<<8|blue;
    }
    public record Vertex(V position,V rest) {}
    /** One continuous surface field, shared across triangle edges. Independent
     * triangle shrinkage would expose the tessellation as a wire cage. */
    public static List<Vertex> cover(Bone bone,Triangle rest,Triangle posed,long seed,long age,int role) {
        float progress=TissueEvolution.attachmentGrowth(age);
        if(progress<=0)return List.of();
        var input=List.of(new Vertex(posed.a,rest.a),new Vertex(posed.b,rest.b),new Vertex(posed.c,rest.c));
        if(progress>=1)return input;
        var output=new ArrayList<Vertex>();var previous=input.getLast();
        float before=progress-threshold(bone,previous.rest,seed,role);
        for(var point:input) {
            float after=progress-threshold(bone,point.rest,seed,role);
            if((before>=0)!=(after>=0)) {
                float t=before/(before-after);
                output.add(new Vertex(previous.position.add(point.position.sub(previous.position).mul(t)),previous.rest.add(point.rest.sub(previous.rest).mul(t))));
            }
            if(after>=0)output.add(point);
            previous=point;before=after;
        }
        return List.copyOf(output);
    }
    private static float threshold(Bone bone,V point,long seed,int role) {
        var box=bone.box();int axis=box.span(0)>box.span(1)&&box.span(0)>box.span(2)?0:box.span(1)>box.span(2)?1:2;
        int u=(axis+1)%3,v=(axis+2)%3;
        float along=Math.clamp((point.get(axis)-box.low().get(axis))/box.span(axis),0,1);
        if(role==0)along=Math.abs(along-.35F)*1.4F;
        else if(Math.abs(box.low().get(axis))>Math.abs(box.high().get(axis)))along=1-along;
        float x=(point.get(u)-box.low().get(u))/box.span(u),y=(point.get(v)-box.low().get(v))/box.span(v);
        float phase=TissueGrowth.sample(seed,940+role)*6.283185F;
        float edge=.10F+.78F*along+.07F*(float)Math.sin(x*2.4F+phase)+.05F*(float)Math.cos(y*2.7F-phase);
        return Math.clamp(edge,.05F,.95F);
    }
    public static float organGain(TissueCore core,String name) {
        return switch(core) {
            case MUSCLE -> name.startsWith("claw_")||name.startsWith("blade_")||name.equals("ulnar_blade")?1.55F:1;
            case CARAPACE -> name.startsWith("scapular_plate")||name.startsWith("costal_arch")?1.55F:1;
            case JAW -> name.equals("mandible")||name.startsWith("cheek_blade")||name.startsWith("fang_")?1.40F:1;
            case GRASP -> name.startsWith("claw_")||name.startsWith("blade_")?1.35F:1;
            case CREST -> name.startsWith("shoulder_crest")?2.10F:1;
        };
    }
    public static void focus(float[] vertices,float gain) {
        if(gain==1)return;
        V root=new V(0,0,0);for(int i=0;i<24;i+=3)root=root.add(new V(vertices[i],vertices[i+1],vertices[i+2]));root=root.mul(.125F);
        int rings=vertices.length/24;
        for(int ring=0;ring<rings;ring++) {
            V section=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)section=section.add(new V(vertices[i],vertices[i+1],vertices[i+2]));section=section.mul(.125F);
            V centre=root.add(section.sub(root).mul(gain));float girth=1+(gain-1)*.45F;
            for(int i=ring*24;i<ring*24+24;i+=3) {
                V point=new V(vertices[i],vertices[i+1],vertices[i+2]);point=centre.add(point.sub(section).mul(girth));
                vertices[i]=point.x();vertices[i+1]=point.y();vertices[i+2]=point.z();
            }
        }
    }
}
