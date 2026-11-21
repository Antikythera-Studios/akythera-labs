package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.animation.notify.impl.MotionWarpingNotifyState;
import com.yesman.akythera.api.animation.warping.AttackTargetWarpTarget;
import com.yesman.akythera.api.animation.warping.DataDrivenMotionWarpTarget;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.BlueprintSourceWriter;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.MotionWarpTargetScriptHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Editor holder for a [MotionWarpingNotifyState].
public final class MotionWarpingNotifyStateHolder extends AnimationNotifyStateHolder<MotionWarpingNotifyState> {
    @EditorEditable(
        label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_WARP_TARGET,
        order = 0,
        candidates = "targetCandidates",
        candidateLabel = "targetLabel",
        candidateCategory = "targetCategory",
        searchable = true
    )
    private MotionWarpTargetBlueprint target = AttackTargetWarpTarget.INSTANCE;

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

    /// The targets [#target] may pick, built-in ones first, then the project's, the data packs' and the classes'.
    static List<MotionWarpTargetBlueprint> targetCandidates() {
        List<MotionWarpTargetBlueprint> candidates = new ArrayList<>();
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Set<ResourceKey<MotionWarpTargetScriptBlueprint>> projectKeys = project
            .<MotionWarpTargetScriptBlueprint, MotionWarpTargetScriptHolder>getModifiableEntries(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // A built-in target is a type that decodes from no field
        AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPES.stream()
            .flatMap(codec -> codec.codec().parse(JsonOps.INSTANCE, new JsonObject()).result().stream())
            .filter(blueprint -> !(blueprint instanceof MotionWarpTargetBlueprint.ByClass || blueprint instanceof MotionWarpTargetBlueprint.ByData))
            .forEach(candidates::add);

        projectKeys.stream()
            .sorted(Comparator.comparing(key -> key.identifier().toString()))
            .map(MotionWarpTargetBlueprint.ByData::new)
            .forEach(candidates::add);

        project.getMergedEntries(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).stream()
            .map(Map.Entry::getKey)
            .filter(key -> !projectKeys.contains(key))
            .sorted(Comparator.comparing(key -> key.identifier().toString()))
            .map(MotionWarpTargetBlueprint.ByData::new)
            .forEach(candidates::add);

        // A target class is one a user wrote: the built-in targets and the data pack runner are left out
        Set<Identifier> projectScriptTypes = FreemarkerExporter.projectScriptTypes();
        AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
            .filter(type -> MotionWarpTarget.class.isAssignableFrom(type.type())
                && !projectScriptTypes.contains(AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type))
                && !type.type().isInterface()
                && !Modifier.isAbstract(type.type().getModifiers())
                && type.type() != DataDrivenMotionWarpTarget.class
                && !MotionWarpTargetBlueprint.class.isAssignableFrom(type.type()))
            .map(MotionWarpTargetBlueprint.ByClass::new)
            .forEach(candidates::add);

        return candidates;
    }

    static Component targetLabel(final MotionWarpTargetBlueprint target) {
        return Component.literal(switch (target) {
            case MotionWarpTargetBlueprint.ByData byData -> byData.target().identifier().toString();
            case MotionWarpTargetBlueprint.ByClass byClass -> byClass.type().type().getSimpleName();
            default -> ParseUtils.toDisplayName(Objects.requireNonNull(AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPES.getKey(target.mapCodec())).getPath());
        });
    }

    static Component targetCategory(final MotionWarpTargetBlueprint target) {
        return Component.translatable(switch (target) {
            case MotionWarpTargetBlueprint.ByData byData ->
                AkytheraProject.getCurrentProject().getModifiableEntries(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).stream()
                    .anyMatch(entry -> entry.getKey().identifier().equals(byData.target().identifier()))
                    ? LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_PROJECT
                    : LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_DATAPACK;
            case MotionWarpTargetBlueprint.ByClass _ -> LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_CLASS;
            default -> LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_BUILT_IN;
        });
    }

    boolean isWarpTranslation() {
        return warpTranslation;
    }

    boolean isWarpRotation() {
        return warpRotation;
    }

    @Override
    public void readFrom(final MotionWarpingNotifyState object) {
        target = object.target();
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
        return new MotionWarpingNotifyState(target, updateRate, warpTranslation, maxStretch, smooth, modifyX, modifyY, warpRotation, maxRotationRate);
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
        String targetSource = switch (target) {
            case MotionWarpTargetBlueprint.ByData byData -> {
                String compiled = FreemarkerExporter.compiledClass(byData.target());

                yield compiled != null
                    ? "new MotionWarpTargetBlueprint.ByClass(ScriptType.of(" + compiled + ".class))"
                    : "new MotionWarpTargetBlueprint.ByData("
                        + BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY, byData.target().identifier().toString()) + ")";
            }
            case MotionWarpTargetBlueprint.ByClass byClass -> "new MotionWarpTargetBlueprint.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.type()) + ")";
            default -> "new " + target.getClass().getSimpleName() + "()";
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
