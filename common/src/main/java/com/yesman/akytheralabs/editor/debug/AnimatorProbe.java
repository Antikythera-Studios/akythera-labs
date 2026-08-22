package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationStateMachine;
import com.yesman.akythera.core.animation.driver.CachedPoseReference;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akytheralabs.mixin.AnimationLayerAccessor;
import com.yesman.akytheralabs.mixin.AnimationStateMachineAccessor;
import com.yesman.akytheralabs.mixin.BlendingAnimatorAccessor;
import com.yesman.akytheralabs.mixin.CachedPoseReferenceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/// Reads what a possessed entity's animator is running, for the editor screens that mirror it.
public final class AnimatorProbe {
    private AnimatorProbe() {}

    /// The value `key` resolves to against the entity's level, or null when the entry is gone.
    /// This is the object an entity plays, never the unsaved copy an editor screen holds.
    public static <T> @Nullable T resolve(final EntityPatch<?> entityPatch, final ResourceKey<T> key) {
        return entityPatch.getLevel().registryAccess().get(key).map(Holder.Reference::value).orElse(null);
    }

    /// The driver running `animation`, or null when no layer of the entity plays it. Each layer is
    /// checked at its base driver and at its montage player.
    ///
    /// What comes back is the driver that *holds* the animation, not the one the layer points at: a
    /// layer's base driver is routinely a state machine or a pose reference standing in front of it.
    public static @Nullable AnimationDriver findDriverPlaying(final EntityPatch<?> entityPatch, final PlayableAnimation animation) {
        ArmatureComponent armatureComponent = entityPatch.getArmatureComponent();

        if (armatureComponent == null || !(armatureComponent.getAnimator() instanceof BlendingAnimator blendingAnimator)) {
            return null;
        }

        // Both engine classes are final, so the mixin-added accessor interfaces are only reachable
        // through Object — javac rejects the direct cast.
        BlendingAnimatorAccessor animatorAccessor = (BlendingAnimatorAccessor)(Object)blendingAnimator;

        for (AnimationLayer layer : animatorAccessor.akytheralabs$getAnimLayers().values()) {
            AnimationDriver driver = ((AnimationLayerAccessor)(Object)layer).akytheralabs$getAnimDriver();

            // `getPlayingAnimation` already reports through the stand-ins, so the match is made on the
            // layer's driver and only the winner is stepped through.
            if (driver.getPlayingAnimation() == animation) {
                return unwrap(driver);
            }

            if (layer.montagePlayer().getPlayingAnimation() == animation) {
                return layer.montagePlayer();
            }
        }

        return null;
    }

    /// The data-driven animator script the entity runs, or null when it has no armature, no blending
    /// animator, or an animator driven by a hardcoded script.
    public static @Nullable DataDrivenAnimatorScript dataDrivenScript(final EntityPatch<?> entityPatch) {
        ArmatureComponent armatureComponent = entityPatch.getArmatureComponent();

        if (armatureComponent == null || !(armatureComponent.getAnimator() instanceof BlendingAnimator blendingAnimator)) {
            return null;
        }

        AnimatorScript animatorScript = ((BlendingAnimatorAccessor)(Object)blendingAnimator).akytheralabs$getAnimScript();

        return animatorScript instanceof DataDrivenAnimatorScript dataDrivenAnimatorScript
            ? dataDrivenAnimatorScript
            : null;
    }

    /// The [FSMAnimationState] ids every [AnimationStateMachine] the entity's layers reach is
    /// currently in, in the `namespace:path` form the Anim Graph names its state nodes with.
    ///
    /// Only the machines standing at the head of a layer (behind any number of pose references) and
    /// the machines nested in those machines' active states are reached — a machine buried inside a
    /// blend driver is invisible from here, since drivers expose no children.
    public static Set<String> activeStateIds(final EntityPatch<?> entityPatch) {
        ArmatureComponent armatureComponent = entityPatch.getArmatureComponent();

        if (armatureComponent == null || !(armatureComponent.getAnimator() instanceof BlendingAnimator blendingAnimator)) {
            return Set.of();
        }

        Set<String> ids = new HashSet<>();
        BlendingAnimatorAccessor animatorAccessor = (BlendingAnimatorAccessor)(Object)blendingAnimator;

        for (AnimationLayer layer : animatorAccessor.akytheralabs$getAnimLayers().values()) {
            collectActiveStates(((AnimationLayerAccessor)(Object)layer).akytheralabs$getAnimDriver(), ids);
        }

        return ids;
    }

    /// A state with no identifier is skipped rather than named: the entity keeps running the machine it
    /// was built with until its patch is renewed, so between a reload dropping a state and that rebuild
    /// the machine holds a block the registry no longer knows. Nothing on a canvas can match it either.
    private static void collectActiveStates(final AnimationDriver driver, final Set<String> ids) {
        if (driver instanceof CachedPoseReference poseReference) {
            collectActiveStates(((CachedPoseReferenceAccessor)(Object)poseReference).akytheralabs$ref(), ids);
            return;
        }

        if (driver instanceof AnimationStateMachine<?> stateMachine) {
            Identifier state = stateMachine.getState().identifier();

            if (state != null) ids.add(state.toString());

            collectActiveStates(
                ((AnimationStateMachineAccessor)(Object)stateMachine).akytheralabs$getCurrentStateDriver(), ids);
        }
    }

    /// Steps through the drivers that only forward to another one, down to the driver that holds the
    /// animation itself.
    private static AnimationDriver unwrap(final AnimationDriver driver) {
        if (driver instanceof AnimationStateMachine<?> stateMachine) {
            return unwrap(((AnimationStateMachineAccessor)(Object)stateMachine).akytheralabs$getCurrentStateDriver());
        }

        if (driver instanceof CachedPoseReference poseReference) {
            return unwrap(((CachedPoseReferenceAccessor)(Object)poseReference).akytheralabs$ref());
        }

        return driver;
    }
}
