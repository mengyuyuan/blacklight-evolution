package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Surface connectivity for a finite blade ribbon. Uncut intervals weld both sides
 * back together; an extrapolated sign change alone cannot detach any tissue. */
final class FiniteMeshCut {
    interface Vertices<V> { Vec3 position(V vertex); V mix(V a,V b,double t); }
    record Loop(List<Vec3> points,Vec3 normal,int side,List<Vec3[]> triangles) {}
    record Piece<V>(List<List<V>> faces,List<Loop> caps) {}
    record Incision(int face,Vec3 a,Vec3 b) {}
    record Result<V>(List<Piece<V>> pieces,List<Incision> incisions,String reason) {}
    private record Fragment<V>(int face,int side,List<V> polygon) {}
    private record Seam(int positive,int negative,Vec3 a,Vec3 b) {}
    private record Edge(int owner,Vec3 a,Vec3 b) {}
    private static final double EPS=1e-7;
    private static final ThreadLocal<String> CAP_FAILURE=ThreadLocal.withInitial(()->"coverage-or-budget");
    private static <T> List<T> capFailure(String reason){CAP_FAILURE.set(reason);return null;}
    private FiniteMeshCut() {}

    /** Connected skin shells; matching short/long edges support clipped polygons. */
    static <V> List<List<List<V>>> shells(List<List<V>> faces,Vertices<V> vertices){
        var edges=new ArrayList<Edge>();
        for(int f=0;f<faces.size();f++){var face=faces.get(f);for(int i=0;i<face.size();i++)edges.add(new Edge(f,vertices.position(face.get(i)),vertices.position(face.get((i+1)%face.size()))));}
        var groups=new Union(faces.size());weld(edges,groups,null,null,null,1e-7);
        var result=new LinkedHashMap<Integer,List<List<V>>>();
        for(int i=0;i<faces.size();i++)result.computeIfAbsent(groups.root(i),key->new ArrayList<>()).add(faces.get(i));
        return new ArrayList<>(result.values());
    }
    static <V> boolean closed(List<List<V>> faces,Vertices<V> vertices){
        return closureError(faces,vertices)<=1e-4;
    }
    static <V> double closureError(List<List<V>> faces,Vertices<V> vertices){
        var edges=new ArrayList<Edge>();
        for(int f=0;f<faces.size();f++){var face=faces.get(f);for(int i=0;i<face.size();i++){
            var a=vertices.position(face.get(i));var b=vertices.position(face.get((i+1)%face.size()));if(a.distanceToSqr(b)>1e-16)edges.add(new Edge(f,a,b));}}
        if(edges.isEmpty())return Double.POSITIVE_INFINITY;var indexed=new IndexedEdge[edges.size()];for(int i=0;i<edges.size();i++)indexed[i]=new IndexedEdge(i,edges.get(i));
        var tree=new EdgeTree(indexed,0,indexed.length);var candidates=new ArrayList<Edge>();
        double largest=0;
        for(var edge:edges){candidates.clear();tree.collect(new IndexedEdge(edges.size(),edge),candidates);var intervals=new ArrayList<double[]>();
            var axis=edge.b.subtract(edge.a);double length=axis.length();axis=axis.scale(1/length);
            for(var candidate:candidates)if(candidate.owner!=edge.owner){var shared=overlap(edge,candidate,1e-7);if(shared!=null)intervals.add(new double[]{shared[0].subtract(edge.a).dot(axis),shared[1].subtract(edge.a).dot(axis)});}
            intervals.sort(Comparator.comparingDouble(a->a[0]));double through=0;for(var interval:intervals){largest=Math.max(largest,interval[0]-through);through=Math.max(through,interval[1]);}largest=Math.max(largest,length-through);
        }return largest;
    }

