package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

/** Owns our Pre-event pushes for one renderer invocation, including cancelled/nested calls. */
public final class LivingRenderScope implements AutoCloseable {
    private static final ThreadLocal<LivingRenderScope> ACTIVE = new ThreadLocal<>();
    private final LivingRenderScope previous;
    private final PoseStack stack;
    private final PoseStack.Pose baseline;
    private boolean pushed;

    private LivingRenderScope(PoseStack stack) {
        this.stack = stack;
        baseline = stack.last();
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static LivingRenderScope begin(PoseStack stack) { return new LivingRenderScope(stack); }
    public static boolean active(PoseStack stack) {
        var scope = ACTIVE.get();
        return scope != null && scope.stack == stack;
    }
    public static void push(PoseStack stack) {
        var scope = ACTIVE.get();
        if (scope == null || scope.stack != stack) throw new IllegalStateException("Missing living render scope");
        stack.pushPose();
        scope.pushed = true;
    }

    @Override public void close() {
        try {
            // A renderer can throw after pushing its own frame. Unwind to this
            // invocation's entry frame, without touching a parent's transform.
            if (pushed) while (stack.last() != baseline) stack.popPose();
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }
}
