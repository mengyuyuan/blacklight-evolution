package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LivingRenderScopeTest {
    @Test void cancelledPreDoesNotNeedPostToRestoreCaller() {
        var stack = new PoseStack();
        stack.translate(3, 2, 1);
        var entry = stack.last();
        try (var scope = LivingRenderScope.begin(stack)) {
            LivingRenderScope.push(stack);
            stack.scale(.2F, .2F, .2F);
            // Another listener cancels Pre: vanilla never pushes and Post never runs.
        }
        assertSame(entry, stack.last());
        assertTrue(stack.clear());
        assertFalse(LivingRenderScope.active(stack));
    }

    @Test void nestedRenderCannotConsumeParentsFrame() {
        var stack = new PoseStack(); var entry = stack.last();
        try (var outer = LivingRenderScope.begin(stack)) {
            LivingRenderScope.push(stack); var parent = stack.last();
            try (var inner = LivingRenderScope.begin(stack)) {
                LivingRenderScope.push(stack); stack.translate(4, 5, 6);
            }
            assertSame(parent, stack.last());
            assertTrue(LivingRenderScope.active(stack));
            try (var unchanged = LivingRenderScope.begin(stack)) { }
            assertSame(parent, stack.last());
        }
        assertSame(entry, stack.last()); assertFalse(LivingRenderScope.active(stack));
    }

    @Test void exceptionInsideRendererRestoresAllFramesItLeftBehind() {
        var stack = new PoseStack(); var entry = stack.last();
        assertThrows(IllegalArgumentException.class, () -> {
            try (var scope = LivingRenderScope.begin(stack)) {
                LivingRenderScope.push(stack);
                stack.pushPose(); // Vanilla's frame, before its own normal pop.
                throw new IllegalArgumentException("renderer failed");
            }
        });
        assertSame(entry, stack.last()); assertTrue(stack.clear());
        assertFalse(LivingRenderScope.active(stack));
    }

    @Test void alreadyBalancedFragmentAndDifferentStackAreIsolated() {
        var stack = new PoseStack(); var other = new PoseStack(); var entry = stack.last();
        try (var scope = LivingRenderScope.begin(stack)) {
            assertFalse(LivingRenderScope.active(other));
            LivingRenderScope.push(stack); stack.popPose();
        }
        assertSame(entry, stack.last());
        assertThrows(IllegalStateException.class, () -> LivingRenderScope.push(stack));
    }
}
