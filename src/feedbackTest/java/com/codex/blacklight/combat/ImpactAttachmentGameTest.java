package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class ImpactAttachmentGameTest {
    @GameTest(template = "wall_test")
    public static void heavyFeedbackOnlyReadsExistingVictimState(GameTestHelper test) {
        var boss = test.spawn(ModEntities.SUPREME_HUNTER.get(), 2, 2, 2);
        test.assertTrue(!ModAttachments.hasData(boss, ModAttachments.HIT_REACTION), "Fresh victim");
        WeaponImpact.contact(null, boss, ImpactTier.FINISHER, false, null);
        test.assertTrue(!ModAttachments.hasData(boss, ModAttachments.HIT_REACTION),
                "Feedback must not create or publish a default hit reaction");
        boss.discard();
        test.assertTrue(!ModAttachments.available(boss), "Removed victim has no live storage");
        WeaponImpact.contact(null, boss, ImpactTier.FINISHER, false, null);
        test.succeed();
    }
}
