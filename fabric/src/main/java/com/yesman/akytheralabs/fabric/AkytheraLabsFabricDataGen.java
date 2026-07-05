package com.yesman.akytheralabs.fabric;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;

public final class AkytheraLabsFabricDataGen implements DataGeneratorEntrypoint {
    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        //registryBuilder.add(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AkytheraSampleAnimationSequences::bootstrap);
    }

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        //pack.addProvider((FabricDataGenerator.Pack.RegistryDependentFactory<@NonNull AnimationSequenceProvider>) AkytheraSampleAnimationSequences::new);
        //pack.addProvider((FabricDataGenerator.Pack.RegistryDependentFactory<@NonNull BlendSpace2DProvider>) AkytheraSampleBlendSpace2Ds::new);
        //pack.addProvider((DataProvider.Factory<@NonNull AnimationMontageProvider>) AkytheraSampleAnimationMontages::new);
        //pack.addProvider((DataProvider.Factory<@NonNull AnimatorScriptProvider>) AkytheraSampleAnimatorScripts::new);
    }
}
