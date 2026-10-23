package com.codex.blacklight.infection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared integer-lattice surface. Adjacent chunks use exactly the same corner samples. */
public final class BuildingBiomassField {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point sub(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
        public Point mul(double s) { return new Point(x*s,y*s,z*s); }
        public double dot(Point p) { return x*p.x+y*p.y+z*p.z; }
        public Point cross(Point p) { return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Point unit() { double n=length();return n<1e-9?new Point(0,1,0):mul(1/n); }
        public Point lerp(Point p,double t) { return add(p.sub(this).mul(t)); }
    }
    public record Stem(List<Point> points,double radius,double tip,int delay,int duration,int parent) {
        public Stem { points=List.copyOf(points);if(points.size()<2||points.size()>12||radius<=0||tip<=0||duration<1)throw new IllegalArgumentException("Invalid biomass stem"); }
    }
    public record Triangle(Point a,Point b,Point c,Point normal) {}
    public record Cell(int x,int y,int z) {}
    private static final int[][] TETS={{0,1,3,7},{0,3,2,7},{0,2,6,7},{0,6,4,7},{0,4,5,7},{0,5,1,7}};
    private static final Point[] CORNERS=new Point[8];
    private static final List<List<Triangle>> MESH=new ArrayList<>(256);
    static {
        for(int i=0;i<8;i++)CORNERS[i]=new Point(i&1,(i>>1)&1,(i>>2)&1);
        for(int i=0;i<256;i++)MESH.add(triangulate(i));
    }
    private BuildingBiomassField() {}
    public static double smooth(double x) { x=Math.clamp(x,0,1);return x*x*(3-2*x); }
    public static double density(List<Stem> stems,Point p,int age,int disabled) {
        double nearest=Double.POSITIVE_INFINITY;
        for(int index=0;index<stems.size();index++) {
            if((disabled&(1<<index))!=0)continue;
            var stem=stems.get(index);double growth=smooth((age-stem.delay)/(double)stem.duration);
            if(growth<=0)continue;
            int n=stem.points.size()-1;double end=growth*n;
            for(int i=0;i<n&&i<end;i++) {
                var a=stem.points.get(i);var b=stem.points.get(i+1);
                double fraction=Math.min(1,end-i);b=a.lerp(b,fraction);
                var ab=b.sub(a);double length=ab.dot(ab);
                double t=length<1e-9?0:Math.clamp(p.sub(a).dot(ab)/length,0,1);
                double along=(i+t*fraction)/n;
                double bulge=1+.17*Math.sin(along*Math.PI*2)+.08*Math.sin(along*Math.PI*5);
                double radius=(stem.radius+(stem.tip-stem.radius)*along)*bulge*(.3+.7*Math.sqrt(growth));
                nearest=Math.min(nearest,p.sub(a.lerp(b,t)).length()-radius);
            }
        }
        return nearest;
    }
    public static int mask(List<Stem> stems,int x,int y,int z,int age,int disabled) {
        int mask=0;
        for(int i=0;i<8;i++)if(density(stems,new Point(x+(i&1),y+((i>>1)&1),z+((i>>2)&1)),age,disabled)<=0)mask|=1<<i;
        return mask;
    }
    /** The complete, fixed candidate list is derived from saved paths, not growth history. */
    public static List<Cell> cells(List<Stem> stems,int limit) {
        var candidates=new java.util.HashSet<Cell>();
        for(var stem:stems)for(int i=0;i<stem.points.size()-1;i++) {
            var a=stem.points.get(i);var b=stem.points.get(i+1);double r=Math.max(stem.radius,stem.tip)*1.3+1;
            for(int x=(int)Math.floor(Math.min(a.x,b.x)-r);x<=(int)Math.ceil(Math.max(a.x,b.x)+r);x++)
                for(int y=(int)Math.floor(Math.min(a.y,b.y)-r);y<=(int)Math.ceil(Math.max(a.y,b.y)+r);y++)
                    for(int z=(int)Math.floor(Math.min(a.z,b.z)-r);z<=(int)Math.ceil(Math.max(a.z,b.z)+r);z++) {
                        candidates.add(new Cell(x,y,z));
                        if(candidates.size()>65536)throw new IllegalArgumentException("Biomass plan exceeds local volume budget");
                    }
        }
        var result=new ArrayList<Cell>();
        for(var c:candidates)if(mask(stems,c.x,c.y,c.z,24000,0)!=0)result.add(c);
        result.sort(Comparator.comparingInt(Cell::y).thenComparingInt(c->Math.abs(c.x)+Math.abs(c.z)).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        if(result.size()>limit)throw new IllegalArgumentException("Biomass plan exceeds cell budget: "+result.size());
        return List.copyOf(result);
    }
    public static List<Triangle> mesh(int mask) { return MESH.get(mask&255); }
    private static List<Triangle> triangulate(int mask) {
        var output=new ArrayList<Triangle>();
        for(var tet:TETS) {
            var inside=new ArrayList<Point>();var outside=new ArrayList<Point>();var cuts=new ArrayList<Point>();
            for(int v:tet)((mask&(1<<v))!=0?inside:outside).add(CORNERS[v]);
            if(inside.isEmpty()||outside.isEmpty())continue;
            for(var a:inside)for(var b:outside)cuts.add(a.lerp(b,.5));
            Point normal=centre(outside).sub(centre(inside)).unit(),origin=centre(cuts);
            Point u=cuts.getFirst().sub(origin).unit(),v=normal.cross(u);
            cuts.sort(Comparator.comparingDouble(p->Math.atan2(p.sub(origin).dot(v),p.sub(origin).dot(u))));
            for(int i=1;i<cuts.size()-1;i++) {
                Point a=cuts.getFirst(),b=cuts.get(i),c=cuts.get(i+1);var n=b.sub(a).cross(c.sub(a)).unit();
                if(n.dot(normal)<0){var swap=b;b=c;c=swap;n=n.mul(-1);}
                output.add(new Triangle(a,b,c,n));
            }
        }
        return List.copyOf(output);
    }
    private static Point centre(List<Point> p) { var sum=new Point(0,0,0);for(var q:p)sum=sum.add(q);return sum.mul(1D/p.size()); }
    /** The same piecewise-linear field used by the visible tetrahedral surface. */
    public static double scalar(int mask,double x,double y,double z) {
        var p=new Point(x,y,z);
        for(var t:TETS) {
            Point a=CORNERS[t[0]],b=CORNERS[t[1]].sub(a),c=CORNERS[t[2]].sub(a),d=CORNERS[t[3]].sub(a),q=p.sub(a);
            double det=b.dot(c.cross(d));double v=q.dot(c.cross(d))/det,w=b.dot(q.cross(d))/det,k=b.dot(c.cross(q))/det,u=1-v-w-k;
            if(Math.min(Math.min(u,v),Math.min(w,k))>=-1e-8)
                return u*sign(mask,t[0])+v*sign(mask,t[1])+w*sign(mask,t[2])+k*sign(mask,t[3]);
        }
        return -1;
    }
    private static int sign(int mask,int bit) { return (mask&(1<<bit))!=0?1:-1; }
}
