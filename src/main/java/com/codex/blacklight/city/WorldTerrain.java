package com.codex.blacklight.city;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Pure, deterministic column rasterization. Never reads or changes neighbouring chunks. */
final class WorldTerrain {
    static final int HEIGHT=1536,SEA=63;
    private record Key(long seed,int x,int z){}
    private static final BoundedLoadingCache<Key,Column[]> CHUNKS=new BoundedLoadingCache<>(()->64);
    static Column[] chunk(long seed,int x,int z){return CHUNKS.get(new Key(seed,x,z),WorldTerrain::rasterize);}
    static Column column(long seed,int x,int z){return chunk(seed,Math.floorDiv(x,16),Math.floorDiv(z,16))[Math.floorMod(z,16)*16+Math.floorMod(x,16)];}
    static CityLayout.Zone zone(long seed,int x,int z){
        if(FutianMap.inside(x,z))return FutianLayout.zone(seed,x,z);
        int roll=Math.floorMod(CityLayout.hash(seed,Math.floorDiv(x,512),Math.floorDiv(z,512)),10);
        return roll<2?CityLayout.Zone.INFECTED:roll<4?CityLayout.Zone.CONFLICT:CityLayout.Zone.NORMAL;
    }
    private static Column[] rasterize(Key key){
        int bx=key.x*16,bz=key.z*16;
        // The core uses its own raster tiles: do not decode the geographic atlas for it.
        if(FutianMap.inside(bx,bz)&&FutianMap.inside(bx+15,bz+15)){
            Column[] core=new Column[256];
            for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var c=new Column(key.seed,bx+x,bz+z,SEA);c.core=FutianLayout.column(key.seed,bx+x,bz+z);c.top=c.core.top;core[z*16+x]=c;
            }
            return core;
        }
        var tile=WorldAtlas.tile(bx,bz);
        var features=tile.features.stream().filter(f->f.intersects(bx,bz,16)).toList();
        Column[] result=new Column[256];
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=bx+x,wz=bz+z;var c=new Column(key.seed,wx,wz,tile.height(wx,wz));
            if(FutianMap.inside(wx,wz)){c.core=FutianLayout.column(key.seed,wx,wz);c.top=c.core.top;result[z*16+x]=c;continue;}
            // OSM land-use polygons often stop before the road reservation. Reclaim the
            // at-grade road verge before applying explicit water, not after it.
            for(var f:features){
                if(f.kind==6&&f.layer==0&&f.intersects(wx,wz,1)
                        &&f.lineDistance(wx+.5,wz+.5)<=f.width/2+24){
                    c.urban=true;c.ground=Math.max(SEA+1,c.ground);c.water=0;
                }
            }
            for(var f:features){
                if(!f.intersects(wx,wz,1))continue;
                double distance=f.lines.length>0?f.lineDistance(wx+.5,wz+.5):Double.POSITIVE_INFINITY;
                boolean polygon=f.contains(wx+.5,wz+.5);
                // A narrow verge joins mapped urban plots across unmapped street margins.
                // Explicit water polygons are applied afterwards and always retain their water.
                if(f.kind==0){
                    if(polygon||f.edgeDistance(wx+.5,wz+.5)<=24){c.urban=true;c.ground=Math.max(SEA+1,c.ground);c.water=0;}
                    continue;
                }
                if(f.kind==6&&f.layer==0&&distance>f.width/2&&distance<=f.width/2+3){
                    if(c.road==0&&c.water==0){c.cover=3;c.sidewalk=true;c.lamp=distance>f.width/2+1&&distance<f.width/2+2&&Math.floorMod(wx+3*wz+f.id,24)==0;}
                    continue;
                }
                if(!polygon&&distance>f.width/2)continue;
                switch(f.kind){
                    case 1,2->{c.cover=f.kind;c.ground=Math.max(SEA+1,c.ground);c.water=0;}
                    case 3->{c.cover=3;c.ground=Math.max(SEA+1,c.ground);c.water=0;}
                    case 4,5->{
                        int water=f.kind==4?f.base:Math.max(SEA,c.ground);
                        c.ground=Math.min(c.ground,water-3);c.water=water;c.cover=0;
                    }
                    case 6,7->{
                        if(f.layer>0){c.bridge=Math.max(c.bridge,f.base+f.layer*5);}
                        else {c.ground=Math.max(c.ground,SEA+1);c.water=0;c.cover=3;c.sidewalk=false;c.lamp=false;}
                        c.road=f.kind;c.line=distance<.65&&Math.floorMod(wx+wz,10)<6;
                    }
                    case 8->{
                        var part=new Part(f,f.edgeDistance(wx+.5,wz+.5),wx,wz);
                        c.parts.add(part);if(f.landmark!=0)c.landmark=part;c.lamp=false;
                        if(f.min==0){c.ground=f.base;c.water=0;}
                    }
                    default->{}
                }
            }
            c.finish();result[z*16+x]=c;
        }
        return result;
    }
    static final class Part {
        final WorldAtlas.Feature feature;final double edge,radius,angle,halfWidth;
        Part(WorldAtlas.Feature f,double edge,int x,int z){
            feature=f;this.edge=edge;
            if(f.landmark==0){radius=angle=0;halfWidth=1;return;}
            double rx=(x+.5-(f.x0+f.x1)/2)/Math.max(1,(f.x1-f.x0)/2);
            double rz=(z+.5-(f.z0+f.z1)/2)/Math.max(1,(f.z1-f.z0)/2);
            radius=LandmarkShape.radius(f.landmark,rx,rz);angle=Math.atan2(rz,rx);
            halfWidth=Math.max(1,Math.min(f.x1-f.x0,f.z1-f.z0)/2);
        }
        BlockState landmark(int y){
            var f=feature;int dy=y-f.base;if(dy<0||dy>f.height)return b(Blocks.AIR);
            double scale=f.scales[dy],wall=1.5/halfWidth;
            if(radius>scale)return b(Blocks.AIR);
            if(dy==f.height||radius>f.scales[dy+1])return b(Blocks.SMOOTH_QUARTZ);
            if(radius>scale-wall){
                double sectors=(angle+Math.PI)/(Math.PI*2)*(f.landmark==1?8:28);
                boolean rib=Math.abs(sectors-Math.rint(sectors))<.12;
                boolean diagonal=f.landmark==2&&Math.abs((sectors+dy/14.0)-Math.rint(sectors+dy/14.0))<.09;
                return b(rib||diagonal?Blocks.QUARTZ_BLOCK:dy%5==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.LIGHT_BLUE_STAINED_GLASS);
            }
            return b(dy%5==0?Blocks.SMOOTH_STONE:Blocks.AIR);
        }
    }
    static final class Column {
        final long seed;final int x,z;int ground,water,cover,road,bridge,top,treeBase,treeDistance,treeHeight;
        boolean line,urban,sidewalk,lamp;FutianLayout.Column core;Part landmark;final List<Part> parts=new ArrayList<>();
        Column(long seed,int x,int z,int ground){
            this.seed=seed;this.x=x;this.z=z;
            // The legacy core is at sea-level numerically, but its border is dry ground.
            int distance=Math.max(Math.max(-x,x-FutianMap.WIDTH),Math.max(-z,z-FutianMap.DEPTH));
            if(distance>=0&&distance<32)ground=Math.max(SEA+1,ground);
            this.ground=Math.max(2,ground);water=ground<=SEA?SEA:0;
        }
        void finish(){
            top=Math.max(ground,Math.max(water,bridge));
            for(var part:parts)top=Math.max(top,part.feature.base+part.feature.height);
            if((cover==1||cover==2||urban&&cover==0)&&parts.isEmpty()&&road==0&&water==0){
                int tx=Math.floorDiv(x+4,9)*9,tz=Math.floorDiv(z+4,9)*9;
                long hash=CityLayout.hash(seed,tx,tz);
                treeDistance=Math.max(Math.abs(x-tx),Math.abs(z-tz));
                if(Math.floorMod(hash,cover==1?3:cover==2?6:12)==0&&treeDistance<=2){
                    treeBase=Math.max(SEA+1,WorldAtlas.ground(tx,tz));treeHeight=5+Math.floorMod(hash,3);
                    if(treeBase>SEA)top=Math.max(top,treeBase+treeHeight+1);else treeBase=0;
                }
            }
            if(lamp&&parts.isEmpty())top=Math.max(top,ground+6);
            top=Math.min(HEIGHT-1,top);
        }
        BlockState state(int y){
            if(y<0||y>=HEIGHT)return b(Blocks.VOID_AIR);
            if(core!=null)return y>=FutianMap.HEIGHT?b(Blocks.AIR):core.state(y);
            if(y==0)return b(Blocks.BEDROCK);
            if(y<ground-3)return b(Blocks.STONE);
            if(y<ground)return b(water>0?Blocks.SAND:Blocks.DIRT);
            if(y==ground)return b(water>0?Blocks.SAND:sidewalk?Blocks.SMOOTH_STONE:cover==3?(road==7?Blocks.IRON_BLOCK:line?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE):Blocks.GRASS_BLOCK);
            // Named massing replaces its overlapping OSM structural parts, which otherwise
            // re-extrude the old cylinder over the tapered crown (notably the bamboo tower).
            if(landmark!=null&&y>=landmark.feature.base&&y<=landmark.feature.base+landmark.feature.height)
                return landmark.landmark(y);
            for(int i=parts.size()-1;i>=0;i--){
                var part=parts.get(i);var f=part.feature;
                if(y<f.base+f.min||y>f.base+f.height)continue;
                if(f.landmark!=0)return part.landmark(y);
                int dy=y-f.base-f.min;
                if(y==f.base+f.height||dy%4==0)return b(Blocks.SMOOTH_STONE);
                if(part.edge<1.1){
                    if(f.min==0&&dy<=2&&Math.floorMod(x+z,11)<3)return b(Blocks.AIR);
                    if(zone(seed,x,z)==CityLayout.Zone.INFECTED&&dy<6&&Math.floorMod(CityLayout.hash(seed,x,z),7)<4)
                        return CityBlocks.FLESH.get().defaultBlockState();
                    boolean window=dy%4!=0&&Math.floorMod(x+z,5)!=0;
                    return b(window?Blocks.LIGHT_BLUE_STAINED_GLASS:Math.floorMod(f.id,3)==0?Blocks.BRICKS:Blocks.LIGHT_GRAY_CONCRETE);
                }
                return b(Blocks.AIR);
            }
            if(y==bridge)return b(road==7?Blocks.IRON_BLOCK:line?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE);
            if(bridge>0&&y<bridge&&Math.floorMod(x,16)==0&&Math.floorMod(z,8)<2)return b(Blocks.STONE_BRICKS);
            if(y<=water)return b(Blocks.WATER);
            if(lamp&&y>ground&&y<=ground+6)return b(y==ground+6?Blocks.SEA_LANTERN:Blocks.DEEPSLATE_TILE_WALL);
            if(treeBase>0){
                if(treeDistance==0&&y>treeBase&&y<=treeBase+treeHeight)return b(Blocks.OAK_LOG);
                if(y>=treeBase+treeHeight-2&&y<=treeBase+treeHeight+1)
                    return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            }
            return b(Blocks.AIR);
        }
    }
    private static BlockState b(Block block){return block.defaultBlockState();}
    private WorldTerrain(){}
}
