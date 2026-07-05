package com.yesman.akytheralabs.dataholder.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Modifiable data holder for [BlendSpace2D].
///
/// Hand-rolled codec — does not reuse `BlendSpace2D.MAP_CODEC`, because the engine codec routes
/// animation node references through `PlayableAnimation.CODEC` → `RegistryFileCodec`, which yields
/// engine `Holder`s. Instead each [MutableAnimNode] holds an [AnimationRef] whose `toEngineHolder`
/// is the referenced entry's shared `ProjectObjectHolder` (so playback reads the registry's
/// last-saved object); [#referencedReferences] drives the on-disk re-save on a referenced rename.
public final class BlendSpace2DHolder extends AbstractDataHolder<BlendSpace2D> implements DTOProvider {
    public static final Codec<BlendSpace2DHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.FLOAT.fieldOf("fade_in_time").forGetter(BlendSpace2DHolder::getFadeInTime),
            Codec.FLOAT.fieldOf("fade_out_time").forGetter(BlendSpace2DHolder::getFadeOutTime),
            Codec.FLOAT.fieldOf("smooth_x").forGetter(BlendSpace2DHolder::getSmoothX),
            Codec.FLOAT.fieldOf("smooth_y").forGetter(BlendSpace2DHolder::getSmoothY),
            Codec.FLOAT.fieldOf("min_x").forGetter(BlendSpace2DHolder::getMinX),
            Codec.FLOAT.fieldOf("min_y").forGetter(BlendSpace2DHolder::getMinY),
            Codec.FLOAT.fieldOf("max_x").forGetter(BlendSpace2DHolder::getMaxX),
            Codec.FLOAT.fieldOf("max_y").forGetter(BlendSpace2DHolder::getMaxY),
            Codec.BOOL.fieldOf("loops").forGetter(BlendSpace2DHolder::isLoops),
            NodeData.CODEC.listOf().fieldOf("nodes").forGetter(BlendSpace2DHolder::nodeData),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(BlendSpace2DHolder::getPreviewModel)),
            Codec.INT.optionalFieldOf("horizontal_division", 8)
                .forGetter(BlendSpace2DHolder::getHorizontalDivision),
            Codec.INT.optionalFieldOf("vertical_division", 8)
                .forGetter(BlendSpace2DHolder::getVerticalDivision),
            Codec.BOOL.optionalFieldOf("snap_to_grid", false)
                .forGetter(BlendSpace2DHolder::isSnapToGrid),
            AxisNames.CODEC.optionalFieldOf("axis_names", AxisNames.DEFAULT)
                .forGetter(h -> new AxisNames(h.axisNameX, h.axisNameY))
        ).apply(instance, (fadeIn, fadeOut, sx, sy, mnX, mnY, mxX, mxY, lp, ns, prevModel, hDiv, vDiv, snap, axisNames) -> {
            BlendSpace2DHolder h = new BlendSpace2DHolder();
            h.fadeInTime = fadeIn;
            h.fadeOutTime = fadeOut;
            h.smoothX = sx;
            h.smoothY = sy;
            h.minX = mnX;
            h.minY = mnY;
            h.maxX = mxX;
            h.maxY = mxY;
            h.loops = lp;
            h.horizontalDivision = hDiv;
            h.verticalDivision = vDiv;
            h.axisNameX = axisNames.x();
            h.axisNameY = axisNames.y();

            for (NodeData n : ns) {
                MutableAnimNode mn = h.new MutableAnimNode(n.x(), n.y(), -1, n.animation());
                h.nodes.add(mn);
            }

            h.rebuildDelaunay();
            prevModel.ifPresent(h::setPreviewModel);
            h.setSnapToGrid(snap);

            return h;
        })
    );

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_FADE_IN, min = 0F, order = 0)
    private float fadeInTime = 0.25F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_FADE_OUT, min = 0F, order = 1)
    private float fadeOutTime = 0.25F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_LOOPS, order = 2)
    private boolean loops = true;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_AXIS_NAME_X, order = 3)
    private String axisNameX = "X";

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_MIN_X, order = 4)
    private float minX = -180.0F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_MAX_X, order = 5)
    private float maxX = 180.0F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_SMOOTH_X, min = 0F, order = 6)
    private float smoothX;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_HORIZONTAL_DIVISION, min = 1, order = 7)
    private int horizontalDivision = 8;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_AXIS_NAME_Y, order = 8)
    private String axisNameY = "Y";

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_MIN_Y, order = 9)
    private float minY = 0.0F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_MAX_Y, order = 10)
    private float maxY = 4.317181F;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_SMOOTH_Y, min = 0F, order = 11)
    private float smoothY;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_VERTICAL_DIVISION, min = 1, order = 12)
    private int verticalDivision = 8;

    @EditorEditable(label = LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_SNAP_TO_GRID, order = 13)
    private boolean snapToGrid = true;

    @Nullable
    private EntityPreviewModel previewModel;

    private final List<MutableAnimNode> nodes = new ArrayList<>();

    /// rebuild every time a change is made
    private DelaunayTriangulation<MutableAnimNode> nodeSpace;

    /// Bumped on any change that alters the built [BlendSpace2D] (node add/remove, position, or
    /// animation assignment). The editor screen caches [#makeObject] against this and rebuilds on bump.
    private long structureVersion = 0;

    /// Editor-only: notified when the node graph changes in a way that affects root-motion presence —
    /// node add/remove or a node's animation assignment (incl. undo/redo via the graph widget's
    /// resync). The editor screen uses it to add/remove the root-motion trajectory preview. NOT fired
    /// from [#rebuildDelaunay] (which also runs every frame via [#makeObject]).
    @Nullable
    private Runnable onNodeGraphChanged;

    @Override
    public void readFrom(BlendSpace2D object) {
        // No callers today — engine state is never directly loaded into
        // the editor holder; the codec is the only build path.
    }

    @Override
    public BlendSpace2D makeObject() {
        rebuildDelaunay();

        BlendSpace2D.BlendSpace2DBuilder b =
            BlendSpace2D.builder()
                .fadeInTime(fadeInTime).fadeOutTime(fadeOutTime)
                .smoothX(smoothX).smoothY(smoothY)
                .loops(loops)
                .minX(nodeSpace.minX()).maxX(nodeSpace.maxX()).minY(nodeSpace.minY()).maxY(nodeSpace.maxY());

        for (MutableAnimNode n : nodeSpace.getAllPoints()) {
            b.putNode(n.x(), n.y(), n.animation().toEngineHolder());
        }

        return b.create();
    }

    /// Snapshot of node positions + animation refs for the codec.
    private List<NodeData> nodeData() {
        List<NodeData> out = new ArrayList<>(nodes.size());

        for (MutableAnimNode n : nodes) out.add(new NodeData(n.x(), n.y(), n.animation()));

        return out;
    }

    /// The project entries this blendspace references (one per non-empty node's animation), so a
    /// rename of a referenced sequence/montage/blendspace re-saves this blendspace's JSON. Each
    /// node's `AnimationRef#toEngineHolder` is the referenced entry's shared [ProjectObjectHolder]
    /// (an [EditorReference]); empty/unbound nodes degrade to the engine empty holder and are
    /// filtered out. Matched by identity in the rename scan.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return nodes.stream()
            .map(n -> n.animation().toEngineHolder())
            .filter(h -> h instanceof EditorReference)
            .map(h -> (EditorReference) h);
    }

    /// Supplies the tokens the `blend_space_2d_template.ftl` needs to rebuild this blend space: the fade
    /// times, smoothing and axis bounds as ready Java literals, the `loops` flag, the three
    /// `uses_*_lookup` flags telling the template which `HolderLookup.RegistryLookup`s to declare, and
    /// `nodes` — one row per node with its `x` / `y` literals and the full `animation` holder expression
    /// (a `…Lookup.getOrThrow(ResourceKey.create(…))` call, or `PlayableAnimation.EMPTY_ANIM_HOLDER`).
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("fade_in_time", SourceTemplateEngine.floatLiteral(fadeInTime));
        dto.put("fade_out_time", SourceTemplateEngine.floatLiteral(fadeOutTime));
        dto.put("smooth_x", SourceTemplateEngine.floatLiteral(smoothX));
        dto.put("smooth_y", SourceTemplateEngine.floatLiteral(smoothY));
        dto.put("min_x", SourceTemplateEngine.floatLiteral(minX));
        dto.put("min_y", SourceTemplateEngine.floatLiteral(minY));
        dto.put("max_x", SourceTemplateEngine.floatLiteral(maxX));
        dto.put("max_y", SourceTemplateEngine.floatLiteral(maxY));
        dto.put("loops", Boolean.toString(loops));

        boolean usesSequence = false;
        boolean usesMontage = false;
        boolean usesBlend = false;
        List<Map<String, Object>> nodeRows = new ArrayList<>(nodes.size());

        for (MutableAnimNode n : nodes) {
            NodeAnim na = resolveNodeAnim(n.animation());

            switch (na.kind()) {
                case SEQUENCE -> usesSequence = true;
                case MONTAGE -> usesMontage = true;
                case BLEND -> usesBlend = true;
                case NONE -> { }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("x", SourceTemplateEngine.floatLiteral(n.x()));
            row.put("y", SourceTemplateEngine.floatLiteral(n.y()));
            row.put("animation", na.expression());
            nodeRows.add(row);
        }

        dto.put("uses_sequence_lookup", usesSequence);
        dto.put("uses_montage_lookup", usesMontage);
        dto.put("uses_blend_lookup", usesBlend);
        dto.put("nodes", nodeRows);

        return dto;
    }

    /// One node's referenced animation rendered for codegen: the full holder [#expression] and which
    /// registry [#kind] it pulls from (so [#getDTO] can flag the matching lookup for declaration).
    private record NodeAnim(String expression, RefKind kind) { }

    private enum RefKind { NONE, SEQUENCE, MONTAGE, BLEND }

    /// Map a node's [AnimationRef] to its generated holder expression. Empty / unbound (dangling)
    /// references degrade to `PlayableAnimation.EMPTY_ANIM_HOLDER` so the generated provider still
    /// compiles and runs; bound references become a `…Lookup.getOrThrow(ResourceKey.create(…))` call.
    private static NodeAnim resolveNodeAnim(AnimationRef ref) {
        return switch (ref) {
            case AnimationRef.Empty ignored ->
                new NodeAnim("PlayableAnimation.EMPTY_ANIM_HOLDER", RefKind.NONE);
            case AnimationRef.Sequence s ->
                fromHolder(s.holder(), "animSequenceLookup", "AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY", RefKind.SEQUENCE);
            case AnimationRef.Montage m ->
                fromHolder(m.holder(), "animMontageLookup", "AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY", RefKind.MONTAGE);
            case AnimationRef.Blend b ->
                fromHolder(b.holder(), "blendSpace2DLookup", "AkytheraRegistries.DataPack.BLEND_SPACE_2D", RefKind.BLEND);
        };
    }

    private static NodeAnim fromHolder(Holder<?> holder, String lookupVar, String registryKey, RefKind kind) {
        Identifier id = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);

        if (id == null) {
            return new NodeAnim("PlayableAnimation.EMPTY_ANIM_HOLDER", RefKind.NONE);
        }

        String expression = lookupVar + ".getOrThrow(ResourceKey.create("
            + registryKey + ", Identifier.parse(\"" + id + "\")))";

        return new NodeAnim(expression, kind);
    }

    public boolean isLoops() {
        return loops;
    }

    public void setLoops(boolean loops) {
        this.loops = loops;
    }

    public float getSmoothY() {
        return smoothY;
    }

    public void setSmoothY(float smoothY) {
        this.smoothY = smoothY;
    }

    public float getSmoothX() {
        return smoothX;
    }

    public void setSmoothX(float smoothX) {
        this.smoothX = smoothX;
    }

    public float getFadeOutTime() {
        return fadeOutTime;
    }

    public void setFadeOutTime(float fadeOutTime) {
        this.fadeOutTime = fadeOutTime;
    }

    public float getFadeInTime() {
        return fadeInTime;
    }

    public void setFadeInTime(float fadeInTime) {
        this.fadeInTime = fadeInTime;
    }

    public float getMinX() {
        return minX;
    }

    public void setMinX(float minX) {
        this.minX = minX;

        rebuildDelaunay();
    }

    public float getMaxX() {
        return maxX;
    }

    public void setMaxX(float maxX) {
        this.maxX = maxX;

        rebuildDelaunay();
    }

    public float getMinY() {
        return minY;
    }

    public void setMinY(float minY) {
        this.minY = minY;

        rebuildDelaunay();
    }

    public float getMaxY() {
        return maxY;
    }

    public void setMaxY(float maxY) {
        this.maxY = maxY;

        rebuildDelaunay();
    }

    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        boolean turningOn = snapToGrid && !this.snapToGrid;
        this.snapToGrid = snapToGrid;

        if (turningOn) resnapAllNodes();
    }

    public String getAxisNameX() {
        return axisNameX;
    }

    public void setAxisNameX(String axisNameX) {
        this.axisNameX = axisNameX;
    }

    public String getAxisNameY() {
        return axisNameY;
    }

    public void setAxisNameY(String axisNameY) {
        this.axisNameY = axisNameY;
    }

    public int getHorizontalDivision() {
        return horizontalDivision;
    }

    public void setHorizontalDivision(int horizontalDivision) {
        this.horizontalDivision = Math.max(1, horizontalDivision);

        if (snapToGrid) resnapAllNodes();
    }

    public int getVerticalDivision() {
        return verticalDivision;
    }

    public void setVerticalDivision(int verticalDivision) {
        this.verticalDivision = Math.max(1, verticalDivision);

        if (snapToGrid) resnapAllNodes();
    }

    /// Nearest grid X for a blend-space value. Identity when snapping is off or the grid is degenerate.
    public float snapX(float x) {
        if (!snapToGrid || horizontalDivision <= 0 || maxX <= minX) return x;

        float cell = (maxX - minX) / horizontalDivision;

        return minX + Math.round((x - minX) / cell) * cell;
    }

    /// Nearest grid Y for a blend-space value. Identity when snapping is off or the grid is degenerate.
    public float snapY(float y) {
        if (!snapToGrid || verticalDivision <= 0 || maxY <= minY) return y;

        float cell = (maxY - minY) / verticalDivision;

        return minY + Math.round((y - minY) / cell) * cell;
    }

    /// Re-snap every node to the nearest grid point, rebuilding the triangulation once at the end.
    private void resnapAllNodes() {
        for (MutableAnimNode n : nodes) {
            n.x = Math.clamp(snapX(n.x), minX, maxX);
            n.y = Math.clamp(snapY(n.y), minY, maxY);
        }

        rebuildDelaunay();
    }

    public MutableAnimNode addNode(float x, float y) {
        MutableAnimNode node = new MutableAnimNode(
            Math.clamp(snapX(x), minX, maxX),
            Math.clamp(snapY(y), minY, maxY),
            -1, AnimationRef.EMPTY
        );

        nodes.add(node);
        rebuildDelaunay();
        notifyNodeGraphChanged();

        return node;
    }

    public void removeNode(int index) {
        MutableAnimNode toRemove = null;

        for (MutableAnimNode n : nodes) {
            if (n.index() == index) {
                toRemove = n;
                break;
            }
        }

        if (toRemove != null) {
            nodes.remove(toRemove);
            rebuildDelaunay();
            notifyNodeGraphChanged();
        }
    }

    public @Nullable MutableAnimNode getNode(int index) {
        for (MutableAnimNode n : nodes) {
            if (n.index() == index) {
                return n;
            }
        }

        return null;
    }

    /// Readonly: use edit methods above that calls [#rebuildDelaunay()] internally
    public List<MutableAnimNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    /// Direct list access for undo/redo history entries. History operations
    /// handle the Delaunay rebuild via their own `rebuild` callbacks; ordinary
    /// callers should still go through [#addNode] / [#removeNode].
    public List<MutableAnimNode> getNodesMutable() {
        return nodes;
    }

    public long getStructureVersion() {
        return structureVersion;
    }

    public void setOnNodeGraphChanged(@Nullable Runnable listener) {
        onNodeGraphChanged = listener;
    }

    /// Fire the node-graph change listener. Called from node add/remove/animation edits and from the
    /// graph widget's undo/redo resync.
    public void notifyNodeGraphChanged() {
        if (onNodeGraphChanged != null) onNodeGraphChanged.run();
    }

    public void rebuildDelaunay() {
        structureVersion++;
        nodeSpace = new DelaunayTriangulation<> (minX, minY, maxX, maxY, (x, y) -> new MutableAnimNode(x, y, -1, AnimationRef.EMPTY));

        for (MutableAnimNode mutableAnimNode : nodes) {
            nodeSpace.addPoint(mutableAnimNode);
        }

        nodeSpace.reloadTriangles();
    }

    public DelaunayTriangulation<MutableAnimNode> getNodeSpace() {
        return nodeSpace;
    }

    /// Codec-only intermediate grouping the two axis display names into one wire object, so the
    /// parent `group(...)` stays within the 16-element `RecordCodecBuilder` limit. The names
    /// themselves live on the holder as separate [EditorEditable] `String` fields.
    private record AxisNames(String x, String y) {
        static final AxisNames DEFAULT = new AxisNames("X", "Y");

        static final Codec<AxisNames> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                Codec.STRING.optionalFieldOf("x", "X").forGetter(AxisNames::x),
                Codec.STRING.optionalFieldOf("y", "Y").forGetter(AxisNames::y)
            ).apply(i, AxisNames::new)
        );
    }

    /// Codec-only intermediate. Round-trips the wire shape without
    /// participating in mutable in-memory state.
    private record NodeData(float x, float y, AnimationRef animation) {
        static final Codec<NodeData> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                Codec.FLOAT.fieldOf("x").forGetter(NodeData::x),
                Codec.FLOAT.fieldOf("y").forGetter(NodeData::y),
                AnimationRef.CODEC.fieldOf("animation").forGetter(NodeData::animation)
            ).apply(i, NodeData::new)
        );
    }

    public class MutableAnimNode implements DelaunayTriangulation.DelaunayPoint {
        private float x;
        private float y;
        private int index;
        private AnimationRef animation;

        public MutableAnimNode(float x, float y, int index, AnimationRef animation) {
            this.x = x;
            this.y = y;
            this.index = index;
            this.animation = animation;
        }

        @Override
        public float x() { return x; }

        public void setX(float x) {
            this.x = snapX(Math.clamp(x, getMinX(), getMaxX()));
            rebuildDelaunay();
        }

        @Override
        public float y() { return y; }

        public void setY(float y) {
            this.y = snapY(Math.clamp(y, getMinY(), getMaxY()));
            rebuildDelaunay();
        }

        public void setXY(float x, float y) {
            this.x = snapX(Math.clamp(x, getMinX(), getMaxX()));
            this.y = snapY(Math.clamp(y, getMinY(), getMaxY()));
            rebuildDelaunay();
        }

        @Override
        public int index() { return index; }

        @Override
        public void setIndex(int index) { this.index = index; }

        public AnimationRef animation() {
            return animation;
        }

        public void setAnimation(AnimationRef animation) {
            this.animation = animation;
            structureVersion++;
            notifyNodeGraphChanged();
        }
    }
}