package com.yesman.akytheralabs.neoforged;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(value = AkytheraLabs.MODID, dist = Dist.CLIENT)
public class AkytheraLabsNeoForged {
    public AkytheraLabsNeoForged(IEventBus modEventBus, ModContainer modContainer) {
        EditorLogBuffer.getInstance().install();
        ScriptFailureChat.install();

        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsButtonTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsEditBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsComboBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, AkytheraLabsCheckBoxTheme.class);

        modContainer.registerConfig(ModConfig.Type.CLIENT, AkytheraLabsConfig.SPEC);

        modEventBus.addListener(this::doCommonStuff);
        modEventBus.addListener(this::modConfigLoadingEvent);
    }

    private void doCommonStuff(final FMLCommonSetupEvent event) {
        // Bootstrap data holder map after mod platform is loaded
        AkytheraLabs.bootstrapDataHolderMappings();
        ScriptNodeTypes.bootstrap();
        AkytheraProject.registerEvents();
        AkytheraEditorLayoutManager.registerEvents();
    }

    private void modConfigLoadingEvent(final ModConfigEvent.Loading event) {
        // Drop if not a client config event
        if (!ModConfig.Type.CLIENT.equals(event.getConfig().getType())) return;

        AkytheraLabsConfig.initConfigValues();
    }
}
