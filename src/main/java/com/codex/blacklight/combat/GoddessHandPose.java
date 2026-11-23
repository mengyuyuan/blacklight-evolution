package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A floating wrist, palm arches and five articulated digits, shared by skin, picks and muzzle positions. */
public final class GoddessHandPose {
    public static final int WRIST=12, PALM=13, THUMB=14;
    public record Link(int slot,Vec3 base,Vec3 end,double width,double depth,Vec3 side) {
        public Vec3 middle(){return base.lerp(end,.5);}
        public Vec3 up(){return end.subtract(base).normalize();}
        public Vec3 right(){var up=up();return side.subtract(up.scale(side.dot(up))).normalize();}
        public Vec3 normal(){return right().cross(up()).normalize();}
        HydraContact.Box surface(){return HydraContact.affine(middle(),new Vec3(width,base.distanceTo(end)*.5,depth),right(),up(),normal());}
    }
    private static double ease(double t){t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
    static double bend(int move,int digit,double t){
        return switch(move){
            case 4 -> t<0?.06+.75*(1-ease((-t-1-digit*2.8)/11)):.06+t*(digit==0?1.68:1.46);
            case 0 -> {
                double fire=digit==2?39:13;
                double fold=ease((t-(digit==2?15:0))/8)*(1-ease((t-fire+2)/3));
                yield digit<=2?.12+1.08*fold:.46+.13*Math.sin(t*.12+digit);
            }
            case 1 -> .22+.92*(1-ease((t-(17+(4-digit)*7))/5));
            case 2 -> .10+1.27*ease((t-digit*2)/17)*(1-ease((t-37)/4));
            default -> .18+1.05*ease(t/18)*(1-ease((t-40)/5));
        };
    }
    private final List<Link> links=new ArrayList<>(20);
    private final Vec3[] tips=new Vec3[5];
    private final Vec3 origin,right,up,front;
    private final double scale;
    GoddessHandPose(Vec3 anchor,float yaw,int side,int pattern,float t,int lost,double size,double clock,double recoil){
        scale=size;
        var facing=Vec3.directionFromRotation(0,yaw);
        var lateral=new Vec3(facing.z,0,-facing.x);
        double sweep=pattern==1?(ease((t-8)/38)-.5)*.89:pattern==3?-.52*ease(t/18)+.95*ease((t-32)/10):0;
        front=facing.yRot((float)(sweep*side));
        right=new Vec3(front.z,0,-front.x).scale(side);
        double pitch=(pattern==2?-.33*ease(t/26)+.66*ease((t-38)/3)*(1-ease((t-42)/15)):0)+recoil*.40;
        up=new Vec3(front.x*Math.sin(pitch),Math.cos(pitch),front.z*Math.sin(pitch));
        origin=pattern==4?anchor:anchor.add(0,.22*Math.sin(clock*.071),0).add(lateral.scale(.56*Math.sin(clock*.047)))
                .subtract(front.scale(recoil*.9));
        Vec3 wrist=world(0,-2.0,-.4);
        link(WRIST,world(-.22,-4.9,-1.05),wrist,1.05,.80);
        for(int i=-1;i<=1;i++)link(PALM,world(i*.65,-2.24,-.32),world(i*1.58,1.70-.18*Math.abs(i),.12-.22*Math.abs(i)),.89,.77);
        double[] lengths={1.28,1.67,1.83,1.68,1.33};
        for(int finger=0;finger<5;finger++){
            if((lost&(1<<finger))!=0)continue;
            double x=finger==0?-1.89:(finger-2.55)*1.15;
            double y=finger==0?-.75:1.58-.18*Math.abs(finger-2);
            double z=finger==0?.20:.01;
            var from=world(x,y,z);
            double curl=bend(pattern,finger,pattern==4?t:Math.max(0,t));
            for(int j=0;j<3;j++){
                double a=curl*(.55+j*.94);
                double len=lengths[finger]*(1-j*.16);
                x+=finger==0?(-.85*Math.cos(a)+.80*Math.sin(a))*len:(finger-2.55)*.13*Math.cos(a)*len;
                y+=(finger==0?.52:.99)*len*Math.cos(a);
                z+=len*Math.sin(a);
                var to=world(x,y,z);
                link(THUMB+finger,from,to,(finger==0?.64:.50)-j*.065,.51-j*.065);
                from=to;
            }
            tips[finger]=from;
        }
    }
    private Vec3 world(double x,double y,double z){return origin.add(right.scale(x*scale)).add(up.scale(y*scale)).add(front.scale(z*scale));}
    private void link(int slot,Vec3 a,Vec3 b,double w,double d){links.add(new Link(slot,a,b,w*scale,d*scale,right));}
    public List<Link> links(){return links;}
    Vec3 tip(int i){return tips[i];}
    Vec3 palm(){return origin;}
}
