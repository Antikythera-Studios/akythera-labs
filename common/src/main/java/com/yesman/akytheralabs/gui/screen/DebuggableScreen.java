package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.widget.main.EntityDebugModal;
import com.yesman.akytheralabs.gui.widget.main.EntitySelectModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

/// An editor screen that mirrors a live entity while the user possesses it through [EntityDebugModal].
public interface DebuggableScreen {
    /// Entities this screen can mirror. [EntitySelectModal] lists only what this accepts.
    Predicate<Entity> debugTargetFilter();

    /// Called once per client tick with the patch of the possessed entity.
    void onDebugTick(EntityPatch<?> entityPatch);

    /// Whether the scripts this screen mirrors run on the server too. [EntityDebugModal] then offers a
    /// switch between mirroring the client's runs and the server's.
    default boolean mirrorsServer() {
        return false;
    }

    /// Opens the picker for the editor screen on top, filtered by that screen when it is a
    /// [DebuggableScreen], and hands the pick to [EntityDebugModal].
    static void openDebugSelector(final Font font) {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        Predicate<Entity> filter = screen instanceof DebuggableScreen debuggable
            ? debuggable.debugTargetFilter()
            : _ -> true;

        EntitySelectModal.open(font, filter, entity -> EntityDebugModal.open(font, entity));
    }
}
