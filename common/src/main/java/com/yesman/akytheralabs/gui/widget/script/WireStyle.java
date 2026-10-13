package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Arrays;

/// How a wire runs from an output pin to an input pin on a [ScriptNodeGraphWidget]. Every style answers a
/// polyline in screen space, which the canvas both draws and hit-tests.
public enum WireStyle {
    /// A cubic Bézier curve leaving and entering its pins horizontally.
    BEZIER(LangKeys.GUI_EDITOR_PREFERENCES_WIRE_STYLE_BEZIER) {
        @Override
        public float[] path(float x0, float y0, float x1, float y1, float zoom) {
            float handle = Math.max(HANDLE_MIN * zoom, Math.abs(x1 - x0) * 0.5F);
            float cx0 = x0 + handle;
            float cx1 = x1 - handle;
            float length = Math.abs(cx0 - x0) + (float) Math.hypot(cx1 - cx0, y1 - y0) + Math.abs(x1 - cx1);
            int segments = Math.clamp((int) Math.ceil(length / CURVE_STEP), 8, 96);
            float[] points = new float[(segments + 1) * 2];

            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                float mt = 1.0F - t;

                points[i * 2] = mt * mt * mt * x0 + 3 * mt * mt * t * cx0 + 3 * mt * t * t * cx1 + t * t * t * x1;
                points[i * 2 + 1] = mt * mt * mt * y0 + 3 * mt * mt * t * y0 + 3 * mt * t * t * y1 + t * t * t * y1;
            }

            return points;
        }
    },

    /// Horizontal and vertical runs meeting at rounded right angles.
    MANHATTAN(LangKeys.GUI_EDITOR_PREFERENCES_WIRE_STYLE_MANHATTAN) {
        @Override
        public float[] path(float x0, float y0, float x1, float y1, float zoom) {
            float[] corners = rightAngleRoute(x0, y0, x1, y1, zoom);
            FloatPath path = new FloatPath(corners.length * 4);

            path.add(corners[0], corners[1]);

            for (int i = 2; i < corners.length - 2; i += 2) {
                float px = corners[i];
                float py = corners[i + 1];
                float radius = Math.min(CORNER_RADIUS * zoom, cornerRoom(corners, i));
                float in = cutRatio(corners, i, i - 2, radius);
                float out = cutRatio(corners, i, i + 2, radius);

                // A quadratic curve from the cut point on the incoming run to the one on the outgoing run.
                float ax = px + (corners[i - 2] - px) * in;
                float ay = py + (corners[i - 1] - py) * in;
                float bx = px + (corners[i + 2] - px) * out;
                float by = py + (corners[i + 3] - py) * out;

                for (int step = 0; step <= CORNER_STEPS; step++) {
                    float t = step / (float) CORNER_STEPS;
                    float mt = 1.0F - t;

                    path.add(mt * mt * ax + 2 * mt * t * px + t * t * bx, mt * mt * ay + 2 * mt * t * py + t * t * by);
                }
            }

            path.add(corners[corners.length - 2], corners[corners.length - 1]);

            return path.toArray();
        }
    },

    /// Horizontal runs joined by 45° diagonals, cut from the corners of the [#MANHATTAN] route.
    SUBWAY(LangKeys.GUI_EDITOR_PREFERENCES_WIRE_STYLE_SUBWAY) {
        @Override
        public float[] path(float x0, float y0, float x1, float y1, float zoom) {
            float[] corners = rightAngleRoute(x0, y0, x1, y1, zoom);
            FloatPath path = new FloatPath(corners.length * 2);

            path.add(corners[0], corners[1]);

            for (int i = 2; i < corners.length - 2; i += 2) {
                float px = corners[i];
                float py = corners[i + 1];
                float cut = cornerRoom(corners, i);
                float in = cutRatio(corners, i, i - 2, cut);
                float out = cutRatio(corners, i, i + 2, cut);

                path.add(px + (corners[i - 2] - px) * in, py + (corners[i - 1] - py) * in);
                path.add(px + (corners[i + 2] - px) * out, py + (corners[i + 3] - py) * out);
            }

            path.add(corners[corners.length - 2], corners[corners.length - 1]);

            return path.toArray();
        }
    };

    /// Shortest Bézier handle, in graph pixels.
    private static final float HANDLE_MIN = 40.0F;

    /// Screen length of one sampled step of a Bézier curve.
    private static final float CURVE_STEP = 5.0F;

    /// How far a right-angle route runs straight out of a pin before it may turn, in graph pixels.
    private static final float STUB = 20.0F;

    /// Largest radius of a [#MANHATTAN] corner, in graph pixels.
    private static final float CORNER_RADIUS = 8.0F;

    /// Sampled steps of one rounded corner.
    private static final int CORNER_STEPS = 5;

    private final String langKey;

    WireStyle(String langKey) {
        this.langKey = langKey;
    }

    public String langKey() {
        return langKey;
    }

    /// The wire from `(x0, y0)` to `(x1, y1)` as `x, y` pairs, screen space. `zoom` scales the graph-space
    /// measures of the style.
    public abstract float[] path(float x0, float y0, float x1, float y1, float zoom);

    /// The corners of a route made of horizontal and vertical runs, as `x, y` pairs: out of the source
    /// pin to the right, into the target pin from the left. A target behind the source is reached around
    /// through the height between the two pins.
    private static float[] rightAngleRoute(float x0, float y0, float x1, float y1, float zoom) {
        float stub = STUB * zoom;

        if (x1 - x0 >= stub * 2) {
            float midX = (x0 + x1) * 0.5F;

            return new float[] {x0, y0, midX, y0, midX, y1, x1, y1};
        }

        float midY = (y0 + y1) * 0.5F;

        return new float[] {x0, y0, x0 + stub, y0, x0 + stub, midY, x1 - stub, midY, x1 - stub, y1, x1, y1};
    }

    /// How far the corner at pair index `i` may be cut back: half of its shorter neighboring run, so two
    /// cut corners never cross.
    private static float cornerRoom(float[] corners, int i) {
        return Math.min(runLength(corners, i, i - 2), runLength(corners, i, i + 2)) * 0.5F;
    }

    /// The fraction of the run from pair `i` to pair `j` that `distance` covers.
    private static float cutRatio(float[] corners, int i, int j, float distance) {
        float length = runLength(corners, i, j);

        return length == 0.0F ? 0.0F : distance / length;
    }

    private static float runLength(float[] corners, int i, int j) {
        return (float) Math.hypot(corners[j] - corners[i], corners[j + 1] - corners[i + 1]);
    }

    /// A growable list of `x, y` pairs.
    private static final class FloatPath {
        private float[] points;
        private int size;

        private FloatPath(int capacity) {
            this.points = new float[Math.max(capacity, 4)];
        }

        private void add(float x, float y) {
            if (size + 2 > points.length) points = Arrays.copyOf(points, points.length * 2);

            points[size++] = x;
            points[size++] = y;
        }

        private float[] toArray() {
            return Arrays.copyOf(points, size);
        }
    }
}
