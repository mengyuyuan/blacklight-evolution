package com.codex.blacklight.infection;

import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** A fused host is a rebuilt organism, not a collection of larger attachments.
 * The same bounded field develops skin, sockets, jaws and hard organs. Native
 * joints keep their animation; feet and inter-leg walking corridors stay clear. */
public final class TissueAggregate {
    public record Form(float amount,float radial,float quadruped,float upright,float handedness) {
        public static final Form NONE=new Form(0,0,0,0,0);
        public boolean active(){return amount>0;}
    }
    private TissueAggregate(){}
    public static Form sample(TissueInheritance inventory,TissueCore born,double visualWork){
        float formed=0,radial=0,quadruped=0,upright=0,weight=0,diversity=0,handedness=0;
        for(var core:TissueCore.values()){
            // A tendon, pincer or hydraulic joint has its own socket and cannot mint a generic giant thorax/head.
            if(!core.legacy())continue;
            var organ=inventory.organ(core);if(organ==null||core==born||organ.integrated()+organ.pending()<=0)continue;
            float g=TissueGraftLayout.growth(organ,visualWork);formed=Math.max(formed,g);diversity+=g;
            // Divide before adding: two valid, extremely large saved strengths
            // must not overflow into Infinity and turn every blended vertex NaN.
            float share=g*(float)Math.log1p(organ.integrated()/100+organ.pending()/100);
            if(organ.donorPlan()==2)radial+=share;
            else if(organ.donorPlan()==1)quadruped+=share;
            else if(organ.donorPlan()==0)upright+=share;
            weight+=share;handedness+=share*(TissueGrowth.sample(TissueGrowth.mix(organ.variant()+core.ordinal()),933)*2-1);
        }
        if(formed<=0)return Form.NONE;
        float strength=.85F+.12F*Math.min(3,Math.max(0,diversity-1));
        return new Form(formed*strength,weight>0?radial/weight:0,weight>0?quadruped/weight:0,
                weight>0?upright/weight:0,weight>0?handedness/weight:0);
    }
    public static V develop(Bone bone,V point,Form form,boolean groundSupport){
        if(!form.active())return point;
        var box=bone.box();var centre=box.centre();var d=point.sub(centre);
        V right=bone.right(),up=bone.up(),front=bone.forward();float a=form.amount();
        if(bone.role()==0){
            boolean upright=span(box,up)>=span(box,front)*.85F;
            V along=upright?up:front,dorsal=upright?front.mul(-1):up;
            float t=Math.clamp(d.dot(along)/span(box,along)+.5F,0,1);
            float shoulder=(float)Math.exp(-Math.pow((t-.70F)/.34F,2));
            // Broad connected thorax, pinched abdomen and an off-centre dorsal
            // keel replace the straight native trunk with a new weight-bearing shape.
            return point.add(right.mul(d.dot(right)*a*(.30F+.65F*shoulder+.15F*form.radial())))
                    .add(dorsal.mul(d.dot(dorsal)*a*(.25F+.45F*shoulder)))
                    .add(dorsal.mul(span(box,dorsal)*a*.22F*shoulder))
                    .add(along.mul(span(box,along)*a*(.10F*form.quadruped()+.05F*form.upright())*shoulder))
                    .add(right.mul(span(box,right)*a*.07F*form.handedness()*shoulder));
        }
        if(bone.role()==1){
            float depth=span(box,front),height=span(box,up);
            float face=Math.clamp(d.dot(front)/depth+.5F,0,1);
            float neck=Math.clamp((point.dot(up)-(centre.dot(up)-height*.5F))/height,0,1);
            return point.add(front.mul(depth*a*(.22F*neck+(.32F+.14F*form.quadruped())*face*face)))
                    .add(right.mul(d.dot(right)*a*(.25F+.32F*form.radial())*face))
                    .add(up.mul(-height*a*.12F*face));
        }
        if(bone.role()<2||bone.role()>5)return point;
        V along=axis(box);float length=span(box,along);
        float sign=Math.abs(box.low().dot(along))>Math.abs(box.high().dot(along))?-1:1;
        float t=Math.clamp(.5F+sign*d.dot(along)/length,0,1);
        boolean planted=groundSupport&&along.mul(sign).dot(up)<-.65F;
        boolean arm=!groundSupport;float side=bone.modelCentre().x()<0?-1:1;
        float dominant=1+.22F*side*form.handedness();
        V section=centre.add(along.mul(d.dot(along))),radial=point.sub(section);
        float proximal=(float)Math.sin(Math.PI*Math.clamp(t*.85F+.08F,0,1));
        float girth=1+a*(arm?.75F:.35F)*proximal*dominant;
        V result=section.add(radial.mul(girth));
        // Elbow/foreleg bends change the whole limb silhouette, rather than
        // stretching only the weapon tip. The native joint root stays fixed.
        result=result.add(front.mul(length*a*(arm?.22F:.10F)*(float)Math.sin(Math.PI*t)*dominant));
        if(arm)result=result.add(along.mul(sign*length*a*.18F*t*t*dominant));
        if(planted){
            float floor=centre.dot(up)-span(box,up)*.5F;
            result=result.add(up.mul(Math.max(0,floor-result.dot(up))));
        }
        return TissueSilhouette.fit(bone,result);
    }
    private static V axis(Box b){return b.span(0)>b.span(1)&&b.span(0)>b.span(2)?new V(1,0,0):b.span(1)>b.span(2)?new V(0,1,0):new V(0,0,1);}
    private static float span(Box b,V v){return Math.max(.001F,Math.abs(v.x())*b.span(0)+Math.abs(v.y())*b.span(1)+Math.abs(v.z())*b.span(2));}
}
