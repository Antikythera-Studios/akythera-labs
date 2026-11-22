package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.MotionWarpingNotifyState;
import com.yesman.akythera.api.animation.warping.AttackTargetWarpTarget;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.BlueprintSourceWriter;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;
import java.util.stream.Stream;

/// Editor holder for a [MotionWarpingNotifyState].
public final class MotionWarpingNotifyStateHolder extends AnimationNotifyStateHolder<MotionWarpingNotifyState> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_WARP_TARGET, order = 0, rebuildOnWrite = true)
    private MotionWarpTargetRef target = new MotionWarpTargetRef.Blueprint(AttackTargetWarpTarget.INSTANCE);

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_UPDATE_RATE, order = 1, min = 1.0F)
    private int updateRate = 1;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_WARP_TRANSLATION, order = 2, rebuildOnWrite = true)
    private boolean warpTranslation = true;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MAX_STRETCH, order = 3, min = 0.0F, indent = 1, visibleWhen = "isWarpTranslation")
    private float maxStretch = 2.0F;

    /// The largest change of the stretch in one update, 0 for no limit.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_SMOOTH, order = 4, min = 0.0F, indent = 1, visibleWhen = "isWarpTranslation")
    private float smooth;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MODIFY_X, order = 5, indent = 1, visibleWhen = "isWarpTranslation")
    private boolean modifyX = true;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MODIFY_Y, order = 6, indent = 1, visibleWhen = "isWarpTranslation")
    private boolean modifyY;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_WARP_ROTATION, order = 7, rebuildOnWrite = true)
    private boolean warpRotation = true;

    /// Degrees per tick.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MAX_ROTATION_RATE, order = 8, min = 0.0F, indent = 1, visibleWhen = "isWarpRotation")
    private float maxRotationRate = 30.0F;

    boolean isWarpTranslation() {
        return warpTranslation;
    }

    boolean isWarpRotation() {
        return warpRotation;
    }

    @Override
    public void readFrom(final MotionWarpingNotifyState object) {
        target = MotionWarpTargetRef.of(object.target());
        updateRate = object.updateRate();
        warpTranslation = object.warpTranslation();
        maxStretch = object.maxStretch();
        smooth = object.smooth();
        modifyX = object.modifyX();
        modifyY = object.modifyY();
        warpRotation = object.warpRotation();
        maxRotationRate = object.maxRotationRate();
    }

    @Override
    public MotionWarpingNotifyState makeObject() {
        return new MotionWarpingNotifyState(target.toBlueprint(), updateRate, warpTranslation, maxStretch, smooth, modifyX, modifyY, warpRotation, maxRotationRate);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return target instanceof MotionWarpTargetRef.Entry entry ? EditorHolderCodecs.referenceOf(entry.holder()) : Stream.empty();
    }

    /// Only a montage plays a root motion.
    @Override
    public boolean montageOnly() {
        return true;
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH; // MotionWarpingNotifyState hard-codes it
    }

    @Override
    public String notifyName() {
        return MotionWarpingNotifyState.class.getSimpleName();
    }

    @Override
    public void simulateBegin(final Model3DViewer previewer, final TimeWindow window) {
    }

    @Override
    public void simulateTick(final Model3DViewer previewer, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void simulateEnd(final Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/motion_warping.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        MotionWarpTargetBlueprint blueprint = target.toBlueprint();
        String targetSource = switch (blueprint) {
            case MotionWarpTargetBlueprint.ByData byData -> {
                String compiled = FreemarkerExporter.compiledClass(byData.target());

                yield compiled != null
                    ? "new MotionWarpTargetBlueprint.ByClass(ScriptType.of(" + compiled + ".class))"
                    : "new MotionWarpTargetBlueprint.ByData("
                        + BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY, byData.target().identifier().toString()) + ")";
            }
            case MotionWarpTargetBlueprint.ByClass byClass -> "new MotionWarpTargetBlueprint.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.type()) + ")";
            default -> "new " + blueprint.getClass().getSimpleName() + "()";
        };

        return Map.of(
            "target", targetSource,
            "updateRate", updateRate,
            "warpTranslation", warpTranslation,
            "maxStretch", maxStretch,
            "smooth", smooth,
            "modifyX", modifyX,
            "modifyY", modifyY,
            "warpRotation", warpRotation,
            "maxRotationRate", maxRotationRate
        );
    }
}