    /** Resolve a curved seam near the finite ribbon without subdividing distant skin.
     * An exhausted budget rejects refinement instead of leaving a half-refined cut. */
    static <V> List<List<V>> refine(List<List<V>> faces,Vertices<V> vertices,SweptBladeSurface surface,double spacing,int maxFaces){
        if(!(spacing>0)||maxFaces<faces.size())return List.of();
        var pending=new ArrayDeque<>(faces);var result=new ArrayList<List<V>>();
        while(!pending.isEmpty()){
            var face=pending.removeFirst();double longest=0;
            Vec3 low=vertices.position(face.getFirst()),high=low;
            for(int i=0;i<face.size();i++){
                var p=vertices.position(face.get(i));var q=vertices.position(face.get((i+1)%face.size()));longest=Math.max(longest,p.distanceToSqr(q));
                low=new Vec3(Math.min(low.x,p.x),Math.min(low.y,p.y),Math.min(low.z,p.z));
                high=new Vec3(Math.max(high.x,p.x),Math.max(high.y,p.y),Math.max(high.z,p.z));
            }
            if(face.size()!=4||longest<=spacing*spacing||!surface.overlaps(low,high,.045)){result.add(face);continue;}
            if(result.size()+pending.size()+4>maxFaces)return List.of();
            var a=face.get(0);var b=face.get(1);var c=face.get(2);var d=face.get(3);
            var ab=vertices.mix(a,b,.5);var bc=vertices.mix(b,c,.5);var cd=vertices.mix(c,d,.5);var da=vertices.mix(d,a,.5);var middle=vertices.mix(ab,cd,.5);
            pending.addFirst(List.of(da,middle,cd,d));pending.addFirst(List.of(middle,bc,c,cd));
            pending.addFirst(List.of(ab,b,bc,middle));pending.addFirst(List.of(a,ab,middle,da));
        }
        return result;
    }

