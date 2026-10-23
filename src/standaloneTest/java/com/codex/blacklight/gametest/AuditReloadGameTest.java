package com.codex.blacklight.gametest;
import com.codex.blacklight.entity.ThrownBlock;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AuditReloadGameTest {
 @GameTest(template="wall_test",timeoutTicks=40,batch="audit_reload_settled")
 public static void reloadedRecoverySynchronizesSettledFlag(GameTestHelper h) throws Exception {
  var original = ModEntities.THROWN_BLOCK.get().create(h.getLevel());
  CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
  saved.putBoolean("Recover", true); saved.putBoolean("Armed", true);
  var loaded = ModEntities.THROWN_BLOCK.get().create(h.getLevel()); loaded.load(saved);
  var local = ThrownBlock.class.getDeclaredField("settled"); local.setAccessible(true);
  var key = ThrownBlock.class.getDeclaredField("SETTLED"); key.setAccessible(true);
  boolean server = local.getBoolean(loaded);
  boolean synchronizedState = loaded.getEntityData().get((EntityDataAccessor<Boolean>)key.get(null));
  original.discard(); loaded.discard();
  h.assertTrue(server && synchronizedState,"reloaded recovery must synchronize settled=true; server="+server+" synced="+synchronizedState);
  h.succeed();
 }
}
