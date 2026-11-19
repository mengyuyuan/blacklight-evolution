package com.codex.blacklight.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Local axes of the parked six-wheel tender: nose left, operator facing the near flank. Dimensions are blocks. */
final class PumpTruckModel {
    static final int PAINT=0, STEEL=1, RUBBER=2, GLASS=3, TANK=4, STRIPE=5, GRILLE=6, LIVERY=7,
            WHITE=8, RED=9, AMBER=10, HOSE=11, DIAL=12, PLATE=13, SCRAPE=14, RIM=15;
    record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, int material) {}
    static final class Mesh {
        final List<Face> faces=new ArrayList<>();
        void face(int material, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
            faces.add(new Face(a,b,c,d,b.subtract(a).cross(c.subtract(a)).normalize(),material));
        }
        void box(int material,double x,double y,double z,double X,double Y,double Z) {
            Vec3 a=v(x,y,z), b=v(X,y,z), c=v(X,Y,z), d=v(x,Y,z);
            Vec3 e=v(x,y,Z), f=v(X,y,Z), g=v(X,Y,Z), h=v(x,Y,Z);
            face(material,b,a,d,c);face(material,e,f,g,h);
            face(material,a,e,h,d);face(material,f,b,c,g);
            face(material,d,h,g,c);face(material,a,b,f,e);
        }
        void pipe(int material,Vec3 start,Vec3 end,double radius,int sides) {
            var axis=end.subtract(start).normalize();
            var u=axis.cross(Math.abs(axis.y)>.91?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(radius);
            var w=axis.cross(u);
            Vec3 old=u;
            for(int i=1;i<=sides;i++){
                double angle=Math.PI*2*i/sides;
                var next=u.scale(Math.cos(angle)).add(w.scale(Math.sin(angle)));
                face(material,start.add(old),start.add(next),end.add(next),end.add(old));
                face(material,end,end.add(old),end.add(next),end.add(next));
                face(material,start,start.add(next),start.add(old),start.add(old));
                old=next;
            }
        }
        void rail(int material,double x,double y,double z,double X,double Y,double Z,double r) {
            pipe(material,v(x,y,z),v(X,Y,Z),r,8);
        }
    }
    static Vec3 v(double x,double y,double z){ return new Vec3(x,y,z); }
    static final Mesh WHEELS=new Mesh(), BODY=new Mesh(), MIRROR=new Mesh(), SCAR=new Mesh(), NEEDLE=new Mesh(), HANDLE=new Mesh();
    static final Mesh[] LINE=new Mesh[24];
    static final Vec3 PIVOT=v(-1,.65,-1.47);
    static final Vec3 MIRROR_PIVOT=v(-5.40,1.97,-.46);

    static {
        truck();
        NEEDLE.box(RED,-.014,-.018,-.013,.014,.136,.013);
        HANDLE.rail(STEEL,0,0,0,0,.22,.026,.023);
        HANDLE.rail(AMBER,-.08,.22,.026,.08,.22,.026,.035);
        for(int i=0;i<LINE.length;i++) {
            LINE[i]=hoseSegment(i/(double)LINE.length,(i+1.0)/LINE.length);
        }
    }

    private static void truck() {
        WHEELS.box(STEEL,-6.00,.39,-1.86,1.22,.69,-1.09);
        for(double x:new double[]{-5.08,-1.76,.18}) {
            WHEELS.rail(STEEL,x,.55,-2.48,x,.55,-.48,.14);
            for(int side:new int[]{-1,1}) {
                double z=side>0?-.51:-2.40;
                WHEELS.pipe(RUBBER,v(x,.535,z-.18),v(x,.535,z+.18),.52,16);
                double lip=z+side*.183;
                WHEELS.pipe(RIM,v(x,.535,lip),v(x,.535,lip+side*.022),.32,12);
                WHEELS.pipe(PAINT,v(x,.535,lip+side*.022),v(x,.535,lip+side*.044),.19,12);
                for(int i=0;i<6;i++) {
                    double a=i*Math.PI/3, xx=x+.23*Math.cos(a), yy=.535+.23*Math.sin(a);
                    WHEELS.pipe(STEEL,v(xx,yy,lip+side*.023),v(xx,yy,lip+side*.038),.027,6);
                }
            }
        }
        // Spring-mounted chassis, recessed footplates and a split armored windscreen.
        BODY.box(PAINT,-6.23,.81,-2.46,1.42,1.04,-.42);
        BODY.box(STEEL,-6.42,.54,-2.62,-6.19,.83,-.24);
        BODY.box(STRIPE,.99,.65,-2.55,1.45,.95,-.33);
        BODY.box(PAINT,-6.18,1.02,-2.44,-3.85,1.64,-.47);
        BODY.box(PAINT,-5.93,1.62,-2.42,-3.89,2.29,-.49);
        for(double z:new double[]{-2.35,-1.41}) {
            var bottom=v(-6.14,1.55,z);var upper=v(-5.78,2.26,z);
            BODY.face(GLASS,bottom,bottom.add(0,0,.81),upper.add(0,0,.81),upper);
        }
        BODY.box(PAINT,-5.92,2.26,-2.5,-3.8,2.39,-.40);
        BODY.box(GRILLE,-6.205,1.06,-2.04,-6.185,1.47,-.90);
        for(double z:new double[]{-2.37,-.83}) {
            BODY.box(WHITE,-6.215,1.16,z,-6.19,1.37,z+.33);
            BODY.box(AMBER,-6.219,1.08,z,-6.189,1.135,z+.33);
        }
        for(int s:new int[]{-1,1}) {
            double side=s>0?-.475:-2.438;
            BODY.box(GLASS,-5.52,1.70,side-.016,-4.18,2.18,side+.016);
            BODY.box(PAINT,-4.64,1.67,side-.03,-4.58,2.25,side+.03);
            BODY.box(LIVERY,-5.33,1.13,side-.027,-4.13,1.58,side+.028);
            BODY.box(STEEL,-4.14,1.60,side-.042,-3.97,1.65,side+.042);
            BODY.box(GRILLE,-4.62,.66,side-.12,-3.82,.78,side+.12);
            BODY.box(PAINT,-5.73,.97,side-.16,-4.43,1.08,side+.16);
            BODY.box(PAINT,-2.37,.98,side-.18,.79,1.10,side+.18);
        }
        BODY.box(AMBER,-4.56,2.40,-2.14,-4.28,2.52,-1.88);
        BODY.box(AMBER,-4.56,2.40,-1.07,-4.28,2.52,-.81);
        // Two pressure vessels, each with dome collars, saddles, banding and a bolted inspection cap.
        for(double z:new double[]{-1.94,-1.0}) {
            BODY.pipe(TANK,v(-3.72,1.59,z),v(-.30,1.59,z),.49,16);
            for(double x:new double[]{-3.76,-.33})
                BODY.pipe(STEEL,v(x,1.59,z),v(x+.07,1.59,z),.46,16);
            for(double x:new double[]{-3.37,-1.96,-.68}) {
                BODY.pipe(PAINT,v(x,1.59,z),v(x+.078,1.59,z),.509,16);
                BODY.box(STEEL,x-.05,1.01,z-.38,x+.16,1.27,z+.38);
            }
            BODY.pipe(PAINT,v(-2.56,2.05,z),v(-2.56,2.14,z),.22,12);
            BODY.pipe(AMBER,v(-2.56,2.14,z),v(-2.56,2.165,z),.13,12);
        }
        BODY.box(PLATE,-3.09,1.41,-.499,-2.10,1.72,-.477);
        BODY.rail(STEEL,-3.71,2.10,-2.28,-.39,2.10,-2.28,.039);
        // Rear pump manifold, shutoff handwheel, ribbed hose reel, stabilizer jacks.
        BODY.box(GRILLE,-.18,1.02,-2.29,1.24,1.79,-1.24);
        BODY.rail(STEEL,.09,1.63,-1.17,1.19,1.63,-1.17,.10);
        BODY.pipe(RUBBER,v(.12,1.35,-.87),v(.79,1.35,-.87),.32,14);
        for(double x:new double[]{.08,.80}) BODY.pipe(AMBER,v(x,1.35,-.87),v(x+.04,1.35,-.87),.37,14);
        for(double x=.2;x<.78;x+=.09) BODY.pipe(HOSE,v(x,1.35,-.87),v(x+.035,1.35,-.87),.328,14);
        for(double z:new double[]{-2.55,-.28}) {
            WHEELS.rail(STEEL,.90,.64,-1.46,.90,.54,z,.076);
            WHEELS.rail(STEEL,.90,.54,z,.90,.09,z,.068);
            WHEELS.box(PAINT,.68,0,z-.18,1.12,.09,z+.18);
        }
        BODY.rail(STEEL,-3.84,.79,-2.20,-3.84,2.47,-2.20,.088);
        BODY.rail(PAINT,-3.84,2.47,-2.20,-3.74,2.60,-2.20,.084);
        MIRROR.rail(STEEL,-5.40,1.97,-.46,-5.52,2.11,-.16,.031);
        MIRROR.box(PAINT,-5.63,1.89,-.16,-5.54,2.14,.02);
        MIRROR.box(GLASS,-5.54,1.91,-.15,-5.52,2.12,.01);
        SCAR.box(SCRAPE,-5.88,1.09,-.445,-5.42,1.46,-.427);
    }

    static Vec3 linePoint(double t) {
        double z=-.57-11.26*t;
        double x=1.03+Math.sin(t*9)*.64-t*.92;
        double y=.095+Math.max(0,1-12*t)*.97;
        return v(x,y,z);
    }
    private static Mesh hoseSegment(double a,double b) {
        var mesh=new Mesh();
        mesh.pipe(HOSE,linePoint(a),linePoint(b),.099,8);
        if(Math.round(a*LINE.length)%5==0)
            mesh.pipe(STEEL,linePoint(a),linePoint(a+(b-a)*.16),.114,8);
        return mesh;
    }
    private PumpTruckModel() {}
}
