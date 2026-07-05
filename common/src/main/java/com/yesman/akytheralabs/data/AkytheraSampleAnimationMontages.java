package com.yesman.akytheralabs.data;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.data.provider.AnimationMontageProvider;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiConsumer;

@NullMarked
public final class AkytheraSampleAnimationMontages extends AnimationMontageProvider {
    static ResourceKey<AnimationMontage> createId(final String name) {
        return ResourceKey.create(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AkytheraLabs.samplePack(name));
    }

    public static final ResourceKey<AnimationMontage> HUMANOID_SWORD_COMBO1 = createId("humanoid/sword_combo1");
    public static final ResourceKey<AnimationMontage> HUMANOID_SWORD_COMBO2 = createId("humanoid/sword_combo2");
    public static final ResourceKey<AnimationMontage> HUMANOID_SWORD_COMBO3 = createId("humanoid/sword_combo3");
    public static final ResourceKey<AnimationMontage> HUMANOID_SWORD_DASH_ATTACK = createId("humanoid/sword_dash_attack");
    public static final ResourceKey<AnimationMontage> HUMANOID_SWORD_JUMP_ATTACK = createId("humanoid/sword_jump_attack");

    public static final ResourceKey<AnimationMontage> HUMANOID_STEEL_WHIRLWIND = createId("humanoid/steel_whirlwind");

    public AkytheraSampleAnimationMontages(PackOutput packOutput) {
        super(packOutput);
    }

    @Override
    protected void buildAnimMontages(BiConsumer<ResourceKey<AnimationMontage>, AnimationMontage> writer) {
        writer.accept(
            HUMANOID_SWORD_COMBO1,
            AnimationMontage.builder()
                .addAnimSegment(AnimationManager.accessor(AkytheraLabs.samplePack("montage/sword_combo1")))
                .fadeInTime(0.15F)
                .rootMotionEnabled(true)
            .create()
        );
        writer.accept(
            HUMANOID_STEEL_WHIRLWIND,
            AnimationMontage.builder()
                .addAnimSegment(AnimationManager.accessor(AkytheraLabs.samplePack("montage/steel_whirlwind_charging")))
                .addAnimSegment(AnimationManager.accessor(AkytheraLabs.samplePack("montage/steel_whirlwind_loop")))
                .addAnimSegment(AnimationManager.accessor(AkytheraLabs.samplePack("montage/steel_whirlwind_attack")))
                .rootMotionEnabled(true)
                .createMontageSectionAt("charging", 0.0F)
                .createMontageSectionAt("loop", 0.6667F)
                .createMontageSectionAt("attack", 1.3334F)
                .addSectionPlayingOrder("charging", "loop", "loop")
                .create()
        );
    }
}
