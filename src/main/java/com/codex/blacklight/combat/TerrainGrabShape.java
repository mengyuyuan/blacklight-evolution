package com.codex.blacklight.combat;
import net.minecraft.core.*;import java.util.*;import java.util.function.Predicate;
/** A fixed random order makes every larger selection a connected superset of the previous one. */
public final class TerrainGrabShape {
 private TerrainGrabShape(){}
 public static int count(int ticks,int tier){return Math.min(ThrowObjectSize.terrainLimit(tier),1+Math.max(0,ticks-6)/2);}
 public static List<BlockPos> grow(BlockPos center,int limit,long seed,Predicate<BlockPos> allowed){
  var result=new ArrayList<BlockPos>();var frontier=new ArrayList<BlockPos>();var queued=new HashSet<BlockPos>();var random=new Random(seed);frontier.add(center);queued.add(center);
  while(!frontier.isEmpty()&&result.size()<Math.min(27,limit)){
   var pos=frontier.remove(random.nextInt(frontier.size()));if(!allowed.test(pos))continue;result.add(pos.immutable());
   for(var side:Direction.values()){var next=pos.relative(side);if(Math.abs(next.getX()-center.getX())<=2&&Math.abs(next.getY()-center.getY())<=2&&Math.abs(next.getZ()-center.getZ())<=2&&queued.add(next))frontier.add(next);}
  }
  return List.copyOf(result);
 }
}
