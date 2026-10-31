package com.codex.blacklight.military;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryRoleTest {
    @Test void stableIdsAreIndependentOfRankAndAppointment(){
        assertEquals(MilitaryRole.TEAM_LEADER,MilitaryRole.parse("team_leader").orElseThrow());
        assertEquals(MilitaryRole.OFFICER,MilitaryRole.parse("officer").orElseThrow());
        assertEquals(MilitaryRole.COMMANDER,MilitaryRole.parse("commander").orElseThrow());
        assertTrue(MilitaryRole.parse("general").isEmpty());
    }
    @Test void commandRolesHaveDefaultsWithoutChangingExistingRanks(){
        assertEquals(MilitaryRank.PRIVATE,MilitaryRole.TROOPER.defaultRank());
        assertEquals(MilitaryRank.SERGEANT,MilitaryRole.TEAM_LEADER.defaultRank());
        assertEquals(MilitaryRank.LIEUTENANT,MilitaryRole.OFFICER.defaultRank());
        assertEquals(MilitaryRank.MAJOR,MilitaryRole.COMMANDER.defaultRank());
        assertEquals(8,MilitaryRank.values().length);
    }
}
