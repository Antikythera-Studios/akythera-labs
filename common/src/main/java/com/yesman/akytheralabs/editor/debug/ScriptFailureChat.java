package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/// Posts each script failure [ScriptRecorder] reports to chat, next to the engine's log line.
public final class ScriptFailureChat {
    private ScriptFailureChat() {}

    /// Installs the failure listener. Called once from each platform's client init.
    public static void install() {
        ScriptRecorder.setFailureListener(failure -> {
            ScriptCaller caller = failure.caller();
            String where = (caller.blueprint() == null ? "?" : caller.blueprint().identifier()) + "; " + caller.site() + ";";
            Minecraft minecraft = Minecraft.getInstance();

            // The listener runs on whichever thread the script ran on
            minecraft.execute(() -> minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.translatable(LangKeys.GUI_SCRIPT_ERROR_CHAT, where).withStyle(ChatFormatting.RED)));
        });
    }
}
