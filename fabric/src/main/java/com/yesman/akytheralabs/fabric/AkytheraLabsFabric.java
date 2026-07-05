package com.yesman.akytheralabs.fabric;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsComboBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.fml.config.ModConfig;

public final class AkytheraLabsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsButtonTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsEditBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsComboBoxTheme.class);

        AkytheraLabs.bootstrapDataHolderMappings();

        ModConfigEvents.loading(AkytheraLabs.MODID).register(modConfig -> {
            if (modConfig.getType() == ModConfig.Type.CLIENT) {
                AkytheraLabsConfig.initConfigValues();
            }
        });
        ModConfigEvents.reloading(AkytheraLabs.MODID).register(modConfig -> {
            if (modConfig.getType() == ModConfig.Type.CLIENT) {
                AkytheraLabsConfig.initConfigValues();
            }
        });

        ConfigRegistry.INSTANCE.register(AkytheraLabs.MODID, ModConfig.Type.CLIENT, AkytheraLabsConfig.SPEC);
    }
}
