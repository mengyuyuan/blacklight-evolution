package com.codex.blacklight.combat;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public final class GroundCases {
    public static final List<String> NAMES=List.of("stone","wood","stairs","slabs","gap","wall","cliff","ceiling","air","water","waterlogged","empty");
    public static BlockPos origin(BlockPos base,int index){return base.offset((index%4)*9,0,(index/4)*9);}
    public static Vec3 at(BlockPos origin,String name){return Vec3.atCenterOf(origin).add(0,name.equals("air")?1.75:name.equals("waterlogged")?.0:.5,0);}
    public static void setup(Level level,BlockPos base){
        for(int i=0;i<NAMES.size();i++){
            String name=NAMES.get(i);var p=origin(base,i);
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                for(int y=-2;y<4;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                if(name.equals("empty")||name.equals("gap")&&x==1)continue;
                var material=name.equals("wood")?Blocks.OAK_PLANKS:Blocks.STONE_BRICKS;
                int floor=name.equals("cliff")&&x>0?-2:0;
                level.setBlock(p.offset(x,floor,z),material.defaultBlockState(),2);
                if(name.equals("wall")&&x==1)level.setBlock(p.offset(x,1,z),Blocks.STONE.defaultBlockState(),2);
                if(name.equals("ceiling"))level.setBlock(p.offset(x,2,z),Blocks.OAK_PLANKS.defaultBlockState(),2);
                if(name.equals("stairs")||name.equals("slabs")){
                    if(x==1)level.setBlock(p.offset(x,1,z),name.equals("stairs")?Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST):
                            Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM),2);
                    if(x>=2)level.setBlock(p.offset(x,1,z),Blocks.OAK_PLANKS.defaultBlockState(),2);
                }
                boolean rim=Math.abs(x)==3||Math.abs(z)==3;
                if(rim&&(name.equals("water")||name.equals("waterlogged"))){
                    level.setBlock(p.offset(x,0,z),Blocks.GLASS.defaultBlockState(),2);
                    level.setBlock(p.offset(x,1,z),Blocks.GLASS.defaultBlockState(),2);
                    continue;
                }
                if(name.equals("water"))level.setBlock(p.offset(x,1,z),Blocks.WATER.defaultBlockState(),2);
                if(name.equals("waterlogged"))level.setBlock(p.offset(x,0,z),Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED,true),2);
            }
        }
    }
    private static void require(boolean okay,String detail){if(!okay)throw new AssertionError(detail);}
    public static Map<String,Object> verify(Level level,BlockPos base){
        var result=new LinkedHashMap<String,Object>();
        for(int i=0;i<NAMES.size();i++){
            var name=NAMES.get(i);var p=origin(base,i);var point=at(p,name);
            var wave=ConsumeGroundSurface.sample(level,point,ConsumeGroundSurface.Load.MAUL);
            if(List.of("air","water","waterlogged","empty").contains(name)){
                require(wave==null,name+" got unsupported source");result.put(name,"no surface");continue;
            }
            require(wave!=null,name+" missing contact");
            require(wave.tiles().get(0).pos().equals(p),name+" missing actual source "+wave.tiles());
            if(name.equals("stone")||name.equals("wood")){
                var light=ConsumeGroundSurface.sample(level,point,ConsumeGroundSurface.Load.BODY);
                require(wave.tiles().size()>2*light.tiles().size(),name+" expected wider maul footprint");
                require(wave.tiles().stream().allMatch(t->t.state().is(name.equals("wood")?Blocks.OAK_PLANKS:Blocks.STONE_BRICKS)),name+" lost material");
            }else if(name.equals("stairs")||name.equals("slabs")){
                require(wave.tiles().stream().anyMatch(t->t.pos().equals(p.offset(1,1,0))),name+" missed step");
                require(wave.tiles().stream().anyMatch(t->t.pos().equals(p.offset(2,1,0))),name+" missed connected upper tread");
            }else if(name.equals("ceiling")){
                require(wave.tiles().stream().allMatch(t->t.pos().getY()==p.getY()),"lifted a ceiling");
            }else{
                require(wave.tiles().stream().noneMatch(t->t.pos().getX()>p.getX()),name+" crossed discontinuity");
            }
            result.put(name,wave.tiles().size());
        }
        return result;
    }
}
