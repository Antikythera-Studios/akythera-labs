package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.IKChain;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorFieldScanner;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/// Editable twin of one [IKChain]. The chain length always fits the solver: a length the solver can't solve is
/// refused, and picking a solver that can't solve the current length moves it to the shortest one it can.
public final class IKChainHolder {
    /// The longest chain the length row offers.
    private static final int MAX_CHAIN_LENGTH = 16;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_TIP_BONE, order = 0, candidates = "bones", searchable = true)
    private @Nullable String tipBone;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CHAIN_LENGTH, order = 1, min = 1.0F, max = MAX_CHAIN_LENGTH)
    private int chainLength;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SOLVER, order = 2, candidates = "solverTypes", rebuildOnWrite = true)
    private IKSolverType solverType;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SOLVER_SETTINGS, order = 3, visibleWhen = "hasSolverSettings")
    private IKSolverSettingsHolder solverSettings;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STEPPING, order = 4)
    private final SteppingHolder stepping;

    private IKChainHolder(
        final @Nullable String tipBone, final int chainLength, final IKSolverSettingsHolder solverSettings, final IKChain.Stepping stepping
    ) {
        this.tipBone = tipBone;
        this.chainLength = chainLength;
        this.solverType = solverSettings.type();
        this.solverSettings = solverSettings;
        this.stepping = new SteppingHolder(stepping);
    }

    /// The holder of `chain`. Throws when no settings holder is registered for its solver type.
    public static IKChainHolder of(final IKChain chain) {
        IKSolverSettingsHolder settings = Objects.requireNonNull(
            IKSolverSettingsHolders.create(chain.solver().type()),
            () -> "No IK solver holder registered for " + chain.solver().type()
        );
        settings.readFrom(chain.solver());

        return new IKChainHolder(chain.tipBone(), chain.chainLength(), settings, chain.stepping());
    }

    /// A two-bone chain with no tip bone picked yet.
    public static IKChainHolder create() {
        return new IKChainHolder(null, 2, new TwoBoneSolverHolder(), IKChain.Stepping.DEFAULT);
    }

    private static List<String> bones(final ArmaturePhysicsDataHolder holder) {
        return holder.boneNames();
    }

    private static List<IKSolverType> solverTypes() {
        return IKSolverSettingsHolders.types();
    }

    public @Nullable String getTipBone() {
        return tipBone;
    }

    public void setTipBone(final @Nullable String tipBone) {
        this.tipBone = tipBone;
    }

    public int getChainLength() {
        return chainLength;
    }

    /// Takes `chainLength` when the solver can solve it.
    public void setChainLength(final int chainLength) {
        if (solverSettings.makeObject().supportsLength(chainLength)) this.chainLength = chainLength;
    }

    public IKSolverType getSolverType() {
        return solverType;
    }

    /// Swaps [#solverSettings] for `solverType`'s holder at its defaults, and moves [#chainLength] to the shortest
    /// length the new solver solves when it can't solve the current one. A type with no registered holder is ignored.
    public void setSolverType(final IKSolverType solverType) {
        if (this.solverType == solverType) return;

        IKSolverSettingsHolder swapped = IKSolverSettingsHolders.create(solverType);

        if (swapped == null) return;

        this.solverType = solverType;
        this.solverSettings = swapped;

        if (!swapped.makeObject().supportsLength(chainLength)) {
            chainLength = IntStream.rangeClosed(1, MAX_CHAIN_LENGTH).filter(swapped.makeObject()::supportsLength).findFirst().orElse(1);
        }
    }

    /// Whether [#solverSettings] has any row to edit.
    boolean hasSolverSettings() {
        return !EditorFieldScanner.fieldsOf(solverSettings.getClass()).isEmpty();
    }

    /// The names of the bones the chain spans in `armature`, from the tip bone upward. Empty while no tip bone is picked.
    public Set<String> boneNames(final Armature armature) {
        Set<String> names = new LinkedHashSet<>();
        Bone bone = tipBone == null ? null : armature.getBoneByName(tipBone);

        for (int i = 0; i < chainLength && bone != null; i++, bone = bone.getParent()) names.add(bone.getName());

        return names;
    }

    /// The chain, or null while no tip bone is picked.
    public @Nullable IKChain makeObject() {
        return tipBone == null ? null : new IKChain(tipBone, chainLength, solverSettings.makeObject(), stepping.makeObject());
    }

    /// Editable twin of [IKChain.Stepping].
    public static final class SteppingHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_DISTANCE_THRESHOLD, order = 0, min = 0.0F)
        private float distanceThreshold;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_ANGULAR_THRESHOLD, order = 1, min = 0.0F, max = 180.0F)
        private float angularThreshold;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STEP_HEIGHT, order = 2, min = 0.0F)
        private float stepHeight;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RAY_MAX_DISTANCE, order = 3, min = 0.0F)
        private float rayMaxDistance;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STEP_TIME, order = 4, min = 0.01F)
        private float stepTime;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STEP_COOLDOWN, order = 5, min = 0.0F)
        private float stepCooldown;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_PREDICTION, order = 6, min = 0.0F)
        private float prediction;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RESET_TIME, order = 7, min = 0.0F)
        private float resetTime;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RESET_DISTANCE, order = 8, min = 0.0F)
        private float resetDistance;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RESET_ANGLE, order = 9, min = 0.0F, max = 180.0F)
        private float resetAngle;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RESET_STEP_TIME, order = 10, min = 0.01F)
        private float resetStepTime;

        public SteppingHolder(final IKChain.Stepping stepping) {
            distanceThreshold = stepping.distanceThreshold();
            angularThreshold = stepping.angularThreshold();
            stepHeight = stepping.stepHeight();
            rayMaxDistance = stepping.rayMaxDistance();
            stepTime = stepping.stepTime();
            stepCooldown = stepping.stepCooldown();
            prediction = stepping.prediction();
            resetTime = stepping.resetTime();
            resetDistance = stepping.resetDistance();
            resetAngle = stepping.resetAngle();
            resetStepTime = stepping.resetStepTime();
        }

        public IKChain.Stepping makeObject() {
            return new IKChain.Stepping(
                distanceThreshold, angularThreshold, stepHeight, rayMaxDistance, stepTime, stepCooldown, prediction, resetTime, resetDistance, resetAngle,
                resetStepTime
            );
        }
    }
}
