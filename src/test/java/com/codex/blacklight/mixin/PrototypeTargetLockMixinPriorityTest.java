package com.codex.blacklight.mixin;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Epic Fight 21.17.3.1 wraps {@code MouseHandler.turnPlayer}'s {@code LocalPlayer.turn} call at the default
 * priority and never calls {@code original} (it calls {@code player.turn} directly). Mixin applies higher
 * priorities later, which makes them the outer wrapper. If Blacklight's wrapper drops back to the default, Epic Fight
 * ends up outside, swallows every mouse delta, and the prototype's head turns while its body stays put.
 * CI has no Epic Fight, so pin the annotations that decide the order.
 */
class PrototypeTargetLockMixinPriorityTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final int EPIC_FIGHT_PRIORITY = 1000;

    @Test void mouseWrapperOutranksEpicFightsWrapperOnTheSameTurnCall() throws Exception {
        ClassNode mixin = new ClassNode();
        try (var input = getClass().getResourceAsStream("/com/codex/blacklight/mixin/client/PrototypeTargetLockMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(mixin, ClassReader.SKIP_CODE);
        }
        AnnotationNode target = annotation(mixin.invisibleAnnotations, MIXIN);
        if (target == null) target = annotation(mixin.visibleAnnotations, MIXIN);
        assertNotNull(target, "@Mixin");
        Object priority = value(target, "priority");
        assertNotNull(priority, "PrototypeTargetLockMixin must set an explicit priority above Epic Fight's default");
        assertTrue((Integer) priority > EPIC_FIGHT_PRIORITY, "priority " + priority + " lets Epic Fight wrap outside");

        int wrappers = 0;
        for (MethodNode handler : mixin.methods) {
            AnnotationNode wrap = annotation(handler.invisibleAnnotations, WRAP);
            if (wrap == null) wrap = annotation(handler.visibleAnnotations, WRAP);
            if (wrap == null) continue;
            wrappers++;
            assertEquals(List.of("turnPlayer"), value(wrap, "method"));
            List<?> at = (List<?>) value(wrap, "at");
            assertEquals(1, at.size());
            assertEquals("Lnet/minecraft/client/player/LocalPlayer;turn(DD)V", value((AnnotationNode) at.get(0), "target"));
        }
        assertEquals(1, wrappers);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        return null;
    }
}
