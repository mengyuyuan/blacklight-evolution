package com.codex.blacklight.client.screen;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.tuning.AnimationPoseSettings;
import com.codex.blacklight.client.tuning.AnimationPoseSettings.Action;
import com.codex.blacklight.client.tuning.AnimationPoseSettings.ActionSnapshot;
import com.codex.blacklight.client.tuning.AnimationPoseSettings.Axis;
import com.codex.blacklight.client.tuning.AnimationPoseSettings.Joint;
import com.codex.blacklight.client.tuning.AnimationPoseSettings.TransformMode;
import com.codex.blacklight.animation.PlayerMotionController;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Model-first animation editor. The character remains unobstructed: click a tiny joint
 * handle on the body and drag the model itself to author a rotation keyframe.
 */
public final class AnimationTunerScreen extends Screen {
    private static final double PICK_RADIUS_SQR = 100.0D;
    private static final double ROTATION_SENSITIVITY = 0.72D;
    private static final double GIZMO_RADIUS = 29.0D;
    private static final double GIZMO_RING_HIT_WIDTH = 0.20D;
    private static final double MAX_SQUAT_ROOT_DROP = 0.34D;
    private static final Joint[] SQUAT_JOINTS = {
            Joint.ROOT, Joint.TORSO, Joint.CHEST, Joint.HEAD,
            Joint.THIGH_R, Joint.THIGH_L, Joint.LEG_R, Joint.LEG_L,
            Joint.KNEE_R, Joint.KNEE_L
    };

    private Action selectedAction = Action.CHARGED_JUMP;
    private Joint selectedJoint = Joint.ARM_R;
    private TransformMode transformMode = TransformMode.ROTATE;
    private Axis lockedAxis;
    private float currentTime = 2.03F;
    private boolean playing;
    private boolean showJointHandles = true;
    private boolean squatControl;
    private TransformMode modeBeforeSquat = TransformMode.ROTATE;
    private Axis axisBeforeSquat;
    private boolean draggingModel;
    private boolean draggingSquat;
    private int dragButton = -1;
    private GizmoPart draggedGizmoPart;
    private String status = "拖彩环旋转 · Home视角归位 · Ctrl+Shift+R动作还原";
    private String previewAnimation;

    private TimelineSlider timelineSlider;
    private Button playButton;
    private Button modeButton;
    private Button axisButton;
    private Button squatButton;
    private ActionSnapshot resetUndoSnapshot;

    public AnimationTunerScreen() {
        super(Component.literal("黑光动作时间轴"));
        BlacklightNeoMod.LOGGER.info("Opened model-first Blacklight animation editor");
    }

    @Override
    protected void init() {
        AnimationPoseSettings.load();
        currentTime = clampTime(currentTime);
        buildTopControls();
        buildTimeline();
        previewSelected();
    }

