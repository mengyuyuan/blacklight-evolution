package com.codex.blacklight.city;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.codex.blacklight.util.MapResources;

/** Frozen geographic data, shared by all saves. No network, world access or mutable block data. */
final class WorldAtlas {
    static final int TILE=1024, STEP=32, GRID=33;
    static final double ORIGIN_LON=114.048, ORIGIN_LAT=22.552;
    static final double LON_METRES=102811.19191866614, LAT_METRES=111319.49079327358;
    record Cell(int x,int z){}
    record Region(String id,Set<Cell> cells,String fingerprint,JsonObject profiles){}
    private static final BoundedLoadingCache<Cell,Tile> CACHE=new BoundedLoadingCache<>(()->16);
    private static final List<Region> REGIONS=regions();
    static final List<Cell> CELLS=REGIONS.stream().flatMap(r->r.cells.stream())
        .distinct().sorted(Comparator.<Cell>comparingInt(c->Math.max(Math.abs(c.x),Math.abs(c.z)))
        .thenComparingInt(Cell::z).thenComparingInt(Cell::x)).toList();
    static int worldX(double longitude){
        double delta=longitude-ORIGIN_LON;
        delta-=360*Math.floor((delta+180)/360);
        return (int)Math.round(delta*LON_METRES);
    }
    static int worldZ(double latitude){return (int)Math.round((ORIGIN_LAT-latitude)*LAT_METRES);}
    /** Identity uses the small atlas index; it never loads a terrain tile or chunk. */
    static String regionId(int x,int z){
        var cell=new Cell(Math.floorDiv(x,TILE),Math.floorDiv(z,TILE));
        for(var region:REGIONS)if(region.cells.contains(cell))return region.id;
        return "wilderness";
    }
    static String fingerprint(){return digest(REGIONS.stream().map(r->r.id+":"+r.fingerprint).reduce("",(a,b)->a+"\n"+b));}
    private static String digest(String text){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static JsonObject json(String path,boolean gzip){
        String name="/data/blacklight/worldmap/"+path;
        try(var raw=gzip?MapResources.open(WorldAtlas.class,name):WorldAtlas.class.getResourceAsStream(name)){
            if(raw==null)throw new IOException("Missing bundled map: "+path);
            try(var reader=new InputStreamReader(raw,StandardCharsets.UTF_8)){
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }catch(IOException e){throw new IllegalStateException("Cannot read bundled world atlas",e);}
    }
    private static List<Region> regions(){
        var regions=new ArrayList<Region>();
        for(var entry:json("atlas.json",false).getAsJsonArray("regions")){
            String id=entry.getAsString();
            if(!id.matches("[a-z0-9_-]+"))throw new IllegalStateException("Invalid atlas region");
            var index=json(id+"/index.json",false);
            if(index.get("schema").getAsInt()!=1||index.get("tileSize").getAsInt()!=TILE)
                throw new IllegalStateException("Unsupported world atlas schema: "+id);
            var cells=new HashSet<Cell>();
            for(var tile:index.getAsJsonArray("tiles")){
                var o=tile.getAsJsonObject();cells.add(new Cell(o.get("x").getAsInt(),o.get("z").getAsInt()));
            }
            var profiles=json(id+"/surface_profiles.json",false);
            regions.add(new Region(id,Set.copyOf(cells),digest(index.toString()+profiles),profiles));
        }
        return List.copyOf(regions);
    }
    static Tile tile(int x,int z){return CACHE.get(new Cell(Math.floorDiv(x,TILE),Math.floorDiv(z,TILE)),WorldAtlas::load);}
    private static Tile load(Cell cell){
        String key=cell.x+"_"+cell.z;
        for(var region:REGIONS)if(region.cells.contains(cell)){
            return new Tile(json(region.id+"/"+key+".json.gz",true),region.profiles,key);
        }
        for(var region:REGIONS){
            var fringe=region.profiles.getAsJsonObject("fringe").get(key);
            if(fringe!=null)return new Tile(cell,fringe.getAsString());
        }
        return Tile.OCEAN;
    }
    static int ground(int x,int z){return tile(x,z).height(x,z);}
    static final class Tile {
        static final Tile OCEAN=new Tile();
        final int x,z;final short[] heights;final List<Feature> features;
        private final byte[] dry; private final short[] cuts;
        private Tile(){x=z=0;heights=null;dry=null;cuts=null;features=List.of();}
        private Tile(Cell cell,String fringe){
            x=cell.x*TILE;z=cell.z*TILE;heights=new short[GRID*GRID];dry=null;cuts=null;features=List.of();
            var bytes=new ByteArrayInputStream(Base64.getDecoder().decode(fringe));
            try(var input=new DataInputStream(new java.util.zip.InflaterInputStream(bytes))){
                for(int i=0;i<heights.length;i++)heights[i]=input.readShort();
                if(input.read()!=-1)throw new IOException("Extra elevation samples");
            }catch(IOException ex){throw new IllegalStateException("Invalid atlas margin "+cell,ex);}
        }
        Tile(JsonObject object,JsonObject profiles,String key){
            x=object.get("x").getAsInt()*TILE;z=object.get("z").getAsInt()*TILE;
            var e=object.getAsJsonArray("e");if(e.size()!=GRID*GRID)throw new IllegalStateException("Invalid elevation grid");
            heights=new short[e.size()];for(int i=0;i<heights.length;i++)heights[i]=e.get(i).getAsShort();
            var profile=profiles.getAsJsonObject("tiles").getAsJsonObject(key);
            dry=profile!=null&&profile.has("dry")?Base64.getDecoder().decode(profile.get("dry").getAsString()):null;
            if(profile!=null&&profile.has("cuts")){
                cuts=new short[GRID*GRID];var values=profile.getAsJsonArray("cuts");
                for(int i=0;i<values.size();i+=2)cuts[values.get(i).getAsInt()]=values.get(i+1).getAsShort();
            }else cuts=null;
            var fs=new ArrayList<Feature>();for(var f:object.getAsJsonArray("f"))fs.add(new Feature(f.getAsJsonObject(),profiles));
            var links=profiles.getAsJsonObject("links").getAsJsonArray(key);
            if(links!=null){
                for(var link:links)fs.add(new Feature(link.getAsJsonObject(),profiles));
                fs.sort(Comparator.comparingInt(f->f.kind));
            }
            features=List.copyOf(fs);
        }
        int height(int wx,int wz){
            return heights==null?48:interpolate(heights,wx,wz);
        }
        boolean inlandBasin(int wx,int wz){
            int index=((wz-z)/STEP)*32+(wx-x)/STEP;
            return dry!=null&&(dry[index/8]&(1<<(index%8)))!=0;
        }
        int streetCut(int wx,int wz){return cuts==null?0:interpolate(cuts,wx,wz);}
        private int interpolate(short[] values,int wx,int wz){
            double gx=(wx-x)/(double)STEP,gz=(wz-z)/(double)STEP;
            int ix=Math.max(0,Math.min(31,(int)Math.floor(gx))),iz=Math.max(0,Math.min(31,(int)Math.floor(gz)));
            double fx=gx-ix,fz=gz-iz;
            return (int)Math.round((values[iz*GRID+ix]*(1-fx)+values[iz*GRID+ix+1]*fx)*(1-fz)
                +(values[(iz+1)*GRID+ix]*(1-fx)+values[(iz+1)*GRID+ix+1]*fx)*fz);
        }
    }
    static final class Feature {
        final int kind,id,base,height,min,layer,landmark;final double width;
        final double[][][] polygons;final double[][] lines;
        final double[] scales,ramps;final int waterLevel;
        double x0=Double.POSITIVE_INFINITY,z0=x0,x1=Double.NEGATIVE_INFINITY,z1=x1;
        Feature(JsonObject o){this(o,null);}
        Feature(JsonObject o,JsonObject profiles){
            kind=o.get("k").getAsInt();id=o.get("id").getAsInt();base=o.get("y").getAsInt();
            height=o.get("h").getAsInt();min=o.get("m").getAsInt();layer=o.get("l").getAsInt();width=o.get("w").getAsDouble();
            landmark=o.has("a")?o.get("a").getAsInt():0;
            String key=Integer.toString(id);
            var water=kind==4&&profiles!=null?profiles.getAsJsonObject("water").get(key):null;
            waterLevel=water==null?base:Math.min(base,water.getAsInt());
            var ends=layer>0&&profiles!=null?profiles.getAsJsonObject("ramps").getAsJsonArray(key):null;
            ramps=ends==null?null:new double[ends.size()];
            if(ends!=null)for(int i=0;i<ends.size();i++)ramps[i]=ends.get(i).getAsDouble();
            var p=o.getAsJsonArray("p");polygons=new double[p.size()][][];
            for(int i=0;i<p.size();i++)polygons[i]=rings(p.get(i).getAsJsonArray());
            lines=rings(o.getAsJsonArray("s"));
            scales=landmark==0?null:new double[height+2];
            if(scales!=null)for(int y=0;y<scales.length;y++)scales[y]=LandmarkShape.scale(landmark,y/(double)height);
        }
        private double[][] rings(JsonArray array){
            double[][] result=new double[array.size()][];
            for(int i=0;i<result.length;i++){
                var ring=array.get(i).getAsJsonArray();result[i]=new double[ring.size()];
                for(int j=0;j<ring.size();j+=2){
                    double x=ring.get(j).getAsDouble(),z=ring.get(j+1).getAsDouble();result[i][j]=x;result[i][j+1]=z;
                    x0=Math.min(x0,x);x1=Math.max(x1,x);z0=Math.min(z0,z);z1=Math.max(z1,z);
                }
            }
            return result;
        }
        int deck(int grade,double x,double z){
            int rise=layer*5;
            if(ramps==null)return grade+rise;
            double approach=Math.min(60,rise*4);
            double distance=approach;
            for(int i=0;i<ramps.length;i+=2)distance=Math.min(distance,Math.hypot(x-ramps[i],z-ramps[i+1]));
            return grade+(int)Math.floor(rise*distance/approach);
        }
        boolean intersects(int x,int z,int size){double pad=kind==0?24:width/2+(kind==6?24:4);return x+size>=x0-pad&&x<=x1+pad&&z+size>=z0-pad&&z<=z1+pad;}
        boolean contains(double x,double z){
            for(var polygon:polygons){
                if(polygon.length==0||!inside(polygon[0],x,z))continue;
                boolean hole=false;for(int i=1;i<polygon.length;i++)if(inside(polygon[i],x,z)){hole=true;break;}
                if(!hole)return true;
            }
            return false;
        }
        double lineDistance(double x,double z){double distance=Double.POSITIVE_INFINITY;for(var line:lines)distance=Math.min(distance,distance(line,x,z));return distance;}
        double edgeDistance(double x,double z){double d=Double.POSITIVE_INFINITY;for(var p:polygons)for(var r:p)d=Math.min(d,distance(r,x,z));return d;}
        static boolean inside(double[] ring,double x,double z){
            boolean inside=false;
            for(int i=0,j=ring.length-2;i<ring.length;j=i,i+=2){
                double xi=ring[i],zi=ring[i+1],xj=ring[j],zj=ring[j+1];
                if((zi>z)!=(zj>z)&&x<(xj-xi)*(z-zi)/(zj-zi)+xi)inside=!inside;
            }
            return inside;
        }
        static double distance(double[] points,double x,double z){
            double d=Double.POSITIVE_INFINITY;
            for(int i=2;i<points.length;i+=2){
                double ax=points[i-2],az=points[i-1],dx=points[i]-ax,dz=points[i+1]-az,l=dx*dx+dz*dz;
                double t=l==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/l));
                double ex=x-ax-t*dx,ez=z-az-t*dz;d=Math.min(d,ex*ex+ez*ez);
            }
            return Math.sqrt(d);
        }
    }
    private WorldAtlas(){}
}
