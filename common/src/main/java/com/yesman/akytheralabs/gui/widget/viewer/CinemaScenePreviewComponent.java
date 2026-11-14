package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akytheralabs.akythera.cinema.CinemaSceneHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// The playhead of a [CinemaSceneHolder]'s preview in [Model3DViewer], in seconds since the scene started. It draws
/// nothing: each [CinemaActorComponent] poses and places its actor at [#time].
///
/// The playhead stays between zero and [#end]. Nothing plays until [#play], which starts over after a play that ran to
/// the end.
public final class CinemaScenePreviewComponent implements Model3DViewerComponent {
    /// Seconds per tick, the unit a frame's time comes in.
    private static final float TICK_SECONDS = 0.05F;

    private final CinemaSceneHolder holder;

    private boolean playing;

    /// The playhead, in seconds.
    private float time;

    public CinemaScenePreviewComponent(final CinemaSceneHolder holder) {
        this.holder = holder;
    }

    /// Where the preview ends, in seconds: the longest montage's end, or the scene's own when that is later.
    public float end() {
        return Math.max(holder.getLength(), holder.getDuration());
    }

    public void play() {
        if (time >= end()) time = 0.0F;

        playing = true;
    }

    public void pause() {
        playing = false;
    }

    /// Pauses at the scene's start.
    public void stop() {
        playing = false;
        time = 0.0F;
    }

    /// Pauses at `seconds`.
    public void seek(final float seconds) {
        playing = false;
        time = Math.clamp(seconds, 0.0F, end());
    }

    public float time() {
        return time;
    }

    public boolean isPlaying() {
        return playing;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        if (!playing) return;

        time += a * TICK_SECONDS;

        if (time >= end()) {
            time = end();
            playing = false;
        }
    }

    @Override
    public int drawOrder() {
        return 2; // before the actors (5), which read the playhead
    }
}
