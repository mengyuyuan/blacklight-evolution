package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Subtracts the finite blade's narrow kerf from a closed skin. Unlike a surface
 * stripe, the resulting walls retain shallow cuts when another blade arrives. */
final class SolidBladeCut {
    interface Vertices<V> extends FiniteMeshCut.Vertices<V> {
        V cap(Vec3 point,Vec3 normal);
        V flip(V vertex);
        default boolean sameAttributes(V a,V b){return true;}
    }
    record Result<V>(List<List<List<V>>> pieces,boolean changed,String reason){}
    private static final double EPS=1e-9,WIDTH=.006;
    private static final class BudgetExceeded extends RuntimeException {BudgetExceeded(String reason){super(reason);}}
    private static final class Budget {
        int remaining=2_000_000;final int polygons;
        Budget(int polygons){this.polygons=polygons;}
        void spend(int amount){remaining-=amount;if(remaining<0)throw new BudgetExceeded("work");}
        void size(int size){if(size>polygons)throw new BudgetExceeded("faces");}
    }
    private record Plane(Vec3 normal,double w){
        Plane flip(){return new Plane(normal.scale(-1),-w);}
    }
    private record Polygon<V>(List<V> vertices,Plane plane){
        Polygon<V> flip(Vertices<V> adapter){var copy=new ArrayList<V>();for(int i=vertices.size()-1;i>=0;i--)copy.add(adapter.flip(vertices.get(i)));return new Polygon<>(copy,plane.flip());}
    }
    static <V> Result<V> subtract(List<List<V>> faces,Vertices<V> source,List<Vec3[]> triangles,int maxFaces){
        if(faces.isEmpty()||triangles.isEmpty())return new Result<>(List.of(),false,"empty");
        // Work near the origin: absolute world coordinates amplify cancellation
        // where adjacent thin cutter prisms meet at nearly parallel planes.
        Vec3 offset=source.position(faces.getFirst().getFirst());
        Vertices<V> adapter=new Vertices<>(){
            public Vec3 position(V v){return source.position(v).subtract(offset);}
            public V mix(V a,V b,double t){return source.mix(a,b,t);}
            public V cap(Vec3 p,Vec3 n){return source.cap(p.add(offset),n);}
            public V flip(V v){return source.flip(v);}
            public boolean sameAttributes(V a,V b){return source.sameAttributes(a,b);}
        };
        if(!FiniteMeshCut.closed(faces,adapter))return new Result<>(List.of(),false,"open-skin");
        var budget=new Budget(maxFaces);var original=new ArrayList<Polygon<V>>();
        // Model exports can reverse a complete closed shell. Existing inward
        // cavity walls belong to their outer shell and retain their orientation.
        boolean reverse=volume(faces,adapter)<0;
        for(var face:faces){var p=polygon(face,adapter);if(p!=null)original.add(reverse?p.flip(adapter):p);}
        List<Polygon<V>> skin=original;var box=bounds(faces,adapter);boolean candidate=false;
        try{
            // Several cubes can belong to one model bone (a head and its snout,
            // for example). Their overlapping interiors form one solid.
            var shells=FiniteMeshCut.shells(faces,adapter);
            // Vanilla and Gecko cuboids emit six consecutive quads. Keep those
            // known closed cells separate before union: overlapping cells can
            // otherwise share collinear edges and look like a single shell.
            if(faces.size()>6&&faces.size()%6==0){
                var cells=new ArrayList<List<List<V>>>();boolean closedCells=true;
                for(int i=0;i<faces.size();i+=6){var cell=faces.subList(i,i+6);if(!FiniteMeshCut.closed(cell,adapter)||Math.abs(volume(cell,adapter))<1e-9){closedCells=false;break;}cells.add(cell);}
                if(closedCells)shells=cells;
            }
            if(shells.size()>1&&shells.stream().allMatch(s->Math.abs(volume(s,adapter))>1e-9)){
                var solids=new ArrayList<List<Polygon<V>>>();boolean cavities=false;
                for(var shell:shells){double signed=volume(shell,adapter)*(reverse?-1:1);if(signed<0){cavities=true;break;}
                    var solid=new ArrayList<Polygon<V>>();for(var face:shell){var p=polygon(face,adapter);if(p!=null)solid.add(reverse?p.flip(adapter):p);}solids.add(solid);}
                if(!cavities){
                    for(int i=0;i<solids.size();i++)for(int j=i+1;j<solids.size();j++){
                        var ba=bounds(solids.get(i).stream().map(Polygon::vertices).toList(),adapter);var bb=bounds(solids.get(j).stream().map(Polygon::vertices).toList(),adapter);
                        if(ba[0].x>=bb[1].x-EPS||bb[0].x>=ba[1].x-EPS||ba[0].y>=bb[1].y-EPS||bb[0].y>=ba[1].y-EPS||ba[0].z>=bb[1].z-EPS||bb[0].z>=ba[1].z-EPS)continue;
                        var a=new Node<V>(adapter,budget);var b=new Node<V>(adapter,budget);a.build(solids.get(i),0);b.build(solids.get(j),0);
                        a.clipTo(b);b.clipTo(a);b.invert();b.clipTo(a);b.invert();a.build(b.all(),0);
                        solids.set(i,merge(a.all(),adapter));solids.remove(j--);
                    }
                    skin=new ArrayList<>();for(var solid:solids)skin.addAll(solid);
                    original=new ArrayList<>(skin);
                }
            }
            for(var worldTriangle:triangles){
                var triangle=Arrays.stream(worldTriangle).map(p->p.subtract(offset)).toArray(Vec3[]::new);
                var cross=triangle[1].subtract(triangle[0]).cross(triangle[2].subtract(triangle[0]));double length=cross.length();
                if(length<1e-8||!overlaps(box,triangle,WIDTH))continue;
                candidate=true;var normal=cross.scale(1/length);var cutter=prism(triangle,normal,adapter);
                var a=new Node<V>(adapter,budget);var b=new Node<V>(adapter,budget);a.build(skin,0);b.build(cutter,0);
                a.invert();a.clipTo(b);b.clipTo(a);b.invert();b.clipTo(a);b.invert();a.build(b.all(),0);a.invert();
                skin=merge(a.all(),adapter);budget.size(skin.size());if(skin.isEmpty())break;
            }
        }catch(BudgetExceeded rejected){return new Result<>(List.of(),false,"budget:"+rejected.getMessage());}
        if(!candidate)return new Result<>(List.of(),false,"miss");
        var output=skin.stream().map(Polygon::vertices).toList();
        if(!output.isEmpty()&&!FiniteMeshCut.closed(output,adapter))return new Result<>(List.of(),false,"open-result:"+FiniteMeshCut.closureError(output,adapter));
        double before=volume(original.stream().map(Polygon::vertices).toList(),adapter),after=volume(output,adapter);
        if(before-after<1e-9)return new Result<>(List.of(),false,"unchanged");
        var shells=FiniteMeshCut.shells(output,adapter);var pieces=new ArrayList<List<List<V>>>();var cavities=new ArrayList<List<List<V>>>();
        for(var shell:shells){double v=volume(shell,adapter);if(v>1e-9)pieces.add(new ArrayList<>(shell));else if(v< -1e-9)cavities.add(shell);}
        // A sealed internal cut is an inward shell, not an independently flying gib.
        for(var cavity:cavities){var at=bounds(cavity,adapter)[0];List<List<V>> owner=null;double distance=Double.POSITIVE_INFINITY;
            for(var piece:pieces){double d=FiniteMeshCut.distance(at,piece,adapter);if(d<distance){distance=d;owner=piece;}}
            if(owner!=null)owner.addAll(cavity);
        }
        if(pieces.isEmpty())return new Result<>(List.of(),false,"no-solid");
        if(pieces.stream().anyMatch(p->!FiniteMeshCut.closed(p,adapter)))return new Result<>(List.of(),false,"open-piece");
        return new Result<>(pieces,true,pieces.size()>1?"separated":"incised");
    }
    static <V> double volume(List<List<V>> faces,FiniteMeshCut.Vertices<V> adapter){
        if(faces.isEmpty())return 0;var origin=adapter.position(faces.getFirst().getFirst());double sum=0;
        for(var face:faces){var a=adapter.position(face.getFirst()).subtract(origin);for(int i=1;i+1<face.size();i++)sum+=a.dot(adapter.position(face.get(i)).subtract(origin).cross(adapter.position(face.get(i+1)).subtract(origin)))/6;}
        return sum;
    }
    private record Point(long x,long y,long z){
        Point(Vec3 p){this(Math.round(p.x*1e7),Math.round(p.y*1e7),Math.round(p.z*1e7));}
    }
    private record EdgeKey(Point a,Point b){}
    private record EdgeOwner(int polygon,int edge,Object identity){}
    /** BSP introduces coplanar subdivisions; removing their shared edges keeps
     * repeated cuts affordable without deleting a wound or moving its surface. */
    private static <V> List<Polygon<V>> merge(List<Polygon<V>> input,Vertices<V> adapter){
        var values=new ArrayList<>(input);
        for(int pass=0;pass<12;pass++){
            var edges=new HashMap<EdgeKey,EdgeOwner>();boolean changed=false;
            for(int i=0;i<values.size();i++){
                var p=values.get(i);if(p==null)continue;
                for(int e=0;e<p.vertices.size();e++){
                    var a=adapter.position(p.vertices.get(e));var b=adapter.position(p.vertices.get((e+1)%p.vertices.size()));
                    var key=new EdgeKey(new Point(a),new Point(b));var other=edges.get(new EdgeKey(key.b,key.a));
                    if(other!=null&&values.get(other.polygon)==other.identity){
                        var joined=join(p,e,values.get(other.polygon),other.edge,adapter);
                        if(joined!=null){values.set(i,joined);values.set(other.polygon,null);changed=true;break;}
                    }
                    edges.put(key,new EdgeOwner(i,e,p));
                }
            }
            values.removeIf(Objects::isNull);if(!changed)break;
        }return values;
    }
    private static <V> Polygon<V> join(Polygon<V> a,int ai,Polygon<V> b,int bi,Vertices<V> adapter){
        if(a.plane.normal.dot(b.plane.normal)<1-1e-10||Math.abs(a.plane.w-b.plane.w)>1e-8)return null;
        int an=a.vertices.size(),bn=b.vertices.size();
        var a0=a.vertices.get(ai);var a1=a.vertices.get((ai+1)%an);var b0=b.vertices.get(bi);var b1=b.vertices.get((bi+1)%bn);
        if(adapter.position(a0).distanceToSqr(adapter.position(b1))>1e-14||adapter.position(a1).distanceToSqr(adapter.position(b0))>1e-14
                ||!adapter.sameAttributes(a0,b1)||!adapter.sameAttributes(a1,b0))return null;
        var points=new ArrayList<V>();for(int j=1;j<=an;j++)points.add(a.vertices.get((ai+j)%an));
        for(int j=2;j<bn;j++)points.add(b.vertices.get((bi+j)%bn));
        // Remove collinear interpolation vertices, preserving texture seams.
        for(int j=points.size()-1;j>=0&&points.size()>3;j--){int n=points.size();var before=points.get((j+n-1)%n);var at=points.get(j);var next=points.get((j+1)%n);
            var axis=adapter.position(next).subtract(adapter.position(before));double length=axis.lengthSqr();if(length<1e-18)continue;
            double t=adapter.position(at).subtract(adapter.position(before)).dot(axis)/length;
            if(t>0&&t<1&&adapter.position(at).distanceToSqr(adapter.position(before).add(axis.scale(t)))<1e-16&&adapter.sameAttributes(at,adapter.mix(before,next,t)))points.remove(j);
        }
        for(int j=0;j<points.size();j++){
            var x=adapter.position(points.get(j));var y=adapter.position(points.get((j+1)%points.size()));var z=adapter.position(points.get((j+2)%points.size()));
            if(y.subtract(x).cross(z.subtract(y)).dot(a.plane.normal)<-1e-12)return null;
        }
        return new Polygon<>(points,a.plane);
    }
    private static <V> Polygon<V> polygon(List<V> source,Vertices<V> adapter){
        var vertices=new ArrayList<V>();for(var v:source)if(vertices.isEmpty()||adapter.position(v).distanceToSqr(adapter.position(vertices.getLast()))>EPS*EPS)vertices.add(v);
        if(vertices.size()>1&&adapter.position(vertices.getFirst()).distanceToSqr(adapter.position(vertices.getLast()))<=EPS*EPS)vertices.removeLast();
        if(vertices.size()<3)return null;var a=adapter.position(vertices.getFirst());Vec3 normal=Vec3.ZERO;
        for(int i=1;i+1<vertices.size();i++)normal=normal.add(adapter.position(vertices.get(i)).subtract(a).cross(adapter.position(vertices.get(i+1)).subtract(a)));
        double length=normal.length();if(length<1e-14)return null;normal=normal.scale(1/length);
        return new Polygon<>(vertices,new Plane(normal,normal.dot(a)));
    }
    private static <V> List<Polygon<V>> prism(Vec3[] triangle,Vec3 normal,Vertices<V> adapter){
        Vec3[] p=new Vec3[6];for(int i=0;i<3;i++){p[i]=triangle[i].subtract(normal.scale(WIDTH*.5));p[i+3]=triangle[i].add(normal.scale(WIDTH*.5));}
        var out=new ArrayList<Polygon<V>>();
        for(var indices:new int[][]{{0,2,1},{3,4,5},{0,1,4,3},{1,2,5,4},{2,0,3,5}}){
            var a=p[indices[0]];var cross=p[indices[1]].subtract(a).cross(p[indices[2]].subtract(a));double length=cross.length();if(length<1e-12)continue;var n=cross.scale(1/length);
            var values=new ArrayList<V>();for(int i:indices)values.add(adapter.cap(p[i],n));var poly=polygon(values,adapter);if(poly!=null)out.add(poly);
        }return out;
    }
    private static <V> Vec3[] bounds(List<List<V>> faces,FiniteMeshCut.Vertices<V> adapter){
        var low=adapter.position(faces.getFirst().getFirst());var high=low;
        for(var face:faces)for(var vertex:face){var p=adapter.position(vertex);low=new Vec3(Math.min(low.x,p.x),Math.min(low.y,p.y),Math.min(low.z,p.z));high=new Vec3(Math.max(high.x,p.x),Math.max(high.y,p.y),Math.max(high.z,p.z));}
        return new Vec3[]{low,high};
    }
    private static boolean overlaps(Vec3[] box,Vec3[] triangle,double pad){
        var lo=triangle[0];var hi=lo;for(var p:triangle){lo=new Vec3(Math.min(lo.x,p.x),Math.min(lo.y,p.y),Math.min(lo.z,p.z));hi=new Vec3(Math.max(hi.x,p.x),Math.max(hi.y,p.y),Math.max(hi.z,p.z));}
        return hi.x+pad>=box[0].x&&lo.x-pad<=box[1].x&&hi.y+pad>=box[0].y&&lo.y-pad<=box[1].y&&hi.z+pad>=box[0].z&&lo.z-pad<=box[1].z;
    }
    private static final class Node<V>{
        final Vertices<V> adapter;final Budget budget;Plane plane;final List<Polygon<V>> polygons=new ArrayList<>();Node<V> front,back;
        Node(Vertices<V> adapter,Budget budget){this.adapter=adapter;this.budget=budget;}
        void invert(){for(int i=0;i<polygons.size();i++)polygons.set(i,polygons.get(i).flip(adapter));if(plane!=null)plane=plane.flip();if(front!=null)front.invert();if(back!=null)back.invert();var old=front;front=back;back=old;}
        List<Polygon<V>> all(){var all=new ArrayList<>(polygons);if(front!=null)all.addAll(front.all());if(back!=null)all.addAll(back.all());budget.size(all.size());return all;}
        void build(List<Polygon<V>> values,int depth){
            if(values.isEmpty())return;if(depth>256)throw new BudgetExceeded("depth");if(plane==null)plane=values.getFirst().plane;
            var f=new ArrayList<Polygon<V>>();var b=new ArrayList<Polygon<V>>();for(var p:values)split(p,polygons,polygons,f,b);budget.size(polygons.size()+f.size()+b.size());
            if(!f.isEmpty()){if(front==null)front=new Node<>(adapter,budget);front.build(f,depth+1);}
            if(!b.isEmpty()){if(back==null)back=new Node<>(adapter,budget);back.build(b,depth+1);}
        }
        List<Polygon<V>> clip(List<Polygon<V>> values){
            if(plane==null)return new ArrayList<>(values);var f=new ArrayList<Polygon<V>>();var b=new ArrayList<Polygon<V>>();
            for(var p:values)split(p,f,b,f,b);if(front!=null)f=new ArrayList<>(front.clip(f));if(back!=null)b=new ArrayList<>(back.clip(b));else b.clear();f.addAll(b);budget.size(f.size());return f;
        }
        void clipTo(Node<V> other){var clipped=other.clip(polygons);polygons.clear();polygons.addAll(clipped);if(front!=null)front.clipTo(other);if(back!=null)back.clipTo(other);}
        void split(Polygon<V> polygon,List<Polygon<V>> coplanarFront,List<Polygon<V>> coplanarBack,List<Polygon<V>> front,List<Polygon<V>> back){
            if(polygon.plane==plane){coplanarFront.add(polygon);return;}
            var points=polygon.vertices;budget.spend(points.size());int type=0;int[] types=new int[points.size()];double[] distances=new double[points.size()];
            for(int i=0;i<points.size();i++){double d=plane.normal.dot(adapter.position(points.get(i)))-plane.w;distances[i]=d;types[i]=d< -EPS?2:d>EPS?1:0;type|=types[i];}
            if(type==0){(plane.normal.dot(polygon.plane.normal)>0?coplanarFront:coplanarBack).add(polygon);return;}
            if(type==1){front.add(polygon);return;}if(type==2){back.add(polygon);return;}
            var f=new ArrayList<V>();var b=new ArrayList<V>();
            for(int i=0;i<points.size();i++){int j=(i+1)%points.size();var a=points.get(i);var z=points.get(j);if(types[i]!=2)f.add(a);if(types[i]!=1)b.add(a);
                if((types[i]|types[j])==3){double alpha=distances[i]/(distances[i]-distances[j]);var v=adapter.mix(a,z,alpha);f.add(v);b.add(v);}}
            var a=polygon(f,adapter);var z=polygon(b,adapter);if(a!=null)front.add(a);if(z!=null)back.add(z);
        }
    }
}
