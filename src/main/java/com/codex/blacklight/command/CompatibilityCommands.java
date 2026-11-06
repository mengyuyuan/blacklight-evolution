package com.codex.blacklight.command;

import com.codex.blacklight.combat.NativeHitObservation;
import com.codex.blacklight.compat.NativeCombatAccess;
import com.codex.blacklight.infection.NativeInfection;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;

/** On-demand receipts from the real damage path, never synthetic probe damage. */
public final class CompatibilityCommands {
    private CompatibilityCommands(){}
    public static LiteralArgumentBuilder<CommandSourceStack> commands(){
        return Commands.literal("compat").requires(s->s.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("target",EntityArgument.entity()).executes(c->{
                var entity=EntityArgument.getEntity(c,"target");
                if(!(entity instanceof LivingEntity living)){c.getSource().sendFailure(Component.literal("目标不是生物。"));return 0;}
                var pools=NativeCombatAccess.read(living,true);
                c.getSource().sendSuccess(()->Component.literal(String.format(Locale.ROOT,"%s | 生命 %.2f/%.2f，伤害吸收 %.2f，机制储备 %s",living.getType().toShortString(),pools.health(),living.getMaxHealth(),pools.absorption(),pools.reserves())),false);
                if(living instanceof Mob mob)c.getSource().sendSuccess(()->Component.literal("感染="+NativeInfection.active(mob)+"，保留原实体="+NativeInfection.preserve(mob)+"，AI="+mob.getNavigation().getClass().getSimpleName()+" / "+mob.getMoveControl().getClass().getSimpleName()+"，无敌标记="+mob.isInvulnerable()),false);
                var trace=NativeHitObservation.last(living);
                c.getSource().sendSuccess(()->Component.literal(trace.map(t->String.format(Locale.ROOT,"最近命中：%s，输入 %.2f，结果 %s，返回 %s，生命 %.2f→%.2f，吸收 %.2f→%.2f，储备 %s→%s，记录时间 %d",t.source(),t.requested(),t.result(),t.returned(),t.before().health(),t.after().health(),t.before().absorption(),t.after().absorption(),t.before().reserves(),t.after().reserves(),t.gameTime())).orElse("暂无命中记录。可用 /blacklight compat trace <目标> 30 开始观察，再正常攻击。")),false);
                return 1;
            })))
            .then(Commands.literal("trace").then(Commands.argument("target",EntityArgument.entity()).then(Commands.argument("seconds",IntegerArgumentType.integer(1,60)).executes(c->{
                var entity=EntityArgument.getEntity(c,"target");
                if(!(entity instanceof LivingEntity living)||!NativeHitObservation.watch(living,IntegerArgumentType.getInteger(c,"seconds"))){c.getSource().sendFailure(Component.literal("无法观察此目标，或已达到 32 个观察目标上限。"));return 0;}
                c.getSource().sendSuccess(()->Component.literal("已开始临时观察；正常攻击后用 compat inspect 查看。不会改变伤害、护盾、感染或 AI。"),false);return 1;
            }))))
            .then(Commands.literal("stop").then(Commands.argument("target",EntityArgument.entity()).executes(c->{
                var entity=EntityArgument.getEntity(c,"target");if(!(entity instanceof LivingEntity living))return 0;
                NativeHitObservation.forget(living);c.getSource().sendSuccess(()->Component.literal("已清除该目标的临时观察与命中记录。"),false);return 1;
            })));
    }
}
