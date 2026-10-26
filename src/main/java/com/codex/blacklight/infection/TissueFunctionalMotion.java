package com.codex.blacklight.infection;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Continuous articulation of a connected module; the buried first ring remains on the native bone. */
public final class TissueFunctionalMotion {
    private TissueFunctionalMotion(){}
    public static void apply(float[] vertices,Bone bone,String name,TissueCore core,float time,float feeding){
        if(vertices.length<48||bone==null)return;
        if(core==TissueCore.HORN||time<0&&core!=TissueCore.PHARYNX&&core!=TissueCore.TONGUE&&core!=TissueCore.EXTRAORAL)return;
        bone=TissueMorphogenesis.functionalFrame(bone);
        V root=new V(0,0,0);for(int i=0;i<24;i+=3)root=root.add(new V(vertices[i],vertices[i+1],vertices[i+2]));root=root.mul(.125F);
        var cycle=TissueOrganCycle.cycle(core);float effort=cycle.effort(time),strike=time<0?0:TissueOrganCycle.smooth((time-cycle.windup())/Math.max(1,cycle.contact()-cycle.windup()));
        if(time>=cycle.retract())strike*=1-TissueOrganCycle.smooth((time-cycle.retract())/(cycle.end()-cycle.retract()));
        int rings=vertices.length/24;
        V[] centres=null;if(core==TissueCore.HYDROSTAT){centres=new V[rings];for(int ring=0;ring<rings;ring++){
            V centre=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)centre=centre.add(new V(vertices[i],vertices[i+1],vertices[i+2]));centres[ring]=centre.mul(.125F);
        }}
        float length=span(bone.box(),bone.up()),width=span(bone.box(),bone.right());
        for(int i=0;i<vertices.length;i+=3){
            int ring=i/24;float t=ring/(float)(rings-1);
            V p=new V(vertices[i],vertices[i+1],vertices[i+2]),d=p.sub(root);float flex=t*t;
            if(core==TissueCore.TENDON)p=p.add(bone.up().mul(d.dot(bone.up())*-.14F*effort)).add(bone.forward().mul(.7F*effort*flex));
            if(core==TissueCore.HYDRAULIC){float pump=effort*.22F;V along=bone.up().mul(d.dot(bone.up()));p=root.add(along.mul(1+.1F*strike)).add(d.sub(along).mul(1+pump*flex));}
            if(core==TissueCore.HYDROSTAT){
                float side=bone.modelCentre().x()<0?-1:1;
                V extended=root.add(bone.forward().mul(length*.78F*t)).add(bone.right().mul(side*length*.10F*(float)Math.sin(Math.PI*t))).sub(bone.up().mul(length*.08F*t));
                V centre=centres[ring].mul(1-strike).add(extended.mul(strike));
                p=centre.add(p.sub(centres[ring]).mul(1-.15F*strike*t));
            }
            if(core==TissueCore.BLADE){
                float sign=bone.right().cross(bone.up().mul(-1)).dot(bone.forward())<0?-1:1;
                float angle=sign*(-.20F*effort+1.20F*strike)*flex;
                V rotated=d.mul((float)Math.cos(angle)).add(bone.right().cross(d).mul((float)Math.sin(angle)))
                        .add(bone.right().mul(bone.right().dot(d)*(1-(float)Math.cos(angle))));
                p=root.add(rotated);
            }
            if(core==TissueCore.CHELA){float side=(name.endsWith("inner")?-1:1)*(bone.modelCentre().x()<0?-1:1);
                float closed=time<cycle.windup()?-.25F*effort:time>=cycle.retract()?.65F*strike:-.25F+.90F*strike;
                p=p.add(bone.right().mul(-side*width*.14F*closed*flex));}
            if(core==TissueCore.PHARYNX){float extension=(float)Math.sin(Math.PI*Math.clamp(feeding,0,1));
                // Completely folded into the mouth at rest. Extension and return share one curve.
                p=root.add(d.mul(.12F+.88F*extension)).add(bone.forward().mul(2.4F*extension*flex));}
            if(core==TissueCore.SADDLE)p=p.add(bone.up().mul(d.dot(bone.up())*-.20F*effort*flex)).add(bone.forward().mul(width*(strike*.85F-effort*.2F)*flex));
            if(core==TissueCore.CLUB){
                float angle=(-.45F*effort+1.05F*strike)*flex;
                V rotated=d.mul((float)Math.cos(angle)).add(bone.right().cross(d).mul((float)Math.sin(angle)))
                        .add(bone.right().mul(bone.right().dot(d)*(1-(float)Math.cos(angle))));p=root.add(rotated);
            }
            if(core==TissueCore.TONGUE)p=root.add(d.mul(.12F+.88F*strike));
            if(core==TissueCore.EXTRAORAL)p=root.add(d.mul(.18F+.82F*strike));
            if(core==TissueCore.CNIDOCYTE){V axis=bone.up().mul(d.dot(bone.up()));p=root.add(axis).add(d.sub(axis).mul(1+(.24F*effort-.15F*strike)*flex));}
            vertices[i]=p.x();vertices[i+1]=p.y();vertices[i+2]=p.z();
        }
    }
    private static float span(Box box,V direction){return Math.abs(direction.x())*box.span(0)+Math.abs(direction.y())*box.span(1)+Math.abs(direction.z())*box.span(2);}
}
