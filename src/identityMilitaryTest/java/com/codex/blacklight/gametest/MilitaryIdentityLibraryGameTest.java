package com.codex.blacklight.gametest;

import com.codex.blacklight.disguise.DisguiseIdentity;
import com.codex.blacklight.disguise.DisguiseLibrary;
import com.codex.blacklight.disguise.DisguiseMorph;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.AmmoState;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.military.MilitaryOrder;
import com.codex.blacklight.military.MilitaryOrganizationSystem;
import com.codex.blacklight.military.MilitaryRank;
import com.codex.blacklight.military.MilitaryRole;
import com.codex.blacklight.military.MilitaryUnitType;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual capture/library/select integration. This suite does not claim to run consumption. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryIdentityLibraryGameTest {
    private static final String[] PRIVATE_FIELDS = {"UUID", "Pos", "Motion", "Inventory", "Passengers",
            "MilitaryUnit", "MilitaryAutomaticJoin", "MilitaryAmmoReserve", "MilitaryWeaponInitialized",
            "MilitaryStowedWeapon", "MilitaryDeployment", "BlackwatchHome", "BlackwatchHomeDimension",
            "ForgeData", "NeoForgeData", "BlacklightMilitaryBase", "BlacklightMilitaryPost"};

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(x, 1, z, Blocks.STONE);
            for (int y = 2; y < 8; y++) h.setBlock(x, y, z,
                    x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.BEDROCK : Blocks.AIR);
        }
        // The real morph must finish unwitnessed. Block adjacent tests' possible sight lines too.
        for (int x = 9; x <= 12; x++) for (int z = 9; z <= 12; z++)
            for (int y = 2; y <= 6; y++)
                if (x == 9 || x == 12 || z == 9 || z == 12 || y == 6) h.setBlock(x, y, z, Blocks.BEDROCK);
    }

    private static MilitarySoldier member(GameTestHelper h, boolean army, MilitaryRole role, int x) {
        var type = army ? (role == MilitaryRole.OFFICER ? ModEntities.MILITARY_OFFICER.get() : ModEntities.MILITARY_TEAM_LEADER.get())
                : (role == MilitaryRole.OFFICER ? ModEntities.BLACKWATCH_OFFICER.get() : ModEntities.BLACKWATCH_TEAM_LEADER.get());
        var soldier = type.create(h.getLevel());
        h.assertTrue(soldier != null, "registered military identity entity exists");
        soldier.setNoAi(true); soldier.setPersistenceRequired(); soldier.setAutomaticUnitJoin(false);
        soldier.setPos(h.absoluteVec(new Vec3(x + .5, 2, 4.5)));
        h.assertTrue(h.getLevel().addFreshEntity(soldier), "real military identity source enters the level");
        return soldier;
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "identity-m5"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(h.absoluteVec(new Vec3(10.5, 2, 10.5)));
        h.getLevel().addNewPlayer(player);
        var state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE); state.infect(); state.setPrototypeMode(true);
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state);
        player.getInventory().clearContent(); player.getInventory().setItem(7, new ItemStack(Items.IRON_INGOT, 7));
        return player;
    }

    private static void appearance(GameTestHelper h, DisguiseIdentity identity, MilitarySoldier source,
                                   MilitaryRole role, MilitaryRank rank, boolean equipped) {
        h.assertTrue(identity.valid() && identity.entityType() == source.getType()
                        && identity.family().equals(source.faction().id()), "captured military type and family survive selection");
        var tag = identity.appearance();
        h.assertTrue(tag.getString("MilitaryRole").equals(role.id()) && tag.getString("MilitaryRank").equals(rank.id())
                        && tag.getBoolean("MilitaryEquipped") == equipped, "role, actual rank and outfit survive the bounded library");
        for (String key : PRIVATE_FIELDS) h.assertTrue(!tag.contains(key), "appearance excludes source ownership/inventory field " + key);
        // HandItems/ArmorItems are the author's permitted bounded cosmetics, never a player inventory grant.
        var avatar = (MilitarySoldier) source.getType().create(h.getLevel());
        h.assertTrue(avatar != null, "real registered render-surrogate type exists");
        avatar.load(tag.copy());
        h.assertTrue(avatar.commandRole() == role && avatar.rank() == rank && avatar.equipped() == equipped,
                "the same real NBT loading used by DisguiseRenderer restores military appearance");
        h.assertTrue(!avatar.getUUID().equals(source.getUUID()) && avatar.unitId() == null && avatar.reserveAmmo() == 0,
                "appearance does not clone entity identity, organization or finite reserve");
        h.assertTrue(!avatar.getPersistentData().contains("BlacklightMilitaryBase"), "render surrogate inherits no base origin");
    }

    private static void unchanged(GameTestHelper h, ServerPlayer player, MilitarySoldier source, UUID sourceId, UUID unit,
                                  MilitaryRole role, MilitaryRank rank, boolean equipped, int magazine, int reserve,
                                  CompoundTag weaponBefore) {
        h.assertTrue(source.isAlive(), "capture source remains alive; actualHealth=" + source.getHealth());
        h.assertTrue(sourceId.equals(source.getUUID()), "capture never changes source UUID; expected=" + sourceId + " actual=" + source.getUUID());
        var roster = MilitaryOrganizationSystem.organization(h.getLevel());
        h.assertTrue(unit.equals(source.unitId()), "capture never changes registered source unit; expected=" + unit
                + " actual=" + source.unitId() + " rosterOwner=" + roster.leafOf(sourceId));
        h.assertTrue(roster.leafOf(sourceId).filter(unit::equals).isPresent()
                        && roster.unit(unit).orElseThrow().members().get(sourceId) == rank
                        && sourceId.equals(roster.unit(unit).orElseThrow().commander()),
                "source retains its actual registered roster rank and command appointment");
        h.assertTrue(source.commandRole() == role, "source role unchanged; expected=" + role + " actual=" + source.commandRole());
        h.assertTrue(source.rank() == rank, "source rank unchanged; expected=" + rank + " actual=" + source.rank());
        h.assertTrue(source.equipped() == equipped, "source outfit unchanged; expected=" + equipped + " actual=" + source.equipped());
        h.assertTrue(source.magazineAmmo() == magazine && source.reserveAmmo() == reserve,
                "source's real magazine and reserve remain finite and unchanged");
        var current = source.saveWithoutId(new CompoundTag());
        h.assertTrue(java.util.Objects.equals(weaponBefore.get("HandItems"), current.get("HandItems"))
                        && weaponBefore.getCompound("MilitaryStowedWeapon").equals(current.getCompound("MilitaryStowedWeapon")),
                "source's carried or stowed real weapon stack is not copied away or changed");
        h.assertTrue(source.getPersistentData().getString("BlacklightMilitaryBase").equals("identity-m5-source"),
                "source's original base ledger marker remains its own");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            h.assertTrue(slot == 7 ? stack.is(Items.IRON_INGOT) && stack.getCount() == 7 : stack.isEmpty(),
                    "identity operations grant no playable weapon, ammunition or equipment into slot " + slot);
        }
    }

    private static void captureRoundTrip(GameTestHelper h, boolean army, MilitaryRole role, boolean equipped) {
        arena(h);
        var source = member(h, army, role, 4);
        var otherFaction = member(h, !army, MilitaryRole.TEAM_LEADER, 6);
        var rank = role == MilitaryRole.OFFICER ? MilitaryRank.COLONEL : MilitaryRank.SERGEANT;
        UUID unit = UUID.randomUUID(), sourceId = source.getUUID(); source.setRank(rank);
        source.initializeWeapon();
        var gun = (FirearmItem) source.getMainHandItem().getItem();
        FirearmItem.store(source.getMainHandItem(), new AmmoState(5, 0, 0));
        var finite = source.saveWithoutId(new CompoundTag()); finite.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(gun.definition().ammoId(), 11);
        source.load(finite); source.setEquipped(equipped);
        source.getPersistentData().putString("BlacklightMilitaryBase", "identity-m5-source");
        source.getPersistentData().putLong("BlacklightMilitaryPost", source.blockPosition().asLong());
        var origin = source.position();
        h.assertTrue(MilitaryOrganizationSystem.transaction(h.getLevel(), org ->
                        org.create(unit, source.faction(), MilitaryUnitType.TACTICAL_SQUAD, "identity-m5-" + unit, 1,
                                new MilitaryOrder.Point(origin.x, origin.y, origin.z))
                                && org.assign(unit, sourceId, rank) && org.appoint(unit, sourceId)),
                "fixture creates and assigns a real registered source unit, not an invalid UUID hint");
        MilitaryOrganizationSystem.manual(h.getLevel(), unit);
        MilitaryOrganizationSystem.ensureMember(source);
        h.assertTrue(unit.equals(source.unitId())
                        && MilitaryOrganizationSystem.organization(h.getLevel()).leafOf(sourceId).filter(unit::equals).isPresent(),
                "real normal membership validation accepts this legitimate source assignment before capture");
        int magazine = source.magazineAmmo(), reserve = source.reserveAmmo();
        h.assertTrue(gun.definition().capacity() > magazine && reserve == 11, "fixture uses a partly spent real finite gun");
        var weaponBefore = source.saveWithoutId(new CompoundTag());
        var player = player(h);

        DisguiseSystem.captured(player, DisguiseIdentity.capture(source));
        var library = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DISGUISE_LIBRARY);
        h.assertTrue(library.identities().size() == 1, "real capture callback adds the source to the server-owned library");
        appearance(h, library.identities().get(0), source, role, rank, equipped);
        var stored = DisguiseLibrary.CODEC.encodeStart(NbtOps.INSTANCE, library).result().orElseThrow();
        var restored = DisguiseLibrary.CODEC.parse(NbtOps.INSTANCE, stored).result().orElseThrow();
        h.assertTrue(library.equals(restored), "actual military identity collection survives its storage codec");
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.DISGUISE_LIBRARY, restored);

        var cow = EntityType.COW.create(h.getLevel());
        h.assertTrue(cow != null, "second real vanilla identity exists");
        DisguiseSystem.captured(player, DisguiseIdentity.capture(cow));
        var selections = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DISGUISE_LIBRARY);
        h.assertTrue(selections.identities().size() == 2, "the second capture preserves the older military entry");
        int officerIndex = selections.identities().indexOf(restored.identities().get(0));
        h.assertTrue(officerIndex == 1, "military identity can be selected by its actual revision and index");
        DisguiseSystem.select(player, selections.revision(), 0);
        h.assertTrue(DisguiseSystem.morphing(player), "selecting the other stored identity enters the actual morph path");
        // FakePlayer has no network connection tick. Drive the production tick subscriber with real server time.
        h.onEachTick(() -> { player.tickCount++; DisguiseSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player)); });

        h.runAtTickTime(DisguiseMorph.TICKS + 4, () -> {
            h.assertTrue(DisguiseSystem.active(player) && !DisguiseSystem.morphing(player)
                            && com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DISGUISE_IDENTITY).type().equals("minecraft:cow"),
                    "the different library entry actually became the active finished disguise");
            DisguiseSystem.select(player, selections.revision(), officerIndex);
            h.assertTrue(DisguiseSystem.morphing(player), "re-selecting the older military identity starts a new real morph");
        });
        h.runAtTickTime(DisguiseMorph.TICKS * 2 + 9, () -> {
            h.assertTrue(DisguiseSystem.active(player) && !DisguiseSystem.morphing(player),
                    "military re-selection completed on the actual server clock");
            appearance(h, com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DISGUISE_IDENTITY), source, role, rank, equipped);
            h.assertTrue(DisguiseSystem.fooled(source, player) && DisguiseSystem.fooled(otherFaction, player),
                    "both currently allied military factions accept this legitimate unwitnessed disguise");
            boolean clean;
            try {
                unchanged(h, player, source, sourceId, unit, role, rank, equipped, magazine, reserve, weaponBefore);
            } finally {
                player.discard(); source.discard(); otherFaction.discard();
                clean = MilitaryOrganizationSystem.transaction(h.getLevel(), org -> {
                    org.removeMember(sourceId);
                    return org.unit(unit).isEmpty() || org.removeUnit(unit);
                });
            }
            h.assertTrue(clean && MilitaryOrganizationSystem.organization(h.getLevel()).unit(unit).isEmpty(),
                    "fixture removes its own roster and empty manual unit after identity checks");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 70)
    public static void armyOfficerCaptureLibraryStorageAndReselectionPreserveEquippedAppearance(GameTestHelper h) {
        captureRoundTrip(h, true, MilitaryRole.OFFICER, true);
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 70)
    public static void armyLeaderCaptureLibraryStorageAndReselectionPreserveUniformAppearance(GameTestHelper h) {
        captureRoundTrip(h, true, MilitaryRole.TEAM_LEADER, false);
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 70)
    public static void blackwatchOfficerCaptureLibraryStorageAndReselectionPreserveUniformAppearance(GameTestHelper h) {
        captureRoundTrip(h, false, MilitaryRole.OFFICER, false);
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 70)
    public static void blackwatchLeaderCaptureLibraryStorageAndReselectionPreserveEquippedAppearance(GameTestHelper h) {
        captureRoundTrip(h, false, MilitaryRole.TEAM_LEADER, true);
    }
}
