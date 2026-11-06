package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akytheralabs.editor.historytrack.HistoryManager;

/// The frame axis a track panel lays its rows along, at 60 frames per second: the playhead, the visible window, and
/// the mapping between frames and screen x. Read by [CurrentFrameIndicator] and [FrameScrollBar].
public interface TrackTimeline {
    float currentFrame();

    void setCurrentFrame(float frame);

    /// Moves the playhead to `frame` as the user's own move: one [PlayheadMove] undo step when it lands elsewhere.
    default void scrubTo(float frame) {
        float from = currentFrame();

        setCurrentFrame(frame);

        if (currentFrame() != from) HistoryManager.getInstance().pushHistory(new PlayheadMove(this, from, currentFrame()));
    }

    /// The text over the playhead's box.
    String playheadLabel();

    /// Whether the playhead lies inside the visible window.
    default boolean isCurrentFrameInScope() {
        return currentFrame() >= visualFrameStart() && currentFrame() <= visualFrameEnd();
    }

    /// Whether something other than the user moves the playhead, which locks scrubbing.
    default boolean isDebugDriven() {
        return false;
    }

    /// Screen x of `frame`.
    int frameToX(float frame);

    /// The frame at screen x `x`.
    float xToFrame(double x);

    float visualFrameStart();

    float visualFrameEnd();

    void setVisualFrameStart(float frame);

    void setVisualFrameEnd(float frame);

    /// The length [FrameScrollBar] spans, in frames. Zero when there is nothing to span.
    float timelineFrames();

    /// The frames between two ruler marks: the first of 5, 10, 25, 50, 100, 200, 400… that keeps at most 20 marks over
    /// `frameScope` frames.
    static int rulerStride(final float frameScope) {
        int[] scales = { 5, 10, 25, 50, 100 };
        int stride;
        int i = 0;

        do {
            stride = i < scales.length ? scales[i] : 100 << (i - scales.length + 1);
            i++;
        } while (frameScope / stride > 20);

        return stride;
    }
}
