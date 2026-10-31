package com.codex.blacklight.client;

import com.codex.blacklight.client.renderer.LivingRenderScope;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** Native renderer/event-bus regression; only enabled in a disposable QA client. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class RenderScopeSmoke {
    private enum Mode { NORMAL, CANCEL, NESTED, THROW }
    private static LivingEntity target;
    private static Mode mode;
    private static int pre,post,ticks;
    private static boolean nested,done;
    private static final JsonArray cases=new JsonArray();
    private static final IllegalStateException FAILURE=new IllegalStateException("EXPECTED audit listener exception");
    private static boolean enabled(){return Boolean.getBoolean("blacklight.renderAudit");}

    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled()||done)return;
        var mc=Minecraft.getInstance();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.mouseHandler.releaseMouse();mc.options.pauseOnLostFocus=false;
        if(mc.player!=null&&mc.level!=null){mc.setScreen(null);ticks++;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void cancelAfterOurPre(RenderLivingEvent.Pre<?,?> event) {
        if(!enabled()||event.getEntity()!=target||mode==null)return;
        pre++;
        if(!LivingRenderScope.active(event.getPoseStack()))throw new AssertionError("Native renderer wrapper missing");
        if(event.getPoseStack().clear())throw new AssertionError("Expected effect frame before foreign listener");
        if(mode==Mode.THROW)throw FAILURE;
        if(mode==Mode.NESTED&&!nested) {
            nested=true;var parent=event.getPoseStack().last();
            render(target,event.getPoseStack());
            if(event.getPoseStack().last()!=parent)throw new AssertionError("Nested render consumed parent frame");
        }
        if(mode!=Mode.NORMAL)event.setCanceled(true);
    }
    @SubscribeEvent public static void after(RenderLivingEvent.Post<?,?> event) {
        if(enabled()&&event.getEntity()==target)post++;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void render(LivingEntity entity,PoseStack stack) {
        var mc=Minecraft.getInstance();
        ((LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(entity)).render(entity,0,.5F,stack,mc.renderBuffers().bufferSource(),15728880);
    }
    private static void check(String name,Mode requested,int stage) {
        var mc=Minecraft.getInstance();target=net.minecraft.world.entity.EntityType.PIG.create(mc.level);
        target.setPos(mc.player.position());long now=mc.level.getGameTime();
        if(stage==0)com.codex.blacklight.registry.ModAttachments.setData(target, ModAttachments.HIT_REACTION,new HitReaction(now,20,HitReaction.STAGGER,0));
        else com.codex.blacklight.registry.ModAttachments.setData(target, ModAttachments.CONSUME_VISUAL,new ConsumeVisual(mc.player.getId(),now-(stage==ConsumeVisual.CONSUME?28:0),46,PowerForm.NONE.ordinal(),stage));
        mode=requested;pre=0;post=0;nested=false;
        var stack=new PoseStack();stack.translate(2,3,4);var baseline=stack.last();boolean threw=false;
        try{render(target,stack);}catch(IllegalStateException ex){if(ex!=FAILURE)throw ex;threw=true;}
        if(threw!=(requested==Mode.THROW))throw new AssertionError(name+": exception propagation mismatch");
        if(pre==0||requested==Mode.NESTED&&pre<2)throw new AssertionError(name+": cancellation listener not reached");
        if(requested==Mode.NORMAL?post!=1:post!=0)throw new AssertionError(name+": unexpected Post count "+post);
        if(stack.last()!=baseline||!stack.clear()||LivingRenderScope.active(stack))throw new AssertionError(name+": stack or scope leaked");
        var result=new JsonObject();result.addProperty("case",name);result.addProperty("pre",pre);result.addProperty("post",post);result.addProperty("balanced",true);cases.add(result);
        target.discard();target=null;mode=null;
    }
    private static void checkReloadReplica(boolean recovering) {
        var mc=Minecraft.getInstance();
        var source=com.codex.blacklight.registry.ModEntities.THROWN_BLOCK.get().create(mc.level);
        var restored=com.codex.blacklight.registry.ModEntities.THROWN_BLOCK.get().create(mc.level);
        var replica=com.codex.blacklight.registry.ModEntities.THROWN_BLOCK.get().create(mc.level);
        try {
            source.setPos(mc.player.position().add(0,20,0));
            source.setDeltaMovement(.3,-.6,.1);
            var saved=new net.minecraft.nbt.CompoundTag();source.saveWithoutId(saved);
            saved.putBoolean("Recover",recovering);restored.load(saved);
            if(recovering&&restored.getDeltaMovement().lengthSqr()!=0)throw new AssertionError("Recovery retained saved velocity");
            // Replicate synchronized fields only: the client never receives private settled/NBT.
            var values=restored.getEntityData().getNonDefaultValues();
            if(values!=null)replica.getEntityData().assignValues(values);
            replica.setPos(source.position());replica.setDeltaMovement(.3,-.6,.1);
            var start=replica.position();replica.tick();replica.tick();
            if(replica.position().equals(start)!=recovering)throw new AssertionError("Reload replica ballistic state mismatch");
            var result=new JsonObject();result.addProperty("case",recovering?"reload-recovery-client":"reload-airborne-client");result.addProperty("passed",true);cases.add(result);
        } finally {source.discard();restored.discard();replica.discard();}
    }
    @SubscribeEvent public static void frame(RenderLevelStageEvent event) {
        if(!enabled()||done||ticks<30||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        Throwable failure=null;
        try {
            check("hit-normal",Mode.NORMAL,0);
            check("hit-cancel",Mode.CANCEL,0);
            check("hold-cancel",Mode.CANCEL,ConsumeVisual.HOLD);
            check("capture-cancel",Mode.CANCEL,ConsumeVisual.CAPTURE);
            check("nested-cancel",Mode.NESTED,0);
            check("listener-exception",Mode.THROW,0);
            check("fracture-cancel",Mode.CANCEL,ConsumeVisual.CONSUME);
            checkReloadReplica(true);
            checkReloadReplica(false);
        } catch(Throwable t){failure=t;}
        finally {done=true;mode=null;if(target!=null)target.discard();target=null;}
        var report=new JsonObject();report.addProperty("passed",failure==null);report.add("cases",cases);
        if(failure!=null)report.addProperty("error",failure.toString());
        try {
            Path output=Minecraft.getInstance().gameDirectory.toPath().resolve("render-audit.json");
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){throw new RuntimeException(e);}
        Minecraft.getInstance().stop();
    }
}
