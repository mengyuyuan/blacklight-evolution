package com.codex.blacklight.client.renderer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;

/** Open tissue paths fitted to the posed torso, forearms and the remaining receiver.
 * Coordinates on each body are azimuth in degrees and height in half-lengths.
 * Paths change surfaces at their contact and emerge elsewhere around the same body. */
final class ConsumeExteriorShape {
    enum Part { GIVER, PREY, RIGHT_ARM, LEFT_ARM }
    record Surface(Vec3 center,Vec3 up,Vec3 side,Vec3 front,double width,double depth,double half){
        static Surface torso(ConsumeSocketFit.Skin skin,double width,double depth){
            return new Surface(skin.center(),skin.up(),skin.side(),skin.front(),
                    Math.max(.035,width),Math.max(.025,depth),skin.height());
        }
        static Surface arm(Vec3 elbow,Vec3 wrist,Vec3 fallback){
            Vec3 along=wrist.subtract(elbow);double length=along.length();if(length<.04)return null;
            Vec3 up=along.scale(1/length),side=project(fallback,up);
            return new Surface(elbow.lerp(wrist,.5),up,side,side.cross(up),.127,.119,length*.53);
        }
        Vec3 point(Vec3 facing,double angle,double h,double clearance){
            var forward=project(facing,up);
            var radial=forward.scale(Math.cos(angle)).add(up.cross(forward).scale(Math.sin(angle)));
            // Rounded block cross-section. Cords remain on the corners of an MC coat when the trunk twists.
            double x=Math.abs(radial.dot(side))/width,z=Math.abs(radial.dot(front))/depth;
            double reach=1/Math.cbrt(x*x*x+z*z*z);
            return center.add(up.scale(h*half)).add(radial.scale(reach+clearance));
        }
    }
    record Bodies(Surface giver,Surface prey,Surface rightArm,Surface leftArm,Vec3 direction,Vec3 movement){
        Surface surface(Part part){return switch(part){case GIVER->giver;case PREY->prey;case RIGHT_ARM->rightArm;case LEFT_ARM->leftArm;};}
    }
    record Pin(Part part,double angle,double height) {}
    record Ribbon(double width,double delay,Pin[] pins){}
    record Strip(Vec3[] points,double[] widths){}
    private static Pin a(double angle,double h){return new Pin(Part.GIVER,angle,h);}
    private static Pin p(double angle,double h){return new Pin(Part.PREY,angle,h);}
    private static Pin r(double angle,double h){return new Pin(Part.RIGHT_ARM,angle,h);}
    private static Pin l(double angle,double h){return new Pin(Part.LEFT_ARM,angle,h);}
    private static Ribbon cord(double width,double delay,Pin... pins){return new Ribbon(width,delay,pins);}

