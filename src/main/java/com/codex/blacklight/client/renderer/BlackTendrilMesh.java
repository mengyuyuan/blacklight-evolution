package com.codex.blacklight.client.renderer;

/** Local block-space surfaces used both by the planted hazard and the seed in flight.
 * Each limb uses continuous elliptical rings, a dorsal highlight and a tapered blade. */
public final class BlackTendrilMesh {
    private BlackTendrilMesh(){}
    public record P(double x,double y,double z){
        P plus(P p){return new P(x+p.x,y+p.y,z+p.z);}
        P minus(P p){return new P(x-p.x,y-p.y,z-p.z);}
        P scale(double s){return new P(x*s,y*s,z*s);}
        P cross(P p){return new P(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        P unit(){return scale(1/Math.max(1e-8,Math.sqrt(x*x+y*y+z*z)));}
    }
    public interface Surface { void quad(P a,P b,P c,P d,int pigment); }
    public interface Obstacle { P stop(P from,P to); }
    private static final int INK=0xff101119, CHITIN=0xff262833, STEEL=0xff3d4353, EDGE=0xff737f8a;
    private static final int FISSURE=0xff78424e, FIBRE=0xff302633;
    private static final double TAU=2*Math.PI;
    private static final P UP=new P(0,1,0);
    private static double ease(double t,double a,double b){
        double u=Math.min(1,Math.max(0,(t-a)/(b-a)));return u*u*(3-2*u);
    }
    private static P radial(double a,double r,double y){return new P(r*Math.cos(a),y,r*Math.sin(a));}
    static int strand(Surface s,Obstacle clip,P[] spine,double[] width,double flat,boolean silver,boolean vein){
        int sides=8;P[] last=null;P lastTangent=null;
        for(int j=0;j<spine.length;j++){
            boolean stop=false;
            if(j>0 && clip!=null){
                P h=clip.stop(spine[j-1],spine[j]);
                if(h!=null){spine[j]=h;stop=true;}
            }
            P axis=(j==0?spine[1].minus(spine[0]):(j==spine.length-1 || stop?spine[j]:spine[j+1]).minus(spine[j-1])).unit();
            P tangent=lastTangent==null?axis.cross(Math.abs(axis.y)>.96?new P(0,0,1):UP).unit():axis.cross(lastTangent).cross(axis).unit();
            lastTangent=tangent;
            P normal=tangent.cross(axis).unit();
            P[] ring=new P[sides];
            for(int k=0;k<sides;k++){
                double angle=k*TAU/sides;
                ring[k]=spine[j].plus(tangent.scale(Math.cos(angle)*width[j])).plus(normal.scale(Math.sin(angle)*width[j]*flat));
            }
            if(last!=null)for(int k=0;k<sides;k++){
                int n=(k+1)%sides;
                int color=vein?(k<=1?FISSURE:FIBRE):k==0?(silver && j%5>=2?EDGE:STEEL):k==1||k==7?CHITIN:INK;
                s.quad(last[k],ring[k],ring[n],last[n],color);
            }
            last=ring;if(stop)return j;
        }
        return spine.length;
    }
    /** Serrated blood seed with a pale bone point, three keratin hooks and three trailing fibres. */
    static void dart(Surface s){
        double[] y={-.52,-.41,-.21,.03,.24,.41,.71};
        double[] radius={.046,.13,.205,.23,.16,.085,.003};
        int[] meat={0xff611d32,0xff862e44,0xff782638,0xff511a2a,0xff913246,0xff6c2238,0xff471827,0xff702436};
        int[] bone={0xffb99d79,0xffddc8a0,0xffc4af8d,0xffa58b70};
        for(int j=0;j<y.length-1;j++)for(int i=0;i<8;i++){
            double a=i*TAU/8,b=(i+1)*TAU/8;
            s.quad(radial(a,radius[j],y[j]),radial(b,radius[j],y[j]),radial(b,radius[j+1],y[j+1]),
                    radial(a,radius[j+1],y[j+1]),j==5?bone[i%4]:meat[(i+j/2)%8]);
        }
        for(int i=0;i<3;i++){
            double a=i*TAU/3;
            strand(s,null,new P[]{radial(a,.065,.44),radial(a,.19,.21),radial(a+.09,.30,-.01),
                    radial(a+.16,.39,-.18),radial(a+.20,.39,-.32),radial(a+.21,.34,-.41)},
                    new double[]{.017,.075,.097,.078,.041,.006},.54,true,false);
            strand(s,null,new P[]{radial(a,.038,-.47),radial(a+.16,.054,-.59),
                    radial(a+.50,.099,-.70),radial(a+.88,.092,-.82)},
                    new double[]{.036,.031,.022,.005},.83,false,true);
        }
    }
    public static void shell(Surface s,double t,double seed,double fuse,double pop){
        double charge=ease(t,2,fuse-3);
        double beat=Math.pow(.5+.5*Math.cos((t-12)*TAU/10),5);
        double split=ease(t,fuse-5,fuse+1);
        double sink=1-.78*pop;
        for(int i=0;i<8;i++){
            double a=seed+i*TAU/8;
            P[] spine=new P[8];double[] widths=new double[8];
            for(int j=0;j<8;j++){
                double u=j/7.,bow=Math.sin(Math.PI*u);
                double r=.20+.40*bow+.095*beat*charge*bow+.48*split*u*u;
                double y=(.06+(1.05+.24*charge+.12*beat)*u-.52*split*u*u)*sink;
                spine[j]=radial(a+.42*u-.29*split*u,r*(1-.46*pop),y);
                widths[j]=(.012+.23*Math.pow(bow,.66))*(1-.42*pop);
            }
            strand(s,null,spine,widths,.59,i%3==1,false);
            // Overlapping chevrons catch a narrow grazing highlight on each armoured lobe.
            for(int row=2;row<=5;row++){
                P center=spine[row].plus(radial(a,.053,0));
                P across=radial(a+Math.PI/2,widths[row]*.84,0);
                P peak=center.plus(radial(a,.073,.068));
                strand(s,null,new P[]{center.minus(across),peak,center.plus(across)},new double[]{.009,.029,.007},.58,row==4,false);
            }
            if(charge>.05 && i%2==0){
                P[] fissure=new P[5];double[] thin=new double[5];
                for(int j=0;j<5;j++){
                    double u=j/4.;
                    fissure[j]=radial(a+.38+.22*u,.34+.17*Math.sin(Math.PI*u),(.23+.73*u)*sink);
                    thin[j]=.011+(.026+.035*beat)*Math.sin(Math.PI*u)*charge;
                }
                strand(s,null,fissure,thin,1,false,true);
            }
        }
    }
    public static void draw(Surface s,Obstacle clip,double t,double seed,double fuse,double radius){
        double ready=ease(t,2,fuse-7),snap=ease(t,fuse-3,fuse),unwind=ease(t,fuse+1,fuse+10);
        double pop=ease(t,fuse-2,fuse+3);
        double fade=1-ease(t,fuse+6,fuse+12);
        if(fade<=0)return;
        shell((a,b,c,d,rgb)->s.quad(a.scale(fade),b.scale(fade),c.scale(fade),d.scale(fade),rgb),t,seed,fuse,pop);
        for(int i=0;i<11;i++){
            boolean blade=i%3!=1;
            double a=seed+i*TAU/11;
            double whip=Math.sin(i*14.03+seed);
            double reach=(.76+ready*(.95+.22*whip))*(1-.25*ease(t,fuse-7,fuse-3));
            reach+=(radius-(blade?0:.38)-reach)*snap;
            reach*=1-.43*unwind;
            P[] spine=new P[11];double[] widths=new double[11];
            for(int j=0;j<11;j++){
                double u=j/10.,arch=Math.sin(Math.PI*u);
                double coil=(1-snap)*(.65+.24*whip)*Math.sin(u*4.3-t*.085+i*.32);
                double kink=.22*whip*snap*Math.sin(6*u-1.1)+.24*snap*u*u*Math.sin(4*u+i);
                double curl=unwind*1.21*u*u+kink;
                double y=.077+.22*(1-u)+arch*(.12+snap*(blade?.58:.26))+.10*whip*arch
                    +(.23+.37*(1+whip))*u*u*(snap*(1-unwind))+.26*unwind*arch*Math.sin(u*5.7+i);
                spine[j]=radial(a+coil+curl,.26+u*(reach-.26),Math.max(.043,y)*fade);
                double harpoon=(blade?.135:.036)*Math.pow(Math.max(0,1-Math.abs(u-.79)/.19),1.5)*snap;
                widths[j]=(.017+(blade?.163:.084)*Math.pow(1-u,.72)+harpoon)*fade;
                if(j==10)widths[j]=.004*fade;
            }
            int clear= strand(s,clip,spine,widths,blade?.74:.93,blade,false);
            // Hooked secondary lashes split from the parent, travelling on the same pressure wave.
            for(int j=3;j<=7 && j+1<clear;j+=2){
                double u=j/10.,side=(i+j)%4<2?1:-1;
                P base=spine[j];P forward=spine[j+1].minus(spine[j-1]).unit();
                P cross=forward.cross(UP).unit().scale(side);
                double spread=(.09+.54*snap+.13*ready)*fade*(1-.5*unwind);
                P shoulder=base.plus(cross.scale(.23*spread)).plus(forward.scale(.31*spread)).plus(new P(0,.12*spread,0));
                P barb=base.plus(cross.scale(.79*spread)).minus(forward.scale((.25+.20*u)*spread)).plus(new P(0,.29*spread,0));
                P hook=barb.minus(forward.scale(.44*spread)).plus(cross.scale(.10*spread));
                strand(s,clip,new P[]{base,shoulder,barb,hook},new double[]{.025*fade,.083*fade,.037*fade,.002},.54,j==5,false);
            }
        }
        // Ejected pieces follow ballistic arcs while the planted tendons curl back.
        if(t>fuse-1 && t<fuse+10)for(int i=0;i<9;i++){
            double u=Math.min(1,(t-fuse+1)/11),a=seed+i*2.4,rad=(.4+u*(1.3+(i%4)*.39));
            P p=radial(a,rad,.20+4*(.6+.14*(i%5))*u*(1-u));
            P tail=radial(a,.09+.10*(i%3),.22*(1-2*u)).scale(fade);
            if(clip==null || clip.stop(new P(0,.38,0),p)==null)
                strand(s,null,new P[]{p.minus(tail),p,p.plus(tail.scale(.46))},new double[]{.004,.06*fade,.004},.43,true,false);
        }
    }
}
