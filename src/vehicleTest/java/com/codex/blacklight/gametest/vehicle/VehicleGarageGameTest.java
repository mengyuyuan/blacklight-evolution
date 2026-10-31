package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleGarageGameTest {
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=40)
    public static void fieldFoundationDepthSurvivesSaveWhileLegacyPiecesKeepOriginalBounds(GameTestHelper h) {
        var context=net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(int turn=0;turn<4;turn++) {
            var base=new CityMilitaryPlan.Site(0,0,80,turn,false);
            var oldBase=new CityMilitaryPiece(base);var oldBaseTag=oldBase.createTag(context);
            var fieldBase=new CityMilitaryPiece(base,null,CityMilitaryPlan.FIELD_FOUNDATION_DEPTH);
            h.assertTrue(!oldBaseTag.contains("FoundationDepth") && new CityMilitaryPiece(oldBaseTag).getBoundingBox().minY()==77,"legacy base bounds remain at three blocks");
            h.assertTrue(new CityMilitaryPiece(fieldBase.createTag(context)).getBoundingBox().minY()==74,"field base persisted support extends through six blocks");
            var site=new VehicleGarageData.Site(100,80,100,turn);
            var legacy=new VehicleGaragePiece("legacy",com.codex.blacklight.military.MilitaryFaction.MILITARY,site);
            var field=new VehicleGaragePiece("field",com.codex.blacklight.military.MilitaryFaction.MILITARY,site,true);
            h.assertTrue(new VehicleGaragePiece(legacy.createTag(context)).getBoundingBox().minY()==77,"legacy garage does not gain a new foundation on load");
            h.assertTrue(new VehicleGaragePiece(field.createTag(context)).getBoundingBox().minY()==74,"field garage retains its supported depth after reload");
        }
        h.succeed();
    }

    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void oldSavedBasePieceRegistersWithoutPlanningOrPlacingAnyNewGarage(GameTestHelper h) {
        try(var f=new Fixture(h,235000)) {
            var oldSite=new CityMilitaryPlan.Site(f.x-500,0,80,0,false);
            String id=VehicleGaragePiece.Planner.baseId(oldSite);int garages=VehicleGarageService.data(f.level).garages().size();
            var before=f.level.getChunkSource().getChunkNow(oldSite.x()>>4,oldSite.z()>>4);
            CityMilitaryService.discoverPieces(f.level,List.of(new CityMilitaryPiece(oldSite)));
            h.assertTrue(MilitaryBaseAccess.find(f.level,id).isPresent(),"loaded legacy base metadata is still registered");
            h.assertTrue(VehicleGarageService.data(f.level).garages().size()==garages,"absence of a recorded garage piece cannot invent old-world attachments");
            h.assertTrue(before==f.level.getChunkSource().getChunkNow(oldSite.x()>>4,oldSite.z()>>4),"legacy discovery never force-loads or builds unknown terrain");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void actualOperatorCommandsPreviewThenConfirmOneTokenAndRejectReplay(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try(var f=new Fixture(h,236000)) {
            var messages=new ArrayList<String>();
            var sender=new net.minecraft.commands.CommandSource(){
                public void sendSystemMessage(net.minecraft.network.chat.Component message){messages.add(message.getString());}
                public boolean acceptsSuccess(){return true;}public boolean acceptsFailure(){return true;}public boolean shouldInformAdmins(){return false;}
            };
            var source=new net.minecraft.commands.CommandSourceStack(sender,net.minecraft.world.phys.Vec3.atCenterOf(f.origin),net.minecraft.world.phys.Vec2.ZERO,f.level,2,"garage-qa",net.minecraft.network.chat.Component.literal("garage-qa"),f.level.getServer(),null);
            var dispatcher=f.level.getServer().getCommands().getDispatcher();int before=VehicleGarageService.data(f.level).garages().size();
            h.assertTrue(dispatcher.execute("blacklight military garage preview "+com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(f.base)+" "+f.x+" 80 0 0",source)==1,"registered production preview command succeeds with the real colon-separated base ID");
            h.assertTrue(VehicleGarageService.data(f.level).garages().size()==before,"preview command alone does not place or register a garage");
            UUID token=null;var expression=java.util.regex.Pattern.compile("garage attach ([0-9a-f-]{36})");
            for(String message:messages){var match=expression.matcher(message);if(match.find())token=UUID.fromString(match.group(1));}
            h.assertTrue(token!=null,"operator receives the actual concrete one-use confirmation token");
            String command="blacklight military garage attach "+token;
            h.assertTrue(dispatcher.execute(command,source)==1,"actual confirmation command places one garage");
            h.assertTrue(VehicleGarageService.data(f.level).garages().size()==before+1 && dispatcher.execute(command,source)==0,"replaying a consumed token never places a second structure or stock");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void oldBaseRequiresPreviewAndChangedFootprintNeverOverwritesPlayersBlock(GameTestHelper h) {
        try (var f = new Fixture(h, 231000)) {
            var preview = VehicleGarageService.preview(f.level, f.base, f.origin, 0);
            h.assertTrue(preview.allowed() && preview.token()!=null, "clear independently surveyed footprint can be previewed");
            var conflict=f.origin.offset(12,2,12); f.level.setBlock(conflict,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            h.assertTrue(VehicleGarageService.attach(f.level,f.base,f.origin,0,preview.token()).isEmpty(), "confirmation rechecks real footprint");
            h.assertTrue(f.level.getBlockState(conflict).is(Blocks.DIAMOND_BLOCK), "player block remains untouched");
            f.level.setBlock(conflict,Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(VehicleGarageService.attach(f.level,f.base,f.origin,0,UUID.randomUUID()).isEmpty(), "unissued confirmation token cannot place old-world structures");
            var id=f.attach(h); h.assertTrue(!id.isEmpty(), "new confirmed preview places and registers one garage");
            h.assertTrue(VehicleGarageService.data(f.level).garage(id).orElseThrow().slots().values().stream().allMatch(s->s.state()==VehicleGarageData.State.UNISSUED), "placing a garage does not manufacture soldiers or immediately issue vehicles");
        } h.succeed();
    }
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void actualBayAndContainerTransferOnlyMatchingFiniteItemsAndRepairCannotRevive(GameTestHelper h) {
        try (var f = new Fixture(h, 232000)) {
            var id=f.attach(h); var v=ModEntities.MILITARY_TANK.get().create(f.level); v.setPos(f.x+15.5,81,12.5); f.level.addFreshEntity(v);f.entities.add(v);
            v.hurt(f.level.damageSources().generic(),30);v.inventory().addFuel(137); var box=f.origin.offset(15,1,2); var inventory=(Container)f.level.getBlockEntity(box); inventory.clearContent();
            inventory.setItem(0,new ItemStack(VehicleRegistry.item("tank_he_shell"),7)); inventory.setItem(1,new ItemStack(VehicleRegistry.item("military_fuel_can")));
            inventory.setItem(2,new ItemStack(VehicleRegistry.item("vehicle_repair_parts"))); inventory.setItem(3,new ItemStack(VehicleRegistry.item("autocannon_round"),5));
            h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==8, "seven shells plus one real repair part are consumed");
            h.assertTrue(v.inventory().ammunition("tank_he_shell")==7 && v.fuelTicks()==137 && v.health()==v.maxHealth()-10, "real entity owns ammunition and repaired health while dormant legacy fuel stays exact");
            h.assertTrue(inventory.getItem(0).isEmpty() && inventory.getItem(1).getCount()==1 && inventory.getItem(2).isEmpty() && inventory.getItem(3).getCount()==5, "matching ammunition and repair debit exactly; legacy fuel and wrong ammunition are untouched");
            h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==0, "repeating transfer cannot refresh a depleted box");
            inventory.setItem(1,new ItemStack(VehicleRegistry.item("military_fuel_can"))); v.setPos(f.x+15.5,81,36);
            h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==0 && inventory.getItem(1).getCount()==1, "remote vehicle cannot receive repair-bay resources");
            v.setPos(f.x+15.5,81,12.5);f.level.setBlock(box,Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==0,"removed container cannot act as a virtual stockpile");
            v.hurt(f.level.damageSources().generic(),10000); h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==0 && v.health()==0,"destroyed mechanical entity cannot be repaired back to life");
        } h.succeed();
    }
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void missingRealCrewDoesNotCreatePeopleOrConsumeInitialVehicleSlots(GameTestHelper h) {
        try (var f=new Fixture(h,233000)) {
            var id=f.attach(h);VehicleGarageService.tick(f.level);
            h.assertTrue(f.level.getEntitiesOfClass(MilitaryVehicle.class,new AABB(f.x,80,0,f.x+31,90,27)).isEmpty(), "no existing idle driver means no native vehicle issuance");
            h.assertTrue(VehicleGarageService.data(f.level).garage(id).orElseThrow().slots().values().stream().allMatch(s->s.state()==VehicleGarageData.State.UNISSUED), "missing crew preserves all three initial slots");
            for(var core:LargeBaseBlueprint.get(false).commandCores()) f.level.setBlock(new BlockPos(f.x-100+core[0],80+core[1],core[2]),Blocks.AIR.defaultBlockState(),2);
            var v=ModEntities.MILITARY_TRUCK.get().create(f.level);v.setPos(f.x+5.5,81,12.5);f.level.addFreshEntity(v);f.entities.add(v);
            h.assertTrue(VehicleGarageService.transfer(f.level,v,id)==0,"missing actual command core stops supplies immediately");
        } h.succeed();
    }
    @GameTest(template="blackwatch_arena", batch="vehicle6garage", timeoutTicks=160)
    public static void generatedThreeBayRoofedGarageAllowsActualTankRotationAndFullChassisExit(GameTestHelper h) {
        try(var f=new Fixture(h,234000)) {
            f.attach(h);
            for(String chassis:List.of("truck","tank","apc")) {
                double x=chassis.equals("truck")?5.5:chassis.equals("tank")?15.5:25.5;
                var v=VehicleRegistry.type(com.codex.blacklight.military.MilitaryFaction.MILITARY,chassis).create(f.level);
                v.setPos(f.x+x,81,12.5);v.setYRot(0);v.inventory().addFuel(1000);f.level.addFreshEntity(v);f.entities.add(v);
                if(chassis.equals("tank")) {
                    for(int turn=0;turn<120;turn++) h.assertTrue(v.moveVehicle(0,3).reason()==VehicleKinematics.StopReason.NONE,"actual central tank rotates its full long hull and barrel inside the generated garage");
                    v.setYRot(0);
                }
                for(int step=0;step<130;step++)v.moveVehicle(.2,0);
                h.assertTrue(v.getBoundingBox().minZ>=27,"entire real "+chassis+" exits its actual seven-block doorway");
            }
        } h.succeed();
    }
    static final class Fixture implements AutoCloseable {
        final ServerLevel level; final int x; final BlockPos origin; final String base;
        final Map<Long,Boolean> forced=new LinkedHashMap<>(); final List<Entity> entities=new ArrayList<>();
        Fixture(GameTestHelper h,int requestedX) { this.level=h.getLevel();
            // GameTest worlds may retain SavedData across separate server invocations.
            // Never clear a previous garage ledger just to make a repeated test pass.
            int previous=VehicleGarageService.data(level).garages().stream().mapToInt(g->g.site().x()).max().orElse(requestedX-4096);
            int x=Math.max(requestedX,previous+4096);this.x=x;origin=new BlockPos(x,80,0);
            for(int cx=(x-112)>>4;cx<=(x+48)>>4;cx++)for(int cz=-2;cz<=5;cz++){long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=0;dx<31;dx++)for(int z=0;z<40;z++){level.setBlock(new BlockPos(x+dx,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<=89;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState(),2);}
            base=CityMilitaryService.register(level,new CityMilitaryPlan.Site(x-100,0,80,0,false));
            for(var core:LargeBaseBlueprint.get(false).commandCores()) level.setBlock(new BlockPos(x-100+core[0],80+core[1],core[2]),MilitaryFacilityBlocks.COMMAND_TERMINAL.get().defaultBlockState(),2);
        }
        String attach(GameTestHelper h){var p=VehicleGarageService.preview(level,base,origin,0);h.assertTrue(p.allowed(),"fixture preview: "+p.reason());var id=VehicleGarageService.attach(level,base,origin,0,p.token());h.assertTrue(!id.isEmpty(),"confirmed real garage is placed");return id;}
        public void close(){entities.forEach(Entity::discard);forced.forEach((key,value)->{var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
}
