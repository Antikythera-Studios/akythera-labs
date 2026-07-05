package com.yesman.akytheralabs.data;

import com.yesman.akythera.api.data.provider.BlendSpace2DProvider;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@NullMarked
public final class AkytheraSampleBlendSpace2Ds extends BlendSpace2DProvider {
    static ResourceKey<BlendSpace2D> createId(final String name) {
        return ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, AkytheraLabs.samplePack(name));
    }

    public static final ResourceKey<BlendSpace2D> HUMANOID_WALK_RUN = createId("humanoid/walk_run");

    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public AkytheraSampleBlendSpace2Ds(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput);

        this.lookupProvider = lookupProvider;
    }

    @Override
    protected void buildBlendSpace2Ds(BiConsumer<ResourceKey<BlendSpace2D>, BlendSpace2D> writer) {
        HolderLookup.RegistryLookup<AnimationSequence> registryLookup = lookupProvider.join().lookupOrThrow(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY);
        
        writer.accept(
            HUMANOID_WALK_RUN,
            BlendSpace2D
                .builder()
                    // 4.317181F == average walk speed, 5.6123357 == average run speed
                    .minX(-180.0F).maxX(180.0F).minY(0.0F).maxY(4.317181F).loops(true).smoothY(50.0F)
                    .putNode(-180.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(-135.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(-90.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(-45.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(0.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(45.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(90.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(135.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(180.0F, 0.0F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_IDLE))
                    .putNode(-180.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_BACKWARD))
                    .putNode(-135.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_BACKWARD_RIGHT))
                    .putNode(-90.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_RIGHT))
                    .putNode(-45.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_FORWARD_RIGHT))
                    .putNode(0.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_FORWARD))
                    .putNode(45.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_FORWARD_LEFT))
                    .putNode(90.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_LEFT))
                    .putNode(135.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_BACKWARD_LEFT))
                    .putNode(180.0F, 4.317181F, registryLookup.getOrThrow(AkytheraSampleAnimationSequences.HUMANOID_WALK_BACKWARD))
                .create()
        );
    }
}
