package com.codex.blacklight.firearms;

import com.codex.blacklight.combat.HydraContact;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OrdnanceBodyContactTest {
    @Test void coveredNearLimbDoesNotHideAnotherExposedLimb(){
        var near=HydraContact.oriented(new Vec3(2,0,0),new Vec3(.5,.5,.5),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        var far=HydraContact.oriented(new Vec3(4,0,2),new Vec3(.5,.5,.5),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        var regions=List.of(new HydraContact.Region("covered",List.of(near),near.bounds),new HydraContact.Region("exposed",List.of(far),far.bounds));
        var contact=OrdnanceBodyContact.nearestContact(regions,Vec3.ZERO,p->p.z>1&&p.lengthSqr()<=25);
        assertNotNull(contact);assertEquals("exposed",contact.bone());assertEquals(new Vec3(3.5,0,1.5),contact.point());
        assertNull(OrdnanceBodyContact.nearestContact(regions,Vec3.ZERO,p->p.z>1&&p.lengthSqr()<=9),"visible but out-of-radius limb cannot be hit");
        assertNull(OrdnanceBodyContact.nearestContact(regions,Vec3.ZERO,p->false),"complete cover protects all limbs");
    }
}
