package com.codex.blacklight.combat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Immutable, bounded payload. NBT block names survive registry remapping on reload. */
public final class BlockThrowCargo {
 public record Cell(BlockPos offset,BlockState state){}
 private final List<Cell> cells;private final Vec3 size;
 public BlockThrowCargo(List<Cell> cells){
  this.cells=List.copyOf(cells);int x=1,y=1,z=1;
  for(var c:cells){x=Math.max(x,c.offset().getX()+1);y=Math.max(y,c.offset().getY()+1);z=Math.max(z,c.offset().getZ()+1);}size=new Vec3(x,y,z);
 }
 public List<Cell> cells(){return cells;}public Vec3 size(){return size;}public Vec3 half(){return size.scale(.45);}
 public boolean twoHanded(){return ThrowObjectSize.twoHanded(size.x,size.y,size.z);}
 public CompoundTag save(){var n=new CompoundTag();var list=new ListTag();for(var c:cells){var v=new CompoundTag();v.putLong("Pos",c.offset().asLong());v.put("State",NbtUtils.writeBlockState(c.state()));list.add(v);}n.put("Cells",list);return n;}
 public static BlockThrowCargo load(Level level,CompoundTag n,BlockState fallback){
  var cells=new ArrayList<Cell>();var list=n.getList("Cells",Tag.TAG_COMPOUND);var used=new HashSet<BlockPos>();
  for(int i=0;i<Math.min(27,list.size());i++){var v=list.getCompound(i);var pos=BlockPos.of(v.getLong("Pos"));if(pos.getX()<0||pos.getX()>4||pos.getY()<0||pos.getY()>4||pos.getZ()<0||pos.getZ()>4||!used.add(pos))continue;
   var state=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),v.getCompound("State"));if(!state.isAir()&&!state.hasBlockEntity())cells.add(new Cell(pos,state));}
  if(cells.isEmpty())cells.add(new Cell(BlockPos.ZERO,fallback));return new BlockThrowCargo(cells);
 }
}
