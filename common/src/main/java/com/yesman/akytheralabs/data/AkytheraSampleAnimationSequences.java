package com.yesman.akytheralabs.data;

import com.yesman.akythera.api.animation.notify.impl.LogNotify;
import com.yesman.akythera.api.animation.notify.impl.LogNotifyState;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.data.provider.AnimationSequenceProvider;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@NullMarked
public final class AkytheraSampleAnimationSequences extends AnimationSequenceProvider {
    static ResourceKey<AnimationSequence> createId(final String name) {
        return ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AkytheraLabs.samplePack(name));
    }

    public static final ResourceKey<AnimationSequence> HUMANOID_IDLE = createId("humanoid/idle");
    public static final ResourceKey<AnimationSequence> HUMANOID_IDLE_AIR = createId("humanoid/idle_air");
    public static final ResourceKey<AnimationSequence> HUMANOID_IDLE_WATER = createId("humanoid/idle_water");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_FORWARD = createId("humanoid/walk_forward");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_FORWARD_LEFT = createId("humanoid/walk_forward_left");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_FORWARD_RIGHT = createId("humanoid/walk_forward_right");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_LEFT = createId("humanoid/walk_left");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_RIGHT = createId("humanoid/walk_right");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_BACKWARD = createId("humanoid/walk_backward");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_BACKWARD_LEFT = createId("humanoid/walk_backward_left");
    public static final ResourceKey<AnimationSequence> HUMANOID_WALK_BACKWARD_RIGHT = createId("humanoid/walk_backward_right");

    public static void bootstrap(BootstrapContext<AnimationSequence> context) {
        context.register(
            HUMANOID_IDLE,
            new AnimationSequence(
                new AnimationNotifyTrack(
                    List.of(new AnimationNotifyTrack.NotifyInTimeStamp(new LogNotify(LogicalSide.BOTH), 0.2F)),
                    List.of(new AnimationNotifyTrack.NotifyStateInTimeWindow(new LogNotifyState(LogicalSide.BOTH), TimeWindow.create(0.0F, 0.2F)))
                ),
                AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/idle")),
                PlayableAnimation.DEFAULT_FADE_TIME, PlayableAnimation.DEFAULT_FADE_TIME, true, false
            )
        );
        context.register(
            HUMANOID_IDLE_AIR,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/idle_air")), true, false)
        );
        context.register(
            HUMANOID_IDLE_WATER,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/idle_water")), true, false)
        );
        context.register(
            HUMANOID_WALK_FORWARD,
            new AnimationSequence(
                new AnimationNotifyTrack(
                    List.of(new AnimationNotifyTrack.NotifyInTimeStamp(new LogNotify(LogicalSide.BOTH), 0.2F)),
                    List.of(new AnimationNotifyTrack.NotifyStateInTimeWindow(new LogNotifyState(LogicalSide.BOTH), TimeWindow.create(0.0F, 0.2F)))
                ),
                AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_forward")),
                PlayableAnimation.DEFAULT_FADE_TIME, PlayableAnimation.DEFAULT_FADE_TIME, true, false)
        );
        context.register(
            HUMANOID_WALK_FORWARD_LEFT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_forward_left")), true, false)
        );
        context.register(
            HUMANOID_WALK_FORWARD_RIGHT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_forward_right")), true, false)
        );
        context.register(
            HUMANOID_WALK_LEFT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_left")), true, false)
        );
        context.register(
            HUMANOID_WALK_RIGHT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_right")), true, false)
        );
        context.register(
            HUMANOID_WALK_BACKWARD,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_backward")), true, false)
        );
        context.register(
            HUMANOID_WALK_BACKWARD_LEFT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_backward_left")), true, false)
        );
        context.register(
            HUMANOID_WALK_BACKWARD_RIGHT,
            new AnimationSequence(AnimationManager.accessor(AkytheraLabs.samplePack("humanoid/locomotion/walk_backward_right")), true, false)
        );
    }

    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public AkytheraSampleAnimationSequences(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput);

        this.lookupProvider = lookupProvider;
    }

    @Override
    protected void buildAnimSequences(BiConsumer<ResourceKey<AnimationSequence>, AnimationSequence> writer) {
        HolderLookup.RegistryLookup<AnimationSequence> registryLookup = lookupProvider.join().lookupOrThrow(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY);

        writer.accept(HUMANOID_IDLE, registryLookup.getOrThrow(HUMANOID_IDLE).value());
        writer.accept(HUMANOID_IDLE_AIR, registryLookup.getOrThrow(HUMANOID_IDLE_AIR).value());
        writer.accept(HUMANOID_IDLE_WATER, registryLookup.getOrThrow(HUMANOID_IDLE_WATER).value());
        writer.accept(HUMANOID_WALK_FORWARD, registryLookup.getOrThrow(HUMANOID_WALK_FORWARD).value());
        writer.accept(HUMANOID_WALK_FORWARD_LEFT, registryLookup.getOrThrow(HUMANOID_WALK_FORWARD_LEFT).value());
        writer.accept(HUMANOID_WALK_FORWARD_RIGHT, registryLookup.getOrThrow(HUMANOID_WALK_FORWARD_RIGHT).value());
        writer.accept(HUMANOID_WALK_LEFT, registryLookup.getOrThrow(HUMANOID_WALK_LEFT).value());
        writer.accept(HUMANOID_WALK_RIGHT, registryLookup.getOrThrow(HUMANOID_WALK_RIGHT).value());
        writer.accept(HUMANOID_WALK_BACKWARD, registryLookup.getOrThrow(HUMANOID_WALK_BACKWARD).value());
        writer.accept(HUMANOID_WALK_BACKWARD_LEFT, registryLookup.getOrThrow(HUMANOID_WALK_BACKWARD_LEFT).value());
        writer.accept(HUMANOID_WALK_BACKWARD_RIGHT, registryLookup.getOrThrow(HUMANOID_WALK_BACKWARD_RIGHT).value());
    }
}