    // One broad scapular fold, two oblique trunk paths, smaller side seams, then the working forearms.
    // None is a closed loop, and each path has its own high and low contact points.
    static final Ribbon[] GIVER={
        cord(.061,.00,a(28,-.51),a(69,.51),a(118,1.07),a(187,.64),a(240,.12),a(286,.32)),
        cord(.043,.10,a(-5,.82),a(-57,.53),a(-104,.16),a(-165,-.19),a(-227,-.69)),
        cord(.051,.17,a(202,-.84),a(145,-.50),a(99,.01),a(61,.37),a(2,.68),a(-31,.54)),
        cord(.028,.27,a(-88,-.74),a(-154,-.51),a(-188,-.30),a(-243,.25),a(-263,.76)),
        cord(.040,.08,a(-173,.55),a(-106,1.06),a(-69,.75),r(31,-.78),r(104,-.10),r(213,.73)),
        cord(.033,.24,a(151,.90),a(99,.61),l(-45,-.78),l(-123,-.31),l(-218,.57)),
        cord(.023,.38,a(36,.68),a(-10,.39),a(-44,.10),a(-101,-.34),a(-137,-.41))
    };
    // A shoulder or hip enters the prey on its near side. The fibre sweeps over its outer flank
    // before turning back to a different rib. Keep a few larger gaps where hands/face can still read.
    static final Ribbon[] PAIR={
        cord(.062,.00,a(143,.85),a(83,.94),a(36,.63),p(154,.34),p(104,.76),p(49,.89),p(-13,.43),p(-67,-.08)),
        cord(.049,.08,a(-146,-.66),a(-81,-.47),a(-27,-.10),p(-145,-.61),p(-89,-.42),p(-19,.05),p(58,.32),p(98,.04)),
        cord(.038,.18,p(42,-.61),p(104,-.22),p(163,.47),a(45,.51),a(97,.28),a(157,-.17),a(193,-.67)),
        cord(.044,.13,a(-112,.79),a(-46,.63),p(-165,.67),p(-107,.31),p(-62,-.37),p(29,-.72),p(78,-.55)),
        cord(.030,.32,a(121,-.71),a(49,-.45),p(141,-.29),p(82,.05),p(52,.52),p(1,.77),p(-35,.62)),
        cord(.027,.40,p(-59,-.66),p(-103,-.24),p(-152,.17),a(-23,.32),a(-67,.73),a(-141,.95)),
        cord(.037,.22,a(167,.07),a(107,-.10),a(43,-.38),p(178,-.61),p(121,-.39),p(86,.15))
    };
    private static Vec3 project(Vec3 v,Vec3 axis){
        Vec3 result=v.subtract(axis.scale(v.dot(axis)));
        if(result.lengthSqr()<.008){Vec3 alternate=Math.abs(axis.y)<.82?new Vec3(0,1,0):new Vec3(0,0,1);
            result=alternate.subtract(axis.scale(alternate.dot(axis)));}
        return result.normalize();
    }
    static Strip strip(Ribbon ribbon,int index,Bodies bodies,float weight,double age){
        double advance=Mth.clamp((weight-ribbon.delay)/(1-ribbon.delay),0,1);
        if(advance<.012)return null;
        Pin[] pins=ribbon.pins;
        for(var pin:pins)if(bodies.surface(pin.part)==null)return null;
        final int steps=5,max=(pins.length-1)*steps;
        double extent=advance*max;int last=Math.min(max,(int)Math.ceil(extent));
        Vec3[] points=new Vec3[last+1];double[] widths=new double[last+1];
        for(int n=0;n<=last;n++){
            double cursor=Math.min(n,extent),t=cursor/max;
            int k=Math.min(pins.length-2,(int)(cursor/steps));double u=cursor/steps-k;
            Pin from=pins[k],to=pins[k+1];
            double phase=index*1.783+t*4.2,beat=Math.sin(4.6*age-phase);
            double edge=Math.sin(Math.PI*t),clear=.014+.019*edge+.009*beat*edge;
            // Short entering and exiting ends join the skin. The ridges stand proud in between.
            clear-=.047*Math.max(0,1-8*Math.min(t,1-t));
            if(from.part==to.part){
                double theta=Math.toRadians(Mth.lerp(u,from.angle,to.angle));
                double height=Mth.lerp(u,from.height,to.height)+.024*Math.sin(Math.PI*u)*Math.sin(phase);
                points[n]=bodies.surface(from.part).point(bodies.direction,theta,height,clear);
            }else{
                Vec3 start=bodies.surface(from.part).point(bodies.direction,Math.toRadians(from.angle),from.height,clear);
                Vec3 end=bodies.surface(to.part).point(bodies.direction,Math.toRadians(to.angle),to.height,clear);
                double arc=Math.sin(Math.PI*u);
                Vec3 flank=bodies.giver.up.cross(bodies.direction).normalize().scale(index%2==0?.052:-.052);
                points[n]=start.lerp(end,u).add(flank.scale(arc)).subtract(bodies.movement.scale(.36*arc));
            }
            double taper=Math.min(1,Math.min(.35+t*7,.19+(extent-cursor)*.50));
            widths[n]=ribbon.width*taper*(.75+.25*advance)*(.94+.1*Math.sin(phase+age*2.2));
        }
        // Preserve the sockets, relax the small changes of direction between authored surface anchors.
        var raw=Arrays.copyOf(points,points.length);
        for(int n=1;n<last;n++)points[n]=raw[n].lerp(raw[n-1].lerp(raw[n+1],.5),.27);
        return new Strip(points,widths);
    }
    private ConsumeExteriorShape(){}
}
