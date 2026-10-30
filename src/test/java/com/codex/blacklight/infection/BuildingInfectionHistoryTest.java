package com.codex.blacklight.infection;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingInfectionHistoryTest {
    @Test void DeadFrontiersDoNotOccupyTheLiveBudgetOrSavedQueues(){
        var data=new BuildingInfectionData();
        for(int i=0;i<80;i++){
            var c=new BuildingInfectionData.Colony(new BlockPos(i*200,60,0));
            c.dead=true;c.offer(c.origin.above());data.colonies.add(c);
        }
        var live=new BuildingInfectionData.Colony(new BlockPos(-200,60,0));data.colonies.add(live);
        assertEquals(1,data.activeColonies());
        var saved=data.save(new CompoundTag());
        assertEquals(1,saved.getList("colonies",Tag.TAG_COMPOUND).size());
        assertEquals(80,saved.getLongArray("clearedSites").length);
        var copy=BuildingInfectionData.load(saved);
        assertEquals(1,copy.activeColonies());assertEquals(live.origin,copy.colonies.get(0).origin);
        assertEquals(data.clearedSites,copy.clearedSites);
    }
    @Test void LegacyDeadColonyMigratesToCompactHistory(){
        var root=new CompoundTag();var rows=new ListTag();var dead=new CompoundTag();
        dead.putLong("origin",new BlockPos(7,50,9).asLong());dead.putBoolean("dead",true);rows.add(dead);root.put("colonies",rows);
        var copy=BuildingInfectionData.load(root);
        assertEquals(0,copy.activeColonies());assertTrue(copy.clearedSites.contains(new BlockPos(7,50,9).asLong()));
    }
}
