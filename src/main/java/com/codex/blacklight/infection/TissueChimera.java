package com.codex.blacklight.infection;

import java.util.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Donor anatomy changes body topology. No entity IDs select a pre-authored monster. */
public final class TissueChimera {
    private static final java.util.regex.Pattern APPENDAGE=java.util.regex.Pattern.compile("(?:^|_)(?:tentacle|tendril|pseudopod|appendage)_?([0-9]*)(?:_|$)");
    public static int appendageRole(String path,int fallback){
        String leaf=path.substring(path.lastIndexOf('/')+1).replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
        var match=APPENDAGE.matcher(leaf);if(!match.find())return fallback;
        String index=match.group(1);int side=index.isEmpty()?path.hashCode():index.charAt(index.length()-1)-'0';
        return 2+(side&1);
    }
    public record Part(String name,int role,V pivot,List<V> vertices,List<int[]> faces,List<float[]> uv,List<Integer> colors,int material) {
        public Part(String name,int role,V pivot,List<V> vertices,List<int[]> faces,List<float[]> uv,List<Integer> colors){this(name,role,pivot,vertices,faces,uv,colors,-1);}
        public Part(String name,int role,V pivot,List<V> vertices,List<int[]> faces){this(name,role,pivot,vertices,faces,Collections.nCopies(faces.size(),new float[]{0,0,1,0,1,1,0,1}),Collections.nCopies(faces.size(),-1));}
        public Part(String name,int role,V pivot,List<V> vertices,List<int[]> faces,List<float[]> uv){this(name,role,pivot,vertices,faces,uv,Collections.nCopies(faces.size(),-1));}
    }
    public record Body(List<Part> parts,Box bounds,Box trunk) {
        public Body(List<Part> parts,Box bounds){this(parts,bounds,parts.stream().filter(p->p.role()==0).max(Comparator.comparingDouble(TissueChimera::volume)).map(p->TissueChimera.bounds(p.vertices())).orElse(bounds));}
    }
    public record Donor(String species,TissueInheritance.BodySource source,Body body) {}
    public record Piece(String species,String name,int role,V pivot,V root,List<V> vertices,List<int[]> faces,List<float[]> uv,List<Integer> colors,int material) {}
    public record Mapping(V origin,V root,float scale){public V point(V p){return root.add(p.sub(origin).mul(scale));}}
    public record Assembly(List<Piece> pieces,Set<String> species,boolean uprightUpper,Mapping upper) {}
    private TissueChimera(){}
    /** The new upper body already contains the donor's evolved bulk. Keep the
     * receiver's supporting anatomy readable instead of growing it twice.
     * This is a render field only; the saved genes and combat state are intact. */
    public static TissueEvolution.State receiverEvolution(TissueEvolution.State nativeState,TissueEvolution.State inherited,float upperGrowth){
        float g=Math.clamp(upperGrowth,0,1),share=1-.82F*g;
        var a=inherited.aggregate();
        var aggregate=g>=1?TissueAggregate.Form.NONE:new TissueAggregate.Form(a.amount()*(1-g),a.radial(),a.quadruped(),a.upright(),a.handedness());
        return new TissueEvolution.State(inherited.generation(),nativeState.muscle()+(inherited.muscle()-nativeState.muscle())*share,
                nativeState.carapace()+(inherited.carapace()-nativeState.carapace())*share,
                nativeState.jaw()+(inherited.jaw()-nativeState.jaw())*share,
                nativeState.grasp()+(inherited.grasp()-nativeState.grasp())*share,
                nativeState.crest()+(inherited.crest()-nativeState.crest())*share,aggregate);
    }
    public static float growth(TissueInheritance.BodySource source,double visualWork){
        float t=(float)Math.clamp((TissueInheritance.NEW_MATURITY_WORK-Math.max(0,source.work()-Math.clamp(visualWork,0,20)))/600,0,1);
        return t*t*(3-2*t);
    }
    public static Assembly assemble(Box host,int hostPlan,List<Donor> input){
        return assemble(host,hostPlan,input,null);
    }
    public static Assembly assemble(Box host,int hostPlan,List<Donor> input,Box hostThorax){
        return assemble(host,hostPlan,input,hostThorax,host.high().y()+host.span(1)*.5F);
    }
    public static Assembly assemble(Box host,int hostPlan,List<Donor> input,Box hostThorax,float floor){
        return assemble(host,hostPlan,input,hostThorax,floor,8);
    }
    public static Assembly assemble(Box host,int hostPlan,List<Donor> input,Box hostThorax,float floor,int accessorySockets){
        var donors=input.stream().filter(d->d.body()!=null&&!d.body().parts().isEmpty()).sorted(Comparator.comparing(Donor::species)).toList();
        var pieces=new ArrayList<Piece>();var expressed=new TreeSet<String>();
        V centre=host.centre();float width=host.span(0),height=host.span(1),depth=host.span(2);
        boolean upright=false,radialBody=false,softBody=false;int heads=0;Mapping upperMapping=null;
        for(var d:donors){
            int before=pieces.size();var parts=d.body.parts;var body=d.body.bounds;
            if(d.source.plan()==0&&hostPlan==2&&!upright){
                var torso=parts.stream().filter(p->p.role==0).max(Comparator.comparingDouble(TissueChimera::volume)).orElse(null);
                if(torso==null)continue;
                Box trunk=d.body.trunk;V waist=new V(trunk.centre().x(),trunk.high().y(),trunk.centre().z());
                var upper=parts.stream().filter(p->p.role>=0&&p.role<=3).toList();
                var selected=bounds(upper.stream().flatMap(p->p.vertices.stream()).toList());
                float scale=Math.max(width*.95F,height*.85F)/Math.max(1,trunk.span(1));
                scale=Math.min(scale,width*3/Math.max(1,waist.y()-selected.low().y()));
                scale=Math.clamp(scale,.40F,2.5F);
                // Body tissue carries the fusion. A head joint is never an attachment socket.
                Box thorax=hostThorax==null?host:hostThorax;
                V root=new V(thorax.centre().x(),thorax.low().y()+thorax.span(1)*.15F,thorax.low().z()+thorax.span(2)*.30F);
                for(var p:upper)add(pieces,d,p,waist,root,scale,0);
                upperMapping=new Mapping(waist,root,scale);
                connector(pieces,d.species,"upper_body_fusion",root.add(new V(0,height*.45F,depth*.16F)),root,width*.32F,depth*.30F);
                upright=true;
            }else if(d.source.plan()==1||d.source.plan()==0){
                var headParts=parts.stream().filter(p->p.role==1).toList();
                // Headed donors remodel the existing frame through their genes.
                // They do not each add another complete skull, neck or torso.
                if(headParts.isEmpty()&&!softBody){
                    // Keep every material/surface on each appendage. A long trunk
                    // is not a tentacle, and eight mesh batches are not eight limbs.
                    var sockets=new LinkedHashMap<V,List<Part>>();
                    for(var p:parts)if(p.role>=2&&p.role<=5)sockets.computeIfAbsent(p.pivot,k->new ArrayList<>()).add(p);
                    int budget=Math.clamp(accessorySockets,0,8);
                    var limbs=sockets.values().stream().limit(budget).flatMap(List::stream).toList();
                    if(!limbs.isEmpty()){
                        float scale=Math.clamp(width*1.7F/Math.max(1,body.span(0)),.35F,1.5F);
                        V root=centre.add(new V(0,height*.10F,depth*.30F));int limbIndex=0,limbCount=Math.min(budget,sockets.size());
                        for(var group:sockets.values().stream().limit(budget).toList()){
                            float angle=limbCount==1?0:(limbIndex++/(float)(limbCount-1)-.5F)*(float)Math.PI*1.2F;
                            V socket=root.add(new V((float)Math.sin(angle)*width*.38F,0,(float)Math.cos(angle)*depth*.12F));
                            V origin=group.getFirst().pivot;float down=group.stream().flatMap(p->p.vertices.stream()).map(v->v.y()-origin.y()).max(Float::compare).orElse(0F);
                            float fitted=down>0?Math.min(scale,Math.max(.05F,(floor-socket.y()-.25F)/down)):scale;
                            for(var p:group)add(pieces,d,p,origin,socket,fitted,0);
                        }
                        connector(pieces,d.species,"soft_body_fusion",centre,root,width*.28F,height*.26F);
                        softBody=true;
                    }
                }
            }
            if(pieces.size()>before)expressed.add(d.species);
        }
        return new Assembly(List.copyOf(pieces),Set.copyOf(expressed),upright,upperMapping);
    }
    private static double volume(Part p){var b=bounds(p.vertices);return b.span(0)*b.span(1)*b.span(2);}
    private static void add(List<Piece> out,Donor d,Part p,V origin,V root,float scale,float yaw){
        var points=p.vertices.stream().map(v->place(v,origin,root,scale,yaw)).toList();
        out.add(new Piece(d.species,p.name,p.role,place(p.pivot,origin,root,scale,yaw),root,points,p.faces,p.uv,p.colors,p.material));
    }
    private static V place(V p,V origin,V root,float scale,float yaw){V v=p.sub(origin).mul(scale);return root.add(new V(v.x()*(float)Math.cos(yaw)+v.z()*(float)Math.sin(yaw),v.y(),v.z()*(float)Math.cos(yaw)-v.x()*(float)Math.sin(yaw)));}
    private static void connector(List<Piece> out,String species,String name,V buried,V end,float w,float d){
        var points=new ArrayList<V>();var faces=new ArrayList<int[]>();
        for(int ring=0;ring<3;ring++){
            float t=ring/2F,r=1-.25F*t;V c=buried.mul(1-t).add(end.mul(t));
            for(int i=0;i<8;i++){float a=i*(float)Math.PI/4;points.add(c.add(new V((float)Math.cos(a)*w*r,0,(float)Math.sin(a)*d*r)));}
        }
        for(int ring=0;ring<2;ring++)for(int i=0;i<8;i++)faces.add(new int[]{ring*8+i,ring*8+(i+1)%8,(ring+1)*8+(i+1)%8,(ring+1)*8+i});
        out.add(new Piece(species,name,0,end,buried,List.copyOf(points),List.copyOf(faces),Collections.nCopies(faces.size(),new float[]{.05F,.05F,.23F,.05F,.23F,.23F,.05F,.23F}),Collections.nCopies(faces.size(),-1),-1));
    }
    public static List<V> pose(Piece piece,float growth,float walk,float speed,float attack,float age,float headYaw){
        float angle=0;
        if(piece.role==2||piece.role==3)angle=(piece.role==2?1:-1)*(.15F*(float)Math.sin(walk)*speed)-attack*1.1F;
        if(piece.role==4||piece.role==5)angle=(piece.role==4?1:-1)*.28F*(float)Math.sin(walk)*speed;
        float a=angle,c=(float)Math.cos(a),s=(float)Math.sin(a),yaw=piece.role==1?Math.clamp(headYaw,-.5F,.5F):0;
        return piece.vertices.stream().map(p->{
            V v=p.sub(piece.pivot);v=new V(v.x(),v.y()*c-v.z()*s,v.y()*s+v.z()*c);
            v=new V(v.x()*(float)Math.cos(yaw)+v.z()*(float)Math.sin(yaw),v.y(),v.z()*(float)Math.cos(yaw)-v.x()*(float)Math.sin(yaw));
            return piece.root.add(v.add(piece.pivot).sub(piece.root).mul(growth));
        }).toList();
    }
    public static Box bounds(List<V> points){
        float x=Float.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=-x,Z=-x;
        for(var p:points){x=Math.min(x,p.x());y=Math.min(y,p.y());z=Math.min(z,p.z());X=Math.max(X,p.x());Y=Math.max(Y,p.y());Z=Math.max(Z,p.z());}
        return new Box(new V(x,y,z),new V(X,Y,Z));
    }
}