    private void buildTopControls() {
        addRenderableWidget(Button.builder(com.codex.blacklight.client.tuning.MercerAppearance.label(), button -> {
            com.codex.blacklight.client.tuning.MercerAppearance.toggle();
            button.setMessage(com.codex.blacklight.client.tuning.MercerAppearance.label());
            status="外观设置已切换，感染后的本机角色生效";
        }).bounds(6,69,188,17).build());
        addRenderableWidget(Button.builder(actionLabel(Action.CHARGED_JUMP),
                        button -> selectAction(Action.CHARGED_JUMP))
                .bounds(6, 6, 92, 17).build());
        addRenderableWidget(Button.builder(actionLabel(Action.GROUND_SPIKE),
                        button -> selectAction(Action.GROUND_SPIKE))
                .bounds(102, 6, 92, 17).build());
        modeButton = addRenderableWidget(Button.builder(modeLabel(), button ->
                setTransformMode(transformMode.next())).bounds(6, 27, 92, 17).build());
        axisButton = addRenderableWidget(Button.builder(axisLabel(), button -> {
            lockedAxis = lockedAxis == null ? Axis.X : switch (lockedAxis) {
                case X -> Axis.Y;
                case Y -> Axis.Z;
                case Z -> null;
            };
            updateTransformControls();
        }).bounds(102, 27, 92, 17).build());
        squatButton = addRenderableWidget(Button.builder(squatLabel(), button -> toggleSquatControl())
                .bounds(6, 48, 188, 17).build());
        addRenderableWidget(Button.builder(Component.literal("背面归位"), button -> resetView())
                .bounds(width - 250, 6, 68, 17).build());
        addRenderableWidget(Button.builder(Component.literal("动作还原"), button -> resetCurrentAction())
                .bounds(width - 178, 6, 68, 17).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> saveTimeline())
                .bounds(width - 106, 6, 50, 17).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(width - 52, 6, 48, 17).build());
    }

    private void buildTimeline() {
        int buttonY = height - 21;
        addRenderableWidget(Button.builder(Component.literal("上一帧"), button -> jumpKeyframe(false))
                .bounds(5, buttonY, 47, 17).build());
        playButton = addRenderableWidget(Button.builder(playLabel(), button -> togglePlayback())
                .bounds(56, buttonY, 47, 17).build());
        addRenderableWidget(Button.builder(Component.literal("下一帧"), button -> jumpKeyframe(true))
                .bounds(107, buttonY, 47, 17).build());
        timelineSlider = addRenderableWidget(new TimelineSlider(
                160, buttonY, Math.max(120, width - 165)));
    }

    private void selectAction(Action action) {
        if (selectedAction == action) return;
        float normalized = currentTime / selectedAction.totalTime();
        selectedAction = action;
        currentTime = action.totalTime() * normalized;
        playing = false;
        status = "已切换到" + action.displayName();
        rebuildWidgets();
    }

    private void selectJoint(Joint joint) {
        selectedJoint = joint;
        status = "已选 " + joint.displayName() + "：拖旋转球彩环，中心可自由旋转";
        refreshTimelineLabel();
    }

    private Component actionLabel(Action action) {
        return Component.literal((selectedAction == action ? "● " : "○ ") + action.displayName());
    }

    private Component playLabel() { return Component.literal(playing ? "暂停" : "播放"); }

    private Component modeLabel() {
        return Component.literal(transformMode.shortcut() + " " + transformMode.displayName());
    }

    private Component axisLabel() {
        return Component.literal("轴：" + (lockedAxis == null ? "自由" : lockedAxis.name()));
    }

    private Component squatLabel() {
        return Component.literal(String.format(Locale.ROOT, "C 下蹲联动 %.0f%% · 拖人物上下",
                squatDepth() * 100.0D));
    }

    private void toggleSquatControl() {
        squatControl = !squatControl;
        if (squatControl) {
            modeBeforeSquat = transformMode;
            axisBeforeSquat = lockedAxis;
            selectedJoint = Joint.ROOT;
            transformMode = TransformMode.MOVE;
            lockedAxis = Axis.Y;
            status = "下蹲联动已开启：在人物上按住左键上下拖动";
        } else {
            transformMode = modeBeforeSquat;
            lockedAxis = axisBeforeSquat;
            status = "下蹲联动已关闭，已恢复" + transformMode.displayName() + "工具";
        }
        updateTransformControls();
        refreshSquatLabel();
    }

    private void setTransformMode(TransformMode mode) {
        if (squatControl) {
            squatControl = false;
            lockedAxis = axisBeforeSquat;
            refreshSquatLabel();
        }
        transformMode = mode;
        status = mode.displayName() + "模式 · X/Y/Z 锁轴 · Shift 精调";
        updateTransformControls();
    }

    private void toggleAxis(Axis axis) {
        if (squatControl) {
            squatControl = false;
            transformMode = TransformMode.ROTATE;
            refreshSquatLabel();
        }
        lockedAxis = lockedAxis == axis ? null : axis;
        status = lockedAxis == null ? "已恢复自由轴" : "已锁定 " + lockedAxis + " 轴";
        updateTransformControls();
    }

    private void updateTransformControls() {
        if (modeButton != null) modeButton.setMessage(modeLabel());
        if (axisButton != null) axisButton.setMessage(axisLabel());
    }

    private void refreshSquatLabel() {
        if (squatButton != null) squatButton.setMessage(squatLabel());
    }

    private void togglePlayback() {
        playing = !playing;
        if (playing && currentTime >= selectedAction.totalTime() - 0.001F) currentTime = 0.0F;
        if (playButton != null) playButton.setMessage(playLabel());
        setPreviewTime(currentTime);
    }

    private void jumpKeyframe(boolean forward) {
        List<Float> frames = AnimationPoseSettings.keyframeTimes(selectedAction, selectedJoint);
        if (frames.isEmpty()) {
            status = selectedJoint.displayName() + "还没有关键帧";
            return;
        }
        float target = forward ? frames.get(0) : frames.get(frames.size() - 1);
        if (forward) {
            for (float frame : frames) {
                if (frame > currentTime + 0.0125F) { target = frame; break; }
            }
        } else {
            for (int index = frames.size() - 1; index >= 0; index--) {
                float frame = frames.get(index);
                if (frame < currentTime - 0.0125F) { target = frame; break; }
            }
        }
        currentTime = target;
        playing = false;
        syncTime();
    }

    private void deleteCurrentFrame() {
        boolean removed;
        if (squatControl) {
            removed = false;
            for (Joint joint : SQUAT_JOINTS) {
                removed |= AnimationPoseSettings.deleteKeyframeAt(selectedAction, joint, currentTime);
            }
        } else {
            removed = AnimationPoseSettings.deleteKeyframeAt(
                    selectedAction, selectedJoint, currentTime);
        }
        status = removed ? selectedJoint.displayName() + "当前关键帧已删除"
                : "播放头附近没有关键帧";
        refreshSquatLabel();
        refreshTimelineLabel();
    }

    private void clearSelectedTrack() {
        if (squatControl) {
            for (Joint joint : SQUAT_JOINTS) AnimationPoseSettings.resetJoint(selectedAction, joint);
            status = "下蹲联动轨道已清空，恢复原动作";
        } else {
            AnimationPoseSettings.resetJoint(selectedAction, selectedJoint);
            status = selectedJoint.displayName() + "轨道已清空，恢复原动作";
        }
        refreshSquatLabel();
        refreshTimelineLabel();
    }

    private void saveTimeline() {
        AnimationPoseSettings.save();
        status = "时间轴已保存";
    }

    private void resetView() {
        if (minecraft == null) return;
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (minecraft.player != null) {
            float facing = minecraft.player.getYRot();
            minecraft.player.setXRot(0.0F);
            minecraft.player.xRotO = 0.0F;
            minecraft.player.yBodyRot = facing;
            minecraft.player.yBodyRotO = facing;
            minecraft.player.yHeadRot = facing;
            minecraft.player.yHeadRotO = facing;
        }
        status = "视角已归位：第三人称背面、俯仰归零";
    }

    private void resetCurrentAction() {
        resetUndoSnapshot = AnimationPoseSettings.snapshotAction(selectedAction);
        AnimationPoseSettings.resetAction(selectedAction);
        squatControl = false;
        transformMode = TransformMode.ROTATE;
        lockedAxis = null;
        selectedJoint = Joint.ARM_R;
        updateTransformControls();
        refreshSquatLabel();
        previewSelected();
        syncTime();
        status = selectedAction.displayName() + "已恢复默认动作 · Ctrl+Z 可撤销";
    }

    private void undoActionReset() {
        if (resetUndoSnapshot == null) {
            status = "没有可撤销的动作还原";
            return;
        }
        Action restoredAction = resetUndoSnapshot.action();
        AnimationPoseSettings.restoreAction(resetUndoSnapshot);
        resetUndoSnapshot = null;
        if (selectedAction != restoredAction) selectAction(restoredAction);
        else {
            previewSelected();
            syncTime();
        }
        status = restoredAction.displayName() + "已恢复到还原前";
    }

    private void previewSelected() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        previewAnimation = animationFor(selectedAction);
        PlayerMotionController.preview(client.player, previewAnimation, currentTime);
        setPreviewTime(currentTime);
    }

    private void setPreviewTime(float time) {
        currentTime = clampTime(time);
        if (Minecraft.getInstance().player != null && previewAnimation != null)
            PlayerMotionController.seekPreview(Minecraft.getInstance().player, currentTime);
    }

    private void syncTime() {
        setPreviewTime(currentTime);
        if (timelineSlider != null) timelineSlider.syncFromTime();
        if (playButton != null) playButton.setMessage(playLabel());
    }

    private void refreshTimelineLabel() {
        if (timelineSlider != null) timelineSlider.updateMessage();
    }

    private static String animationFor(Action action) {
        return action == Action.GROUND_SPIKE
                ? com.codex.blacklight.animation.MotionLibrary.resolve(com.codex.blacklight.animation.PlayerAction.CLAW_GROUNDSPIKE)
                : "charged_jump";
    }

    private float clampTime(float time) {
        return Math.max(0.0F, Math.min(selectedAction.totalTime(), time));
    }

    @Override
    public void tick() {
        super.tick();
        if (!playing) return;
        currentTime += 0.05F;
        if (currentTime > selectedAction.totalTime()) currentTime = 0.0F;
        syncTime();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }

        if (squatControl && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && insideModelManipulationArea(mouseX, mouseY)) {
            draggingSquat = true;
            draggingModel = true;
            dragButton = button;
            playing = false;
            if (playButton != null) playButton.setMessage(playLabel());
            status = "下蹲联动：向下拖加深，向上拖站起";
            return true;
        }

        // A colored ring has priority over nearby bone dots. Previously a shoulder or
        // elbow dot intercepted the ring click, so the ring highlighted but appeared to
        // have no effect. The small free-rotation center is handled after joint picking.
        GizmoPart gizmoPart = transformMode == TransformMode.ROTATE
                ? findRotationGizmoPart(mouseX, mouseY) : null;
        if (gizmoPart != null && gizmoPart != GizmoPart.FREE) {
            lockedAxis = gizmoPart.axis();
            updateTransformControls();
            status = "旋转球 " + gizmoPart.axis() + " 轴已锁定 · Shift精调 · Ctrl吸附5°";
            beginModelDrag(button, gizmoPart);
            return true;
        }

        Joint picked = findJoint(mouseX, mouseY, 25.0D);
        if (picked != null && picked != selectedJoint) {
            selectJoint(picked);
            beginModelDrag(button, GizmoPart.FREE);
            return true;
        }

        if (gizmoPart != null) {
            lockedAxis = gizmoPart.axis();
            updateTransformControls();
            status = gizmoPart == GizmoPart.FREE
                    ? "旋转球中心：自由旋转 · Shift精调 · Ctrl吸附5°"
                    : "旋转球 " + gizmoPart.axis() + " 轴已锁定 · Shift精调 · Ctrl吸附5°";
            beginModelDrag(button, gizmoPart);
            return true;
        }

        picked = findJoint(mouseX, mouseY);
        if (picked != null) selectJoint(picked);
        if (picked != null || insideModelManipulationArea(mouseX, mouseY)) {
            beginModelDrag(button, GizmoPart.FREE);
            return true;
        }
        return false;
    }

    private void beginModelDrag(int button, GizmoPart gizmoPart) {
            draggingModel = true;
            dragButton = button;
            draggedGizmoPart = gizmoPart;
            playing = false;
            if (playButton != null) playButton.setMessage(playLabel());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!draggingModel || button != dragButton) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (draggingSquat) {
            applySquatDrag(dragY);
            refreshSquatLabel();
            refreshTimelineLabel();
            return true;
        }
        double fine = hasShiftDown() ? 0.18D : 1.0D;
        switch (transformMode) {
            case ROTATE -> applyRotationDrag(button, mouseX, mouseY, dragX, dragY,
                    ROTATION_SENSITIVITY * fine);
            case MOVE -> applyMoveDrag(button, dragX, dragY, 0.012D * fine);
            case SCALE -> applyScaleDrag(dragX, dragY, 0.008D * fine);
        }
        refreshTimelineLabel();
        return true;
    }

    private void applyRotationDrag(int button, double mouseX, double mouseY,
                                   double dragX, double dragY, double sensitivity) {
        Axis gizmoAxis = draggedGizmoPart == null ? null : draggedGizmoPart.axis();
        if (gizmoAxis != null) {
            double delta;
            if (gizmoAxis == Axis.X) {
                delta = -dragY * sensitivity;
            } else if (gizmoAxis == Axis.Y) {
                delta = dragX * sensitivity;
            } else {
                JointPoint center = jointPoint(selectedJoint);
                double previousAngle = Math.atan2(mouseY - dragY - center.y(),
                        mouseX - dragX - center.x());
                double currentAngle = Math.atan2(mouseY - center.y(), mouseX - center.x());
                delta = Math.toDegrees(normalizeRadians(currentAngle - previousAngle))
                        * (hasShiftDown() ? 0.18D : 1.0D);
            }
            addChannel(TransformMode.ROTATE, gizmoAxis, delta);
        } else if (lockedAxis != null) {
            double delta = lockedAxis == Axis.X ? -dragY * sensitivity : dragX * sensitivity;
            addChannel(TransformMode.ROTATE, lockedAxis, delta);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            addChannel(TransformMode.ROTATE, Axis.X, -dragY * sensitivity);
            addChannel(TransformMode.ROTATE, Axis.Y, dragX * sensitivity);
        } else {
            addChannel(TransformMode.ROTATE, Axis.Z, dragX * sensitivity);
        }
        status = "旋转 " + selectedJoint.displayName()
                + (lockedAxis == null ? "（左拖XY / 右拖Z）" : "（" + lockedAxis + "轴）");
    }

    private static double normalizeRadians(double angle) {
        if (angle > Math.PI) return angle - Math.PI * 2.0D;
        if (angle < -Math.PI) return angle + Math.PI * 2.0D;
        return angle;
    }

    private void applyMoveDrag(int button, double dragX, double dragY, double sensitivity) {
        if (lockedAxis != null) {
            double delta = lockedAxis == Axis.X ? dragX * sensitivity : -dragY * sensitivity;
            addChannel(TransformMode.MOVE, lockedAxis, delta);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            addChannel(TransformMode.MOVE, Axis.X, dragX * sensitivity);
            addChannel(TransformMode.MOVE, Axis.Y, -dragY * sensitivity);
        } else {
            addChannel(TransformMode.MOVE, Axis.Z, -dragY * sensitivity);
        }
        status = "位移 " + selectedJoint.displayName()
                + (lockedAxis == null ? "（左拖XY / 右拖Z）" : "（" + lockedAxis + "轴）");
    }

    private void applyScaleDrag(double dragX, double dragY, double sensitivity) {
        double delta = (Math.abs(dragY) >= Math.abs(dragX) ? -dragY : dragX) * sensitivity;
        if (lockedAxis != null) {
            addChannel(TransformMode.SCALE, lockedAxis, delta);
        } else {
            addChannel(TransformMode.SCALE, Axis.X, delta);
            addChannel(TransformMode.SCALE, Axis.Y, delta);
            addChannel(TransformMode.SCALE, Axis.Z, delta);
        }
        status = "缩放 " + selectedJoint.displayName()
                + (lockedAxis == null ? "（整体）" : "（" + lockedAxis + "轴）");
    }

    private void applySquatDrag(double dragY) {
        double fine = hasShiftDown() ? 0.20D : 1.0D;
        double depth = Math.max(0.0D, Math.min(1.0D,
                squatDepth() + dragY * 0.012D * fine));
        if (hasControlDown()) depth = Math.round(depth * 10.0D) / 10.0D;
        setSquatDepth(depth);
        status = String.format(Locale.ROOT,
                "下蹲 %.0f%% · 根骨下降并联动髋、膝、脚保持朝向", depth * 100.0D);
    }

    private double squatDepth() {
        double rootY = AnimationPoseSettings.valueAt(selectedAction, Joint.ROOT,
                currentTime, TransformMode.MOVE, Axis.Y);
        return Math.max(0.0D, Math.min(1.0D, -rootY / MAX_SQUAT_ROOT_DROP));
    }

    private void setSquatDepth(double depth) {
        setChannel(Joint.ROOT, TransformMode.MOVE, Axis.Y, -MAX_SQUAT_ROOT_DROP * depth);
        setChannel(Joint.TORSO, TransformMode.ROTATE, Axis.X, -14.0D * depth);
        setChannel(Joint.CHEST, TransformMode.ROTATE, Axis.X, -8.0D * depth);
        setChannel(Joint.HEAD, TransformMode.ROTATE, Axis.X, 10.0D * depth);
        setChannel(Joint.THIGH_R, TransformMode.ROTATE, Axis.X, -48.0D * depth);
        setChannel(Joint.THIGH_L, TransformMode.ROTATE, Axis.X, -48.0D * depth);
        setChannel(Joint.LEG_R, TransformMode.ROTATE, Axis.X, 72.0D * depth);
        setChannel(Joint.LEG_L, TransformMode.ROTATE, Axis.X, 72.0D * depth);
        // Counter-rotate the foot/knee endpoints so they remain approximately level.
        setChannel(Joint.KNEE_R, TransformMode.ROTATE, Axis.X, -24.0D * depth);
        setChannel(Joint.KNEE_L, TransformMode.ROTATE, Axis.X, -24.0D * depth);
    }

    private void setChannel(Joint joint, TransformMode mode, Axis axis, double value) {
        AnimationPoseSettings.setValueAt(selectedAction, joint, currentTime, mode, axis, value);
    }

    private void addChannel(TransformMode mode, Axis axis, double delta) {
        double current = AnimationPoseSettings.valueAt(
                selectedAction, selectedJoint, currentTime, mode, axis);
        double next = current + delta;
        if (mode == TransformMode.ROTATE && hasControlDown()) {
            next = Math.round(next / 5.0D) * 5.0D;
        }
        AnimationPoseSettings.setValueAt(
                selectedAction, selectedJoint, currentTime, mode, axis, next);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingModel && button == dragButton) {
            draggingModel = false;
            draggingSquat = false;
            dragButton = -1;
            draggedGizmoPart = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollY) {
        if (scrollY != 0.0D) {
            currentTime = clampTime(currentTime + (float)(scrollY * 0.05D));
            playing = false;
            syncTime();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean insideModelManipulationArea(double mouseX, double mouseY) {
        double centerX = width * 0.5D;
        double headY = height * 0.5D - 10.0D;
        return mouseX >= centerX - 70.0D && mouseX <= centerX + 70.0D
                && mouseY >= headY - 20.0D && mouseY <= headY + 105.0D;
    }

    private Joint findJoint(double mouseX, double mouseY) {
        return findJoint(mouseX, mouseY, PICK_RADIUS_SQR);
    }

    private Joint findJoint(double mouseX, double mouseY, double radiusSquared) {
        if (!showJointHandles) return null;
        Joint nearest = null;
        double nearestDistance = radiusSquared;
        for (Joint joint : Joint.values()) {
            JointPoint point = jointPoint(joint);
            double dx = mouseX - point.x();
            double dy = mouseY - point.y();
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = joint;
            }
        }
        return nearest;
    }

    private GizmoPart findRotationGizmoPart(double mouseX, double mouseY) {
        if (!showJointHandles) return null;
        JointPoint center = jointPoint(selectedJoint);
        double dx = mouseX - center.x();
        double dy = mouseY - center.y();
        double distance = Math.sqrt(dx * dx + dy * dy);

        RingHit best = new RingHit(GizmoPart.Z, Math.abs(distance / GIZMO_RADIUS - 1.0D));
        best = nearer(best, new RingHit(GizmoPart.X,
                ellipseDistance(dx, dy, GIZMO_RADIUS * 0.38D, GIZMO_RADIUS)));
        best = nearer(best, new RingHit(GizmoPart.Y,
                ellipseDistance(dx, dy, GIZMO_RADIUS, GIZMO_RADIUS * 0.38D)));
        if (best.distance() <= GIZMO_RING_HIT_WIDTH) return best.part();
        return distance <= GIZMO_RADIUS * 0.56D ? GizmoPart.FREE : null;
    }

    private static RingHit nearer(RingHit first, RingHit second) {
        return second.distance() < first.distance() ? second : first;
    }

    private static double ellipseDistance(double x, double y, double radiusX, double radiusY) {
        return Math.abs(Math.sqrt(x * x / (radiusX * radiusX)
                + y * y / (radiusY * radiusY)) - 1.0D);
    }

    private JointPoint jointPoint(Joint joint) {
        int centerX = width / 2;
        int headY = height / 2 - 10;
        return switch (joint) {
            case HEAD -> new JointPoint(centerX, headY);
            case CHEST -> new JointPoint(centerX, headY + 15);
            case TORSO -> new JointPoint(centerX, headY + 30);
            case ROOT -> new JointPoint(centerX, headY + 44);
            // The editor forces THIRD_PERSON_BACK. In a rear view the model's
            // anatomical right is also the viewer's screen-right; do not mirror it.
            case SHOULDER_R -> new JointPoint(centerX + 11, headY + 16);
            case SHOULDER_L -> new JointPoint(centerX - 11, headY + 16);
            case ARM_R -> new JointPoint(centerX + 18, headY + 28);
            case ARM_L -> new JointPoint(centerX - 18, headY + 28);
            case ELBOW_R -> new JointPoint(centerX + 23, headY + 42);
            case ELBOW_L -> new JointPoint(centerX - 23, headY + 42);
            case HAND_R -> new JointPoint(centerX + 25, headY + 57);
            case HAND_L -> new JointPoint(centerX - 25, headY + 57);
            case TOOL_R -> new JointPoint(centerX + 28, headY + 69);
            case TOOL_L -> new JointPoint(centerX - 28, headY + 69);
            case THIGH_R -> new JointPoint(centerX + 8, headY + 51);
            case THIGH_L -> new JointPoint(centerX - 8, headY + 51);
            case LEG_R -> new JointPoint(centerX + 9, headY + 69);
            case LEG_L -> new JointPoint(centerX - 9, headY + 69);
            case KNEE_R -> new JointPoint(centerX + 9, headY + 87);
            case KNEE_L -> new JointPoint(centerX - 9, headY + 87);
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Only two narrow HUD strips remain; the character itself is never covered.
        graphics.fill(2, 2, 198, 102, 0x26090B10);
        graphics.renderOutline(2, 2, 196, 100, 0x66E23A3A);
        graphics.drawString(font, selectedJoint.displayName() + " / " + selectedJoint.boneName(),
                7, 70, 0xFFFF7378, false);
        graphics.drawString(font, transformReadout(), 7, 81, 0xFFF2F4F8, false);
        graphics.drawString(font, font.plainSubstrByWidth(status, 186), 7, 92,
                0xFFD7DCE5, false);

        graphics.fill(0, height - 24, width, height, 0x22090B10);
        if (showJointHandles) renderJointHandles(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** This is an in-world overlay, not a menu: never blur or darken the rendered level. */
    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Intentionally empty. Screen's default implementation applies the menu blur
        // before drawing widgets, which makes the player impossible to pose accurately.
    }

    private void renderJointHandles(GuiGraphics graphics, int mouseX, int mouseY) {
        Joint hovered = findJoint(mouseX, mouseY);
        for (Joint joint : Joint.values()) {
            JointPoint point = jointPoint(joint);
            if (joint == selectedJoint) {
                graphics.fill(point.x() - 4, point.y() - 4,
                        point.x() + 5, point.y() + 5, 0xE6FF303A);
                graphics.renderOutline(point.x() - 5, point.y() - 5, 11, 11, 0xFFFFFFFF);
            } else if (joint == hovered) {
                graphics.fill(point.x() - 3, point.y() - 3,
                        point.x() + 4, point.y() + 4, 0xE6FFD34D);
            } else {
                graphics.fill(point.x() - 1, point.y() - 1,
                        point.x() + 2, point.y() + 2, 0x99FFFFFF);
            }
        }
        JointPoint selectedPoint = jointPoint(selectedJoint);
        if (squatControl) {
            renderSquatGizmo(graphics, jointPoint(Joint.ROOT));
        } else if (transformMode == TransformMode.ROTATE) {
            renderRotationGizmo(graphics, selectedPoint, mouseX, mouseY);
        } else {
            renderAxisGizmo(graphics, selectedPoint);
        }
        if (hovered != null && hovered != selectedJoint) {
            JointPoint point = jointPoint(hovered);
            graphics.drawString(font, hovered.displayName(), point.x() + 7, point.y() - 4,
                    0xFFFFFFFF, true);
        }
    }

    private void renderSquatGizmo(GuiGraphics graphics, JointPoint point) {
        int color = draggingSquat ? 0xFFFFFF8B : 0xFFFFC83D;
        graphics.vLine(point.x(), point.y() - 28, point.y() + 35, color);
        graphics.fill(point.x() - 5, point.y() - 4, point.x() + 6, point.y() + 5, color);
        graphics.fill(point.x() - 4, point.y() - 29, point.x() + 5, point.y() - 26, color);
        graphics.fill(point.x() - 4, point.y() + 33, point.x() + 5, point.y() + 36, color);
        graphics.drawString(font, String.format(Locale.ROOT, "蹲 %.0f%%", squatDepth() * 100.0D),
                point.x() + 9, point.y() - 4, color, true);
    }

    private void renderRotationGizmo(GuiGraphics graphics, JointPoint point,
                                     int mouseX, int mouseY) {
        GizmoPart hovered = findRotationGizmoPart(mouseX, mouseY);
        GizmoPart active = draggedGizmoPart != null ? draggedGizmoPart : hovered;
        int xColor = gizmoColor(GizmoPart.X, active, 0xFFEF3E46, 0xFFFF9095);
        int yColor = gizmoColor(GizmoPart.Y, active, 0xFF41D95F, 0xFF9CFFAE);
        int zColor = gizmoColor(GizmoPart.Z, active, 0xFF4287FF, 0xFFA2C3FF);
        int freeColor = active == GizmoPart.FREE ? 0x80FFFFFF : 0x2AFFFFFF;

        int radius = (int)Math.round(GIZMO_RADIUS);
        graphics.fill(point.x() - 7, point.y() - 7, point.x() + 8, point.y() + 8,
                freeColor);
        drawEllipse(graphics, point.x(), point.y(), radius, radius, zColor);
        drawEllipse(graphics, point.x(), point.y(), Math.max(2, (int)(radius * 0.38D)),
                radius, xColor);
        drawEllipse(graphics, point.x(), point.y(), radius,
                Math.max(2, (int)(radius * 0.38D)), yColor);

        graphics.drawString(font, "X", point.x() + 8, point.y() - radius - 7,
                xColor, true);
        graphics.drawString(font, "Y", point.x() + radius + 4, point.y() - 4,
                yColor, true);
        graphics.drawString(font, "Z", point.x() - radius - 10, point.y() - 4,
                zColor, true);
    }

    private int gizmoColor(GizmoPart part, GizmoPart active, int normal, int highlight) {
        if (active == part || (active == null && lockedAxis == part.axis())) return highlight;
        if (active != null && active != GizmoPart.FREE) return (normal & 0x00FFFFFF) | 0x70000000;
        return normal;
    }

    private static void drawEllipse(GuiGraphics graphics, int centerX, int centerY,
                                    int radiusX, int radiusY, int color) {
        final int segments = 96;
        for (int index = 0; index < segments; index++) {
            double angle = Math.PI * 2.0D * index / segments;
            int x = centerX + (int)Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int)Math.round(Math.sin(angle) * radiusY);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void renderAxisGizmo(GuiGraphics graphics, JointPoint point) {
        int length = 14;
        int xColor = lockedAxis == null || lockedAxis == Axis.X ? 0xFFFF454D : 0x777A2528;
        int yColor = lockedAxis == null || lockedAxis == Axis.Y ? 0xFF55E06A : 0x772A7035;
        int zColor = lockedAxis == null || lockedAxis == Axis.Z ? 0xFF4E8FFF : 0x7727477F;
        graphics.hLine(point.x() + 6, point.x() + length, point.y(), xColor);
        graphics.vLine(point.x(), point.y() - length, point.y() - 6, yColor);
        for (int index = 6; index <= length; index++) {
            graphics.fill(point.x() + index, point.y() + index,
                    point.x() + index + 1, point.y() + index + 1, zColor);
        }
    }

    private String transformReadout() {
        double x = AnimationPoseSettings.valueAt(
                selectedAction, selectedJoint, currentTime, transformMode, Axis.X);
        double y = AnimationPoseSettings.valueAt(
                selectedAction, selectedJoint, currentTime, transformMode, Axis.Y);
        double z = AnimationPoseSettings.valueAt(
                selectedAction, selectedJoint, currentTime, transformMode, Axis.Z);
        return switch (transformMode) {
            case MOVE -> String.format(Locale.ROOT, "位置 X%+.2f  Y%+.2f  Z%+.2f", x, y, z);
            case ROTATE -> String.format(Locale.ROOT, "X弯%+.1f° Y扭%+.1f° Z侧%+.1f°", x, y, z);
            case SCALE -> String.format(Locale.ROOT, "大小 X%.2f  Y%.2f  Z%.2f", x, y, z);
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyTogglePerspective.matches(keyCode, scanCode)) {
            var previous = minecraft.options.getCameraType();
            var next = previous.cycle();
            minecraft.options.setCameraType(next);
            if (previous.isFirstPerson() != next.isFirstPerson())
                minecraft.gameRenderer.checkEntityPostEffect(next.isFirstPerson() ? minecraft.getCameraEntity() : null);
            minecraft.levelRenderer.needsUpdate();
            status = "视角已切换";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_J) { onClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_SPACE) { togglePlayback(); return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME) { resetView(); return true; }
        if (keyCode == GLFW.GLFW_KEY_R && hasControlDown() && hasShiftDown()) {
            resetCurrentAction();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_1) { selectAction(Action.CHARGED_JUMP); return true; }
        if (keyCode == GLFW.GLFW_KEY_2) { selectAction(Action.GROUND_SPIKE); return true; }
        if (keyCode == GLFW.GLFW_KEY_W) { setTransformMode(TransformMode.MOVE); return true; }
        if (keyCode == GLFW.GLFW_KEY_E) { setTransformMode(TransformMode.ROTATE); return true; }
        if (keyCode == GLFW.GLFW_KEY_R && !hasControlDown()) {
            setTransformMode(TransformMode.SCALE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && hasControlDown()) { undoActionReset(); return true; }
        if (keyCode == GLFW.GLFW_KEY_X) { toggleAxis(Axis.X); return true; }
        if (keyCode == GLFW.GLFW_KEY_Y) { toggleAxis(Axis.Y); return true; }
        if (keyCode == GLFW.GLFW_KEY_Z) { toggleAxis(Axis.Z); return true; }
        if (keyCode == GLFW.GLFW_KEY_F) {
            lockedAxis = null;
            updateTransformControls();
            status = "已恢复自由轴";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            showJointHandles = !showJointHandles;
            status = showJointHandles ? "骨骼点已显示" : "骨骼点已隐藏，可继续拖当前骨骼";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C) { toggleSquatControl(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DELETE) { deleteCurrentFrame(); return true; }
        if (keyCode == GLFW.GLFW_KEY_R && hasControlDown()) { clearSelectedTrack(); return true; }
        if (keyCode == GLFW.GLFW_KEY_S && hasControlDown()) { saveTimeline(); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            currentTime = clampTime(currentTime - (hasShiftDown() ? 0.005F : 0.05F));
            playing = false;
            syncTime();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            currentTime = clampTime(currentTime + (hasShiftDown() ? 0.005F : 0.05F));
            playing = false;
            syncTime();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        AnimationPoseSettings.save();
        stopPreview();
        super.onClose();
    }

    @Override
    public void removed() {
        stopPreview();
        super.removed();
    }

    private void stopPreview() {
        playing = false;
        if (minecraft != null && minecraft.player != null) PlayerMotionController.clearPreview(minecraft.player);
        previewAnimation = null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record JointPoint(int x, int y) { }
    private record RingHit(GizmoPart part, double distance) { }

    private enum GizmoPart {
        X(Axis.X), Y(Axis.Y), Z(Axis.Z), FREE(null);

        private final Axis axis;

        GizmoPart(Axis axis) { this.axis = axis; }
        Axis axis() { return axis; }
    }

    private final class TimelineSlider extends AbstractSliderButton {
        private TimelineSlider(int x, int y, int width) {
            super(x, y, width, 17, Component.empty(), currentTime / selectedAction.totalTime());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.ROOT, "%.3f / %.3f 秒%s",
                    currentTime, selectedAction.totalTime(),
                    AnimationPoseSettings.hasKeyframeAt(selectedAction, selectedJoint, currentTime)
                            ? "  ◆" : "")));
        }

        @Override
        protected void applyValue() {
            currentTime = (float)(value * selectedAction.totalTime());
            playing = false;
            setPreviewTime(currentTime);
            if (playButton != null) playButton.setMessage(playLabel());
            updateMessage();
        }

        private void syncFromTime() {
            value = currentTime / selectedAction.totalTime();
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int innerWidth = getWidth() - 10;
            for (float frame : AnimationPoseSettings.keyframeTimes(selectedAction, selectedJoint)) {
                int markerX = getX() + 5 + Math.round(innerWidth * frame / selectedAction.totalTime());
                graphics.fill(markerX - 1, getY() + 12, markerX + 2, getY() + 16, 0xFFFF3C45);
            }
        }
    }
}
