package com.yesman.akytheralabs.fabric;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.debug.EditorLogBuffer;
import com.yesman.akytheralabs.editor.debug.ScriptFailureChat;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsCheckBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsComboBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.script.node.ScriptNodeTypes;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.fml.config.ModConfig;

public final class AkytheraLabsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EditorLogBuffer.getInstance().install();
        ScriptFailureChat.install();

        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsButtonTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsEditBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsComboBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsCheckBoxTheme.class);

        AkytheraLabs.bootstrapDataHolderMappings();
        ScriptNodeTypes.bootstrap();
        AkytheraProject.registerEvents();
        AkytheraEditorLayoutManager.registerEvents();

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
