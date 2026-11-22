package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.SpiderRupture;
import static com.codex.blacklight.client.renderer.BlackTendrilMesh.P;
import static com.codex.blacklight.client.renderer.BlackTendrilMesh.Surface;

/** Each spoke follows two sampled ground contacts. The red seam travels under a darker torn rim. */
public final class SpiderRuptureMesh {
    public static final int SPOKES = 14;
    private static final int CUT=0xff391922, EDGE=0xff84909b, ARMOR=0xff242432, SCAR=0xffb73742, HOT=0xffff9a72;
    private static final P UP = new P(0,1,0);
    private SpiderRuptureMesh() {}
    private static double unit(double v){return Math.min(1,Math.max(0,v));}
    public static P sample(int spoke,boolean outer,double seed){
        double a=spoke*2*Math.PI/SPOKES+seed+(outer?.05*Math.sin(spoke*13+seed):.15*Math.sin(spoke*7));
        double r=SpiderRupture.STAMP_RADIUS*(outer?1:.52);
        return new P(r*Math.cos(a),0,r*Math.sin(a));
    }
    private static P lerp(P a,P b,double u){return a.plus(b.minus(a).scale(u));}
    private static void ribbon(Surface s,P a,P b,double wa,double wb,int color){
        P side=b.minus(a).cross(UP).unit();
        s.quad(a.plus(side.scale(wa)),b.plus(side.scale(wb)),b.minus(side.scale(wb)),a.minus(side.scale(wa)),color);
    }
    private static void thorn(Surface s,P root,P forward,double height,double width,double twist){
        if(height<.035)return;
        P side=forward.cross(UP).unit().scale(twist);
        P[] spine=new P[6],vein=new P[5];
        double[] girth={1,.91,.78,.49,.23,.012};
        for(int n=0;n<6;n++){
            double u=n/5.,bow=Math.sin(Math.PI*u);
            spine[n]=root.plus(forward.scale(height*(.63*u-.56*bow)))
                    .plus(side.scale(height*(.17*bow+.18*u*u))).plus(UP.scale(height*(u+.14*bow)));
            if(n<5)vein[n]=spine[n].plus(forward.scale(-width*.66*girth[n]));
            girth[n]*=width;
        }
        BlackTendrilMesh.strand(s,null,spine,girth,.76,true,false);
        BlackTendrilMesh.strand(s,null,vein,new double[]{width*.09,width*.13,width*.1,width*.065,.009},.63,false,true);
        P ridge=spine[2];
        P spur=ridge.plus(side.scale(.35*height)).plus(UP.scale(.20*height));
        BlackTendrilMesh.strand(s,null,new P[]{ridge,spur,spur.plus(forward.scale(.22*height)).plus(UP.scale(.16*height))},
                new double[]{width*.48,width*.28,.006},.65,true,false);
    }
    public static void spoke(Surface s,int i,double t,P center,P bend,P end){
        if(t<1 || t>=33 || bend==null || end==null)return;
        P lift=UP.scale(.065);
        center=center.plus(lift);bend=bend.plus(lift);end=end.plus(lift);
        boolean anticipation=t<18;
        double phase=t-18,fade=anticipation?1:unit((33-t)/8);
        double breadth=anticipation?.045+.065*unit(t/15):(.23+.12*Math.exp(-phase*.28))*fade;
        // Before impact the furrow grows inward from the limit of the actual damage radius.
        double gather=unit((t-1-(i%3)) / 12);
        P a=anticipation?lerp(bend,center,unit(2*gather-1)):center;
        P b=anticipation?lerp(end,bend,unit(2*gather)):bend;
        ribbon(s,b,end,breadth*.72,breadth*.28,ARMOR);
        ribbon(s,b.plus(UP.scale(.012)),end.plus(UP.scale(.012)),breadth*.34,breadth*.17,anticipation?SCAR:HOT);
        if(gather>.5){
            ribbon(s,a,bend,breadth*1.35,breadth*.82,CUT);
            ribbon(s,a.plus(UP.scale(.013)),bend.plus(UP.scale(.013)),breadth*.27,breadth*.13,SCAR);
        }
        if(anticipation)return;
        P f=end.minus(bend).unit(),across=f.cross(UP).unit();
        double travel=unit(phase/5), twist=Math.sin(i*2.38+.7);
        P fwd=end.minus(bend).unit(),side=fwd.cross(UP).unit();
        P front=travel<.5?lerp(center,bend,2*travel):lerp(bend,end,2*travel-1);
        double crest=Math.sin(Math.PI*unit((phase+.5)/(11+i%4)))*fade;
        // A raised, armoured runner erupts from the footprint and buckles as the front passes.
        P[] runner=new P[7];double[] widths=new double[7];
        for(int n=0;n<7;n++){
            double u=n/6.,dist=u*travel,arch=Math.sin(Math.PI*u);
            P route=dist<.5?lerp(center,bend,2*dist):lerp(bend,end,2*dist-1);
            double ripple=Math.sin(dist*7+i*.8);
            runner[n]=route.plus(side.scale(.35*ripple*arch*crest))
                    .plus(UP.scale((.06+arch*(.26+.21*ripple))*crest));
            widths[n]=(.42*(1-u)+.09)*crest+.009;
        }
        BlackTendrilMesh.strand(s,null,runner,widths,.58,i%3!=1,false);
        double height=(1.28+.53*(i%4))*crest;
        thorn(s,front,fwd,height,.19+.30*crest,twist);
        if(crest>.08){
            P fork=runner[4],branchDir=side.scale(twist<0?-1:1).plus(fwd.scale(.55)).unit();
            P elbow=fork.plus(branchDir.scale(.5*crest)).plus(UP.scale(.2*crest));
            P barb=fork.plus(branchDir.scale(1.12*crest)).plus(UP.scale(.13*crest));
            BlackTendrilMesh.strand(s,null,new P[]{fork,elbow,barb},new double[]{.21*crest,.16*crest,.03},.68,false,true);
            thorn(s,barb,branchDir,(.47+.14*(i%3))*crest,.18*crest,-twist);
            if(i%3!=1)thorn(s,runner[2],fwd,.78*crest,.22*crest,-twist);
        }
    }
    /** Two tapered blood ribbons twist behind each bone dart along its local negative Y axis. */
    public static void dartWake(Surface s,double age){
        double length=2.8*unit(age/2);
        for(int j=0;j<2;j++){
            for(int k=0;k<6;k++){
                double u=k/6.,v=(k+1)/6.;
                P a=wake(u,age,j,length),b=wake(v,age,j,length);
                P ax=new P(.095*(1-u),0,.045*(1-u)),bx=new P(.095*(1-v),0,.045*(1-v));
                s.quad(a.plus(ax),b.plus(bx),b.minus(bx),a.minus(ax),j==0?SCAR:EDGE);
            }
        }
    }
    private static P wake(double u,double age,int side,double length){
        double a=u*6.2+age*.30+side*Math.PI,r=.16*(1-u)+.04;
        return new P(r*Math.cos(a),-.27-length*u,r*Math.sin(a));
    }
}