    static <V> List<Piece<V>> cut(List<List<V>> faces,Vertices<V> vertices,SweptBladeSurface surface){
        return trace(faces,vertices,surface).pieces();
    }
    static <V> Result<V> trace(List<List<V>> faces,Vertices<V> vertices,SweptBladeSurface surface){
        if(surface.empty())return new Result<>(List.of(),List.of(),"empty-ribbon");
        var incisions=new ArrayList<Incision>();
        var originalEdges=new ArrayList<Edge>();var edges=new ArrayList<Edge>();
        var fragments=new ArrayList<Fragment<V>>();var seams=new ArrayList<Seam>();
        var bridges=new ArrayList<int[]>();var distances=new HashMap<Vec3,Double>();
        for(int f=0;f<faces.size();f++){
            var face=faces.get(f);int count=face.size();double[] distance=new double[count];
            Vec3 low=vertices.position(face.getFirst()),high=low;
            for(var vertex:face){var p=vertices.position(vertex);low=new Vec3(Math.min(low.x,p.x),Math.min(low.y,p.y),Math.min(low.z,p.z));high=new Vec3(Math.max(high.x,p.x),Math.max(high.y,p.y),Math.max(high.z,p.z));}
            if(!surface.overlaps(low,high,.045)){
                // The finite blade cannot reach this face. Keep its topology intact
                // instead of clipping it against an extrapolated sign field.
                int id=fragments.size();fragments.add(new Fragment<>(f,0,face));
                for(int i=0;i<count;i++){var a=vertices.position(face.get(i));var b=vertices.position(face.get((i+1)%count));originalEdges.add(new Edge(f,a,b));edges.add(new Edge(id,a,b));}
                continue;
            }
            var crosses=new HashMap<Integer,V>();
            for(int i=0;i<count;i++)distance[i]=distances.computeIfAbsent(vertices.position(face.get(i)),p->{double d=surface.distance(p);return Math.abs(d)<1e-9?0:d;});
            for(int i=0;i<count;i++){
                int j=(i+1)%count;var a=vertices.position(face.get(i));var b=vertices.position(face.get(j));
                originalEdges.add(new Edge(f,a,b));
                if((distance[i]>=0)!=(distance[j]>=0)){
                    if(distance[i]==0){crosses.put(i,face.get(i));continue;}
                    if(distance[j]==0){crosses.put(i,face.get(j));continue;}
                    double lo=0,hi=1,t=0;
                    for(int n=0;n<28;n++){t=(lo+hi)*.5;if((surface.distance(a.lerp(b,t))>=0)==(distance[i]>=0))lo=t;else hi=t;}
                    crosses.put(i,vertices.mix(face.get(i),face.get(j),(lo+hi)*.5));
                }
            }
            int positive=-1,negative=-1;
            for(int side:new int[]{1,-1}){
                var polygon=new ArrayList<V>();var outer=new ArrayList<Vec3[]>();
                for(int i=0;i<count;i++){
                    int j=(i+1)%count;var a=face.get(i);var b=face.get(j);var cross=crosses.get(i);
                    boolean inside=inside(distance[i],side);
                    if(inside)polygon.add(a);
                    if(cross!=null)polygon.add(cross);
                    if(distance[i]==0&&distance[j]==0||inside&&inside(distance[j],side))outer.add(new Vec3[]{vertices.position(a),vertices.position(b)});
                    else if(cross!=null)outer.add(inside?new Vec3[]{vertices.position(a),vertices.position(cross)}:new Vec3[]{vertices.position(cross),vertices.position(b)});
                }
                compact(polygon,vertices);
                if(polygon.size()<3||area(polygon,vertices)<1e-16)continue;
                int id=fragments.size();fragments.add(new Fragment<>(f,side,polygon));
                for(var edge:outer)edges.add(new Edge(id,edge[0],edge[1]));
                if(side>0)positive=id;else negative=id;
            }
            if(positive>=0&&negative>=0){
                var points=new ArrayList<Vec3>();
                for(var v:crosses.values()){var p=vertices.position(v);if(points.stream().noneMatch(q->q.distanceToSqr(p)<EPS*EPS))points.add(p);}
                if(points.size()==2)incisions(incisions,f,points.get(0),points.get(1),surface);
                if(points.size()==2&&surface.covers(points.get(0),points.get(1)))
                    seams.add(new Seam(positive,negative,points.get(0),points.get(1)));
                else bridges.add(new int[]{positive,negative});
            }
        }
        var original=new Union(faces.size());weld(originalEdges,original,null,null,null);
        var cut=new Union(fragments.size());weld(edges,cut,fragments,surface,seams);
        if(seams.isEmpty())return new Result<>(List.of(),incisions,"no-covered-seam");
        for(var bridge:bridges)cut.join(bridge[0],bridge[1]);
        var islands=new LinkedHashMap<Integer,Set<Integer>>();
        for(int i=0;i<fragments.size();i++)islands.computeIfAbsent(original.root(fragments.get(i).face),k->new LinkedHashSet<>()).add(cut.root(i));
        var separated=new LinkedHashSet<Integer>();
        for(var roots:islands.values())if(roots.size()>1)separated.addAll(roots);
        if(separated.size()<2)return new Result<>(List.of(),incisions,"surface-still-connected");
        var groups=new LinkedHashMap<Integer,List<List<V>>>();
        var untouched=new LinkedHashMap<Integer,List<List<V>>>();
        for(int i=0;i<fragments.size();i++){
            int root=cut.root(i);var target=separated.contains(root)?groups:untouched;
            target.computeIfAbsent(root,k->new ArrayList<>()).add(fragments.get(i).polygon);
        }
        // Decorative cubes were already disconnected before the strike. Keep each whole
        // island on its nearest cut fragment rather than exploding it into a new body.
        for(var island:untouched.values()){
            Vec3 center=Vec3.ZERO;int count=0;for(var face:island)for(var v:face){center=center.add(vertices.position(v));count++;}
            center=center.scale(1.0/count);int nearest=-1;double best=Double.POSITIVE_INFINITY;
            for(var group:groups.entrySet()){double d=distance(center,group.getValue(),vertices);if(d<best){best=d;nearest=group.getKey();}}
            groups.get(nearest).addAll(island);
        }
        var borders=new LinkedHashMap<Integer,List<Seam>>();
        for(var seam:seams){int a=cut.root(seam.positive),b=cut.root(seam.negative);if(a==b)continue;
            borders.computeIfAbsent(a,k->new ArrayList<>()).add(new Seam(1,0,seam.a,seam.b));
            borders.computeIfAbsent(b,k->new ArrayList<>()).add(new Seam(-1,0,seam.a,seam.b));
        }
        var result=new ArrayList<Piece<V>>();
        for(var group:groups.entrySet()){
            var caps=loops(borders.getOrDefault(group.getKey(),List.of()),surface);
            // A closed skin loop is insufficient if intact material remains inside
            // it (e.g. a ring-shaped stroke). Keep that receiver connected.
            if(caps==null)return new Result<>(List.of(),incisions,"cap-"+CAP_FAILURE.get());
            result.add(new Piece<>(group.getValue(),caps));
        }
        return new Result<>(result,incisions,"separated");
    }
    private static void incisions(List<Incision> result,int face,Vec3 a,Vec3 b,SweptBladeSurface surface){
        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.02));Vec3 start=null,end=null;
        for(int i=0;i<steps;i++){
            var p=a.lerp(b,i/(double)steps);var q=a.lerp(b,(i+1)/(double)steps);
            if(surface.covers(p)&&surface.covers(p.lerp(q,.5))&&surface.covers(q)){if(start==null)start=p;end=q;}
            else if(start!=null){result.add(new Incision(face,start,end));start=null;}
        }
        if(start!=null)result.add(new Incision(face,start,end));
    }
    static <V> double distance(Vec3 p,List<List<V>> mesh,Vertices<V> vertices){
        double best=Double.POSITIVE_INFINITY;
        for(var polygon:mesh)for(int i=1;i+1<polygon.size();i++)best=Math.min(best,p.distanceToSqr(SweptBladeSurface.closest(p,vertices.position(polygon.getFirst()),vertices.position(polygon.get(i)),vertices.position(polygon.get(i+1)))));
        return best;
    }
    private static boolean inside(double distance,int side){return side>0?distance>=0:distance<0;}
    private static <V> void compact(List<V> polygon,Vertices<V> vertices){
        for(int i=polygon.size()-1;i>=0&&polygon.size()>1;i--)if(vertices.position(polygon.get(i)).distanceToSqr(vertices.position(polygon.get((i+1)%polygon.size())))<1e-14)polygon.remove(i);
    }
    private static <V> double area(List<V> polygon,Vertices<V> vertices){
        double area=0;var a=vertices.position(polygon.getFirst());for(int i=1;i+1<polygon.size();i++)area+=vertices.position(polygon.get(i)).subtract(a).cross(vertices.position(polygon.get(i+1)).subtract(a)).length();return area*.5;
    }
    private static List<Loop> loops(List<Seam> source,SweptBladeSurface surface){
        var seams=new ArrayList<>(source);var result=new ArrayList<Loop>();
        while(!seams.isEmpty()){
            var first=seams.removeFirst();var loop=new ArrayList<Vec3>();loop.add(first.a);loop.add(first.b);boolean closed=false;
            for(int step=0;step<4096;step++){
                var end=loop.getLast();if(end.distanceToSqr(loop.getFirst())<EPS*EPS){loop.removeLast();closed=true;break;}
                int found=-1;Vec3 next=null;
                for(int i=0;i<seams.size();i++){var edge=seams.get(i);if(end.distanceToSqr(edge.a)<EPS*EPS){found=i;next=edge.b;break;}if(end.distanceToSqr(edge.b)<EPS*EPS){found=i;next=edge.a;break;}}
                if(found<0)break;seams.remove(found);loop.add(next);
            }
            if(!closed)return capFailure("open-loop");
            if(loop.size()>=3){
                var normal=surface.normal(loop.getFirst()).scale(-first.positive);var triangles=fill(loop,normal,surface);
                if(triangles==null)return null;
                result.add(new Loop(loop,normal,first.positive,triangles));
            }
        }return result;
    }
    private static List<Vec3[]> fill(List<Vec3> source,Vec3 normal,SweptBladeSurface surface){
        var loop=new ArrayList<>(source);var flat=new ArrayList<Vec3[]>();Vec3 area=Vec3.ZERO,origin=loop.getFirst();
        for(int i=loop.size()-1;i>=0&&loop.size()>3;i--){
            var a=loop.get((i+loop.size()-1)%loop.size());var b=loop.get(i);var c=loop.get((i+1)%loop.size());
            var axis=c.subtract(a);double length=axis.lengthSqr();if(length<1e-20)continue;
            double t=b.subtract(a).dot(axis)/length;
            if(t>=0&&t<=1&&b.distanceToSqr(a.add(axis.scale(t)))<1e-20)loop.remove(i);
        }
        for(int i=0;i<loop.size();i++)area=area.add(loop.get(i).subtract(origin).cross(loop.get((i+1)%loop.size()).subtract(origin)));
        if(area.dot(normal)<0)Collections.reverse(loop);
        int attempts=0;
        while(loop.size()>2&&attempts++<4096){int best=-1;double bestQuality=-1;
            for(int i=0;i<loop.size();i++){
                Vec3 a=loop.get((i+loop.size()-1)%loop.size()),b=loop.get(i),c=loop.get((i+1)%loop.size());
                double convex=b.subtract(a).cross(c.subtract(b)).dot(normal);
                if(convex<1e-16)continue;
                boolean contains=false;
                for(var p:loop){if(p==a||p==b||p==c)continue;
                    if(b.subtract(a).cross(p.subtract(a)).dot(normal)>=-1e-12&&c.subtract(b).cross(p.subtract(b)).dot(normal)>=-1e-12&&a.subtract(c).cross(p.subtract(c)).dot(normal)>=-1e-12){contains=true;break;}}
                if(contains)continue;
                double quality=convex/(a.distanceToSqr(b)+b.distanceToSqr(c)+c.distanceToSqr(a));
                if(quality>bestQuality){best=i;bestQuality=quality;}
            }
            if(best<0)return capFailure("ear-clip");
            flat.add(new Vec3[]{loop.get((best+loop.size()-1)%loop.size()),loop.get(best),loop.get((best+1)%loop.size())});loop.remove(best);
        }
        if(loop.size()>2)return capFailure("ear-budget");
        var refined=conformingCap(flat,surface);if(refined==null)return capFailure("mesh-budget");
        var result=new ArrayList<Vec3[]>();
        for(var triangle:refined){
                var projected=new Vec3[3];for(int i=0;i<3;i++){
                    boolean boundary=false;for(int j=0;j<source.size();j++){
                        var a=source.get(j);var edge=source.get((j+1)%source.size()).subtract(a);double edgeLength=edge.lengthSqr();if(edgeLength<1e-18)continue;
                        double t=Math.clamp(triangle[i].subtract(a).dot(edge)/edgeLength,0,1);
                        if(triangle[i].distanceToSqr(a.add(edge.scale(t)))<1e-14){boundary=true;break;}
                    }
                    // Moving a boundary point onto the curved ribbon tears its
                    // cap away from the retained skin. Only bend interior points.
                    projected[i]=boundary?triangle[i]:surface.project(triangle[i]);if(!surface.covers(projected[i]))return capFailure("vertex-coverage");
                }
                if(!surface.covers(surface.project(triangle[0].add(triangle[1]).add(triangle[2]).scale(1.0/3))))return capFailure("interior-coverage");
                result.add(projected);
        }return result;
    }
    private record CapEdge(Vec3 a,Vec3 b){
        static CapEdge of(Vec3 a,Vec3 b){int order=Double.compare(a.x,b.x);if(order==0)order=Double.compare(a.y,b.y);if(order==0)order=Double.compare(a.z,b.z);return order<=0?new CapEdge(a,b):new CapEdge(b,a);}
        double length(){return a.distanceToSqr(b);}
    }
    /** Split both incident triangles together before bending their shared edge.
     * Independent subdivision leaves a curved T-junction open after projection. */
    private static List<Vec3[]> conformingCap(List<Vec3[]> faces,SweptBladeSurface surface){
        var triangles=new LinkedHashMap<Long,Vec3[]>();var owners=new HashMap<CapEdge,Set<Long>>();
        var projected=new HashMap<Vec3,Vec3>();
        var queue=new PriorityQueue<CapEdge>(Comparator.comparingDouble(CapEdge::length).reversed());long next=0;
        for(var face:faces){triangles.put(next,face);for(int i=0;i<3;i++){var edge=CapEdge.of(face[i],face[(i+1)%3]);owners.computeIfAbsent(edge,k->new LinkedHashSet<>()).add(next);if(curvedEdge(edge,surface,projected))queue.add(edge);}next++;}
        while(!queue.isEmpty()){
            var edge=queue.remove();var incident=owners.get(edge);if(incident==null)continue;
            var ids=List.copyOf(incident);var middle=edge.a.lerp(edge.b,.5);
            for(long id:ids){var face=triangles.remove(id);if(face==null)continue;int split=-1;
                for(int i=0;i<3;i++){var key=CapEdge.of(face[i],face[(i+1)%3]);if(key.equals(edge))split=i;var set=owners.get(key);set.remove(id);if(set.isEmpty())owners.remove(key);}
                if(split<0)throw new IllegalStateException("Missing cap edge");
                var a=face[split];var b=face[(split+1)%3];var c=face[(split+2)%3];
                for(var child:List.of(new Vec3[]{a,middle,c},new Vec3[]{middle,b,c})){
                    triangles.put(next,child);for(int i=0;i<3;i++){var key=CapEdge.of(child[i],child[(i+1)%3]);owners.computeIfAbsent(key,k->new LinkedHashSet<>()).add(next);if(curvedEdge(key,surface,projected))queue.add(key);}next++;
                }
            }
            if(triangles.size()>4096)return null;
        }return new ArrayList<>(triangles.values());
    }
    private static boolean curvedEdge(CapEdge edge,SweptBladeSurface surface,Map<Vec3,Vec3> cache){
        if(edge.length()<=.01)return false;
        var a=cache.computeIfAbsent(edge.a,surface::project);var b=cache.computeIfAbsent(edge.b,surface::project);
        // Flat spans need no tessellation, regardless of length. Quarter points
        // also catch an S-curve whose midpoint happens to lie on its chord.
        for(double t:new double[]{.25,.5,.75})if(cache.computeIfAbsent(edge.a.lerp(edge.b,t),surface::project).distanceToSqr(a.lerp(b,t))>1e-6)return true;
        return false;
    }
    private static <V> void weld(List<Edge> edges,Union groups,List<Fragment<V>> fragments,SweptBladeSurface surface,List<Seam> seams){
        weld(edges,groups,fragments,surface,seams,EPS);
    }
    private static <V> void weld(List<Edge> edges,Union groups,List<Fragment<V>> fragments,SweptBladeSurface surface,List<Seam> seams,double epsilon){
        if(edges.isEmpty())return;
        var indexed=new IndexedEdge[edges.size()];
        for(int i=0;i<edges.size();i++)indexed[i]=new IndexedEdge(i,edges.get(i));
        var tree=new EdgeTree(indexed,0,indexed.length);var candidates=new ArrayList<Edge>();
        for(int index=0;index<edges.size();index++){
            var edge=edges.get(index);if(edge.a.distanceToSqr(edge.b)<epsilon*epsilon)continue;
            candidates.clear();tree.collect(new IndexedEdge(index,edge),candidates);
            for(var candidate:candidates){
                if(groups.root(edge.owner)==groups.root(candidate.owner))continue;
                var overlap=overlap(edge,candidate,epsilon);if(overlap==null)continue;
                if(fragments!=null&&fragments.get(edge.owner).side!=fragments.get(candidate.owner).side
                        &&Math.abs(surface.distance(overlap[0]))<EPS&&Math.abs(surface.distance(overlap[1]))<EPS
                        &&Math.abs(surface.distance(overlap[0].lerp(overlap[1],.5)))<EPS&&surface.covers(overlap[0],overlap[1])){
                    // A cut can coincide exactly with an existing model edge. That
                    // edge is a wound, not an uncut bridge between the two sides.
                    boolean positive=fragments.get(edge.owner).side>0;
                    seams.add(new Seam(positive?edge.owner:candidate.owner,positive?candidate.owner:edge.owner,overlap[0],overlap[1]));
                }else groups.join(edge.owner,candidate.owner);
            }
        }
    }
    private static Vec3[] overlap(Edge a,Edge b){
        return overlap(a,b,EPS);
    }
    private static Vec3[] overlap(Edge a,Edge b,double epsilon){
        var delta=a.b.subtract(a.a);double length=delta.length();var direction=delta.scale(1/length);
        var p=b.a.subtract(a.a);var q=b.b.subtract(a.a);
        if(p.cross(direction).lengthSqr()>epsilon*epsilon||q.cross(direction).lengthSqr()>epsilon*epsilon)return null;
        double t=p.dot(direction),u=q.dot(direction);
        double from=Math.max(0,Math.min(t,u)),to=Math.min(length,Math.max(t,u));
        return to-from>epsilon?new Vec3[]{a.a.add(direction.scale(from)),a.a.add(direction.scale(to))}:null;
    }
    private record IndexedEdge(int index,Edge edge,Vec3 low,Vec3 high){
        IndexedEdge(int index,Edge edge){this(index,edge,
                new Vec3(Math.min(edge.a.x,edge.b.x),Math.min(edge.a.y,edge.b.y),Math.min(edge.a.z,edge.b.z)),
                new Vec3(Math.max(edge.a.x,edge.b.x),Math.max(edge.a.y,edge.b.y),Math.max(edge.a.z,edge.b.z)));}
        boolean overlaps(Vec3 lo,Vec3 hi){return high.x+EPS>=lo.x&&low.x-EPS<=hi.x&&high.y+EPS>=lo.y&&low.y-EPS<=hi.y&&high.z+EPS>=lo.z&&low.z-EPS<=hi.z;}
        double middle(int axis){return axis==0?low.x+high.x:axis==1?low.y+high.y:low.z+high.z;}
    }
    /** Tight edge bounds avoid quadratic candidate scans when a blade refines a
     * small patch of skin. Long edges still match every shorter neighbour. */
    private static final class EdgeTree {
        final IndexedEdge[] edges;final int from,to,first;final Vec3 low,high;final EdgeTree left,right;
        EdgeTree(IndexedEdge[] edges,int from,int to){
            this.edges=edges;this.from=from;this.to=to;Vec3 lo=edges[from].low,hi=edges[from].high;int first=edges[from].index;
            for(int i=from+1;i<to;i++){var e=edges[i];lo=new Vec3(Math.min(lo.x,e.low.x),Math.min(lo.y,e.low.y),Math.min(lo.z,e.low.z));hi=new Vec3(Math.max(hi.x,e.high.x),Math.max(hi.y,e.high.y),Math.max(hi.z,e.high.z));first=Math.min(first,e.index);}
            low=lo;high=hi;this.first=first;
            if(to-from<=8){left=right=null;return;}
            var size=hi.subtract(lo);int axis=size.x>=size.y&&size.x>=size.z?0:size.y>=size.z?1:2;
            Arrays.sort(edges,from,to,Comparator.comparingDouble(e->e.middle(axis)));int middle=(from+to)/2;
            left=new EdgeTree(edges,from,middle);right=new EdgeTree(edges,middle,to);
        }
        void collect(IndexedEdge target,List<Edge> result){
            if(first>=target.index||!target.overlaps(low,high))return;
            if(left!=null){left.collect(target,result);right.collect(target,result);return;}
            for(int i=from;i<to;i++){var e=edges[i];if(e.index<target.index&&target.overlaps(e.low,e.high))result.add(e.edge);}
        }
    }
    private static final class Union {
        final int[] parent,rank;
        Union(int size){parent=new int[size];rank=new int[size];for(int i=0;i<size;i++)parent[i]=i;}
        int root(int a){while(parent[a]!=a){parent[a]=parent[parent[a]];a=parent[a];}return a;}
        void join(int a,int b){a=root(a);b=root(b);if(a==b)return;if(rank[a]<rank[b])parent[a]=b;else {parent[b]=a;if(rank[a]==rank[b])rank[a]++;}}
    }
}
