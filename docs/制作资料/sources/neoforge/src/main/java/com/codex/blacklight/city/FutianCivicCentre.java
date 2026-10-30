package com.codex.blacklight.city;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Block-scale landmark adaptation: thin wing roof, yellow cylinder and red square tower.
 * The 486 m span follows the published dimensions; interiors are playable approximations.
 */
public final class FutianCivicCentre {
    public static boolean inside(int x,int z){return x>=415&&x<=921&&z>=543&&z<=722;}
    private static int roof(int x,int z){
        double u=(x-668)/243.0,v=(z-625)/77.0;
        // Broad raised centre, descending wings and subtly lifted tips; no extruded roof walls.
        return 63+(int)Math.round(44+38*Math.exp(-Math.pow(u/.46,4))+7*Math.pow(Math.abs(u),6)-3*v*v);
    }
    private static boolean roofFootprint(int x,int z){
        double u=Math.abs((x-668)/243.0);
        return u<=1&&Math.abs(z-625)<=60+17*u;
    }
    public static int top(int x,int z){return inside(x,z)?roof(x,z)+2:63;}
    private static BlockState b(net.minecraft.world.level.block.Block block){return block.defaultBlockState();}
    record Geometry(boolean inside,boolean roof,int cover,double cylinder){}
    static Geometry geometry(int x,int z){
        return inside(x,z)?new Geometry(true,roofFootprint(x,z),roof(x,z),Math.hypot(x-614,z-625)):new Geometry(false,false,63,0);
    }
    public static BlockState state(int x,int y,int z,BlockState original){
        return state(x,y,z,original,geometry(x,z));
    }
    static BlockState state(int x,int y,int z,BlockState original,Geometry geometry){
        if(!geometry.inside||y<63)return original;
        if(y==63)return b(Blocks.SMOOTH_STONE);
        boolean roof=geometry.roof;int cover=geometry.cover;
        if(roof&&y>=cover-2&&y<=cover){
            boolean rib=(x-425)%12==0||(z-548)%16==0;
            return b(y==cover?(rib?Blocks.LIGHT_GRAY_CONCRETE:Blocks.LIGHT_BLUE_CONCRETE):Blocks.LIGHT_GRAY_CONCRETE);
        }
        // Structural underside grid is airy and separated from the occupied wings.
        if(roof&&y==cover-3&&((x-425)%12==0||(z-548)%16==0))return b(Blocks.IRON_BARS);
        double cylinder=geometry.cylinder;
        boolean yellow=cylinder<=25,red=x>=706&&x<=752&&z>=600&&z<=650;
        if((yellow||red)&&y>=69&&y<cover-3){
            boolean wall=yellow?cylinder>=23:x<=707||x>=751||z<=601||z>=649;
            boolean doorway=y<73&&z>=647&&z<=652&&(yellow?Math.abs(x-614)<3:Math.abs(x-729)<3);
            if(doorway)return b(Blocks.AIR);
            // Deep roof spans exceed vanilla skylight propagation. Recessed facade
            // lamps keep the two identifying colours readable without night vision.
            if(wall)return b(y>=72&&(y-72)%8==0&&(x%8==0||z%8==0)?Blocks.OCHRE_FROGLIGHT:yellow?Blocks.YELLOW_CONCRETE:Blocks.RED_CONCRETE);
            if((y-69)%5==0)return b(Blocks.SMOOTH_STONE);
            return b(Blocks.AIR);
        }
        // Raised public deck, with a clear north-south pedestrian axis.
        if(z<=702&&x>=425&&x<=911&&y<=68){
            if(y==68)return b((x+z)%24==0?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_QUARTZ);
            return b(Blocks.STONE);
        }
        if(z>702&&z<=720&&x>=575&&x<=760){
            int stair=63+Math.max(0,(721-z)/3);
            if(y<=Math.min(68,stair))return b(Blocks.SMOOTH_QUARTZ);
        }
        boolean west=x>=425&&x<=575,east=x>=766&&x<=911;
        if((west||east)&&z>=552&&z<=692&&y>=69&&y<=88){
            int left=west?425:766,right=west?575:911;
            boolean wall=x==left||x==right||z==552||z==692;
            if(wall&&z==692&&y<=72&&Math.abs(x-(left+right)/2)<=3)return b(Blocks.AIR);
            if(x==left+5&&z==557&&y>69)return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.SOUTH);
            if(x==left+5&&z==556)return b(Blocks.SMOOTH_QUARTZ);
            if(y==88)return b(Blocks.SMOOTH_STONE);
            if((y-69)%5==0)return b(Blocks.SMOOTH_QUARTZ);
            if(wall){int along=x==left||x==right?z:x;return b(along%6==0?Blocks.WHITE_CONCRETE:Blocks.LIGHT_BLUE_STAINED_GLASS);}
            if(y%5==3&&x%18==0&&z%18==0)return b(Blocks.SEA_LANTERN);
            return b(Blocks.AIR);
        }
        // Slender supports carry the wing roof, never a solid wall across the plaza.
        if((west||east)&&z>=560&&z<=690&&y>88&&y<cover-3){
            int supportX=(x-438)%50,supportZ=(z-570)%50;
            if(supportX>=0&&supportX<=1&&supportZ>=0&&supportZ<=1)return b(Blocks.LIGHT_GRAY_CONCRETE);
        }
        return b(Blocks.AIR);
    }
    private FutianCivicCentre(){}
}
