package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClawSpikeFieldTest {
    @Test void smallMasterUsesTheTwoShapesNamedByTheOriginalTod() {
        var master=ClawSpikeField.Master.SMALL;
        assertEquals(6,master.count);
        assertEquals(2,master.size(0));
        assertEquals(1,master.size(.99));
    }
    @Test void higherMasterCountsAndRiseMatchTheirFigOwners() {
        assertEquals(9,ClawSpikeField.Master.MEDIUM.count);
        assertEquals(15,ClawSpikeField.Master.LARGE.count);
        assertEquals(.1,ClawSpikeField.Master.LARGE.riseMin);
        assertEquals(3,ClawSpikeField.Master.LARGE.size(0));
        assertEquals(2,ClawSpikeField.Master.LARGE.size(.5));
        assertEquals(1,ClawSpikeField.Master.LARGE.size(.99));
    }
}
