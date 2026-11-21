package com.codex.blacklight.client.renderer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.client.renderer.ConsumeExteriorShape.*;

/** A self-authored cluster of heavy folds. The local coordinates run out from the ribs,
 * up the flank and over the shoulder blade. All ends return into the coat. */
final class ConsumeResidueKnot {
    // side, height and back in torso half-extents, then fibre radius and rise in blocks
    private record Point(double side,double up,double back,double radius,double rise){}
    private static Point p(double side,double up,double back,double radius,double rise){
        return new Point(side,up,back,radius,rise);
    }
    private static final Point[][] FOLDS={
        {p(.08,-.54,.91,.015,-.04),p(.42,-.43,1.1,.057,.04),p(.87,-.12,1.14,.096,.09),
         p(1.11,.29,1.05,.101,.12),p(1.04,.59,.76,.088,.12),p(.70,.84,.51,.038,.055),p(.48,.77,.43,.012,-.035)},
        {p(.15,.64,.94,.018,-.02),p(.46,.60,1.19,.074,.065),p(.81,.34,1.24,.108,.14),
         p(1.07,.07,.97,.098,.16),p(1.26,-.14,.44,.077,.10),p(1.14,-.28,.16,.045,.04),p(.93,-.19,-.10,.013,-.04)},
        {p(.93,-.61,.02,.016,-.03),p(1.13,-.44,.31,.054,.04),p(1.23,-.16,.73,.080,.11),
         p(1.27,.14,.86,.078,.13),p(1.19,.38,.67,.060,.09),p(1.03,.48,.22,.033,.035),p(.89,.42,-.05,.012,-.035)},
        {p(.20,.22,.92,.014,-.025),p(.47,.09,1.18,.060,.055),p(.66,.06,1.34,.073,.12),
         p(.94,.26,1.25,.082,.16),p(1.03,.53,.98,.060,.11),p(.81,.69,.77,.030,.06),p(.58,.64,.62,.012,-.02)},
        {p(.96,.62,-.02,.013,-.025),p(1.08,.68,.25,.040,.045),p(1.22,.57,.52,.065,.095),
         p(1.27,.33,.75,.060,.115),p(1.22,.12,.58,.047,.063),p(1.05,-.03,.31,.025,.025),p(.92,.01,.14,.011,-.04)},
        // The medial fold reaches across the back at an angle and slips underneath the shoulder mass.
        {p(-.59,-.04,.92,.016,-.025),p(-.39,.19,1.14,.056,.04),p(-.17,.43,1.26,.074,.08),
         p(.12,.55,1.32,.090,.115),p(.42,.51,1.28,.084,.11),p(.68,.34,1.22,.059,.07),p(.82,.16,.81,.016,-.025)}
    };

    static Strip[] folds(Surface skin,Vec3 facing,boolean flip,float weight,double time,Vec3 movement){
        if(!(weight>.012F))return new Strip[0];
        double shape=Mth.clamp(Math.pow(weight,.74),0,1),model=Mth.clamp(skin.width()/.235,.45,1.65);
        Vec3 back=skin.front().scale(skin.front().dot(facing)>0?-1:1);
        Vec3 side=flip?skin.side().scale(-1):skin.side();
        Vec3 normal=side.scale(.75).add(back.scale(.66)).normalize();
        double stride=Math.min(1,movement.length()*4),pulse=time*(4.8+stride*2.3);
        Strip[] strips=new Strip[FOLDS.length];
        for(int f=0;f<FOLDS.length;f++){
            var row=FOLDS[f];int count=2*row.length-1;
            Vec3[] centers=new Vec3[row.length];double[] radii=new double[row.length];
            for(int i=0;i<row.length;i++){
                var q=row[i];double k=(double)i/(row.length-1),envelope=Math.sin(k*Math.PI);
                double beat=Math.sin(pulse-2.4*k+f*.97),rise=q.rise*model;
                // During retreat the folds recede to a patch of upper ribs and thin simultaneously.
                double x=Mth.lerp(shape,.70,q.side),y=Mth.lerp(shape,.28,q.up),z=Mth.lerp(shape,.76,q.back);
                centers[i]=skin.center().add(side.scale(skin.width()*x)).add(skin.up().scale(skin.half()*y))
                        .add(back.scale(skin.depth()*z)).add(normal.scale(shape*(rise+.008*beat*envelope*model)))
                        .subtract(movement.scale(.11*envelope*shape));
                radii[i]=q.radius*model*shape*(1+.065*envelope*beat);
            }
            Vec3[] points=new Vec3[count];double[] widths=new double[count];
            for(int i=0;i<row.length;i++){
                points[2*i]=i==0||i==row.length-1?centers[i]:centers[i].lerp(centers[i-1].lerp(centers[i+1],.5),.18);
                widths[2*i]=radii[i];
                if(i>0){points[2*i-1]=centers[i-1].lerp(centers[i],.5);widths[2*i-1]=(radii[i-1]+radii[i])*.5;}
            }
            strips[f]=new Strip(points,widths);
        }
        return strips;
    }
    private ConsumeResidueKnot(){}
}
