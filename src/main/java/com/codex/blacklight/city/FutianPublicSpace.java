package com.codex.blacklight.city;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Civic-axis detail pass. Geometry stays on the OSM site; interiors are playable adaptations. */
public final class FutianPublicSpace {
    private static BlockState b(Block block){return block.defaultBlockState();}
    private static boolean cultural(int x,int z){return x>=378&&x<=472&&((z>=140&&z<=267)||(z>=318&&z<=439));}
    public static int top(int x,int z){return cultural(x,z)?99:x>=642&&x<=694&&z>=725&&z<=945?70:63;}
    public static BlockState state(int x,int y,int z,BlockState original){
        if(y<63)return original;
        if(x>=642&&x<=694&&z>=725&&z<=945){
            if(x>=658&&x<=678){
                if(y==63)return b(x==658||x==678?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_QUARTZ);
                if(y<=66&&(original.is(Blocks.GRASS)||original.is(Blocks.TALL_GRASS)||original.is(Blocks.FERN)))return b(Blocks.AIR);
            }
            int along=Math.floorMod(z-760,40);
            if((x==648||x==688)&&z>=760&&z<=920&&along==0){
                if(y>=64&&y<=68)return b(Blocks.POLISHED_ANDESITE);
                if(y==69)return b(Blocks.SEA_LANTERN);
                if(y==70)return b(Blocks.SMOOTH_STONE_SLAB);
            }
            if(((x>=643&&x<=646)||(x>=690&&x<=693))&&z>=755&&z<=915&&along>=8&&along<=11){
                if(y==64)return b(Blocks.SMOOTH_STONE);
                if(y==65)return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,x<668?Direction.WEST:Direction.EAST);
            }
        }
        if(!cultural(x,z))return original;
        boolean music=z<280;
        // Eastern curtain facade: warm concert hall, silver/clear library, with vertical fins.
        int glassX=451+(int)Math.round(3*Math.sin((z-(music?140:318))*.055));
        if(x==glassX&&y>=64&&y<=97){
            boolean entrance=(music?Math.abs(z-205):Math.abs(z-376))<=4&&y<=68;
            if(entrance)return b(Blocks.AIR);
            if(y==97||y==64||(z%9==0))return b(music?Blocks.YELLOW_TERRACOTTA:Blocks.WHITE_CONCRETE);
            return b(music?Blocks.YELLOW_STAINED_GLASS:Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        // Tree-shaped supports are solid Minecraft blocks, with open space between branches.
        if(x>=454&&x<=472&&y>=64&&y<=91){
            int centre=music?(z<205?171:239):(z<377?341:411);
            int dz=z-centre,dx=x-463,h=y-64;
            boolean trunk=Math.abs(dx)<=1&&Math.abs(dz)<=1&&h<=15;
            int branch=Math.max(0,(h-11)/2);
            boolean fork=h>=12&&((Math.abs(Math.abs(dx)-branch)<=1&&Math.abs(dz)<=1)
                    ||(Math.abs(Math.abs(dz)-branch)<=1&&Math.abs(dx)<=1));
            if(trunk||fork)return b(music?Blocks.YELLOW_CONCRETE:Blocks.QUARTZ_BLOCK);
            if(y==64&&Math.abs(dx)<=3&&Math.abs(dz)<=3)return b(Blocks.SMOOTH_QUARTZ);
            if(y==65&&dx==0&&Math.abs(dz)==3)return b(Blocks.SEA_LANTERN);
        }
        // Reading furniture on the ground floor, never cutting away the source building.
        if(!music&&x>=400&&x<=433&&z>=335&&z<=420&&original.isAir()){
            if((x-400)%11==0&&(z-335)%18<9&&(y==64||y==65))return b(Blocks.BOOKSHELF);
            if((x-400)%11>=4&&(x-400)%11<=7&&(z-335)%18<7&&y==64)return b(Blocks.OAK_SLAB);
        }
        return original;
    }
    private FutianPublicSpace(){}
}
