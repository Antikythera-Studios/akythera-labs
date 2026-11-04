package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorFieldScanner;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// Shows an animation editor's notify problems as [Model3DViewer] debug messages. Draws nothing.
///
/// - Each [EditorEditable] field of a notify or a notify state naming an entry that can't be found, such as a sound.
/// - With a model shown, each [AnimationNotifyStateHolder#socketPath] naming a socket the viewer's
///   [PreviewPatchComponent] can't resolve, and an empty one once the notify state is saved.
public final class NotifyDiagnosticsComponent implements Model3DViewerComponent {
    private final Supplier<NotifyTrackHolder> notifyTracks;

    public NotifyDiagnosticsComponent(final Supplier<NotifyTrackHolder> notifyTracks) {
        this.notifyTracks = notifyTracks;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        boolean hasModel = model3DViewer.getComponent(EntityModelComponent.class) != null;
        PreviewPatchComponent patch = model3DViewer.getComponent(PreviewPatchComponent.class);
        List<Component> messages = new ArrayList<>();

        for (NotifyTrackHolder.Track track : notifyTracks.get().getTracks()) {
            for (NotifyTrackHolder.MutableNotify notify : track.notifies) {
                addUnresolvedReferences(notify.holder, notify.holder.notifyName(), messages);
            }

            for (NotifyTrackHolder.MutableNotifyState state : track.notifyStates) {
                addUnresolvedReferences(state.holder, state.holder.notifyName(), messages);

                String socket = state.holder.socketPath();

                if (!hasModel || socket == null) continue;

                if (socket.isEmpty()) {
                    if (state.holder.isSaved()) messages.add(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SOCKET_EMPTY, state.holder.notifyName()));
                } else if (patch == null || patch.socketTransform(model3DViewer, socket) == null) {
                    messages.add(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SOCKET_NOT_FOUND, socket, state.holder.notifyName()));
                }
            }
        }

        model3DViewer.setDebugMessages(this, messages);
    }

    /// Adds a message for each [EditorEditable] field of `holder` naming an entry neither the open project nor the
    /// loaded game has, as [EditorWidgetFactory#resolves] tells.
    private static void addUnresolvedReferences(final Object holder, final String notifyName, final List<Component> messages) {
        for (EditorFieldScanner.EditorField editorField : EditorFieldScanner.fieldsOf(holder.getClass())) {
            Object value;

            try {
                value = editorField.field().get(holder);
            } catch (IllegalAccessException e) {
                continue;
            }

            if (EditorWidgetFactory.resolves(value)) continue;

            Object name = switch (value) {
                case AkytheraProject.EditorSoundEventAccessor sound -> sound.location();
                case Holder<?> reference -> EditorHolderCodecs.entryId(reference);
                case ResourceKey<?> key -> key.identifier();
                case null, default -> value;
            };

            messages.add(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_REFERENCE_NOT_FOUND, Component.translatable(editorField.label()), name, notifyName));
        }
    }

    @Override
    public int drawOrder() {
        return 0; // draws nothing
    }
}
