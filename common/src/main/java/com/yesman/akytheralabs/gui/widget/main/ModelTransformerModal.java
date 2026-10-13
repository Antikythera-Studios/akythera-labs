package com.yesman.akytheralabs.gui.widget.main;

import com.google.gson.JsonObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.json.JsonMeshLoader;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.InMemoryAssetAccessor;
import com.yesman.akytheralabs.SingleFileResourceManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager.ExportResult;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager.ModelExport;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager.ModelProbe;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.BonePoseComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/// Transcodes legacy `.json` models — mesh and armature squashed into one file — into the two
/// `.akasset` files the runtime reads. Dropped files are baked in memory for the previewer;
/// `Export All` runs the transcode through [AssetExportManager].
///
/// Three columns: the dropped models over the files they become, the properties of whatever is
/// picked in the previewer plus the options the export runs under, and the previewer itself.
public final class ModelTransformerModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int LIST_LABEL_H = 18;
    /// Bottom area: top FOOTER_ROW_H carries widgets, remaining WARNING_AREA_H reserved for messages.
    private static final int FOOTER_ROW_H = 24;
    private static final int WARNING_AREA_H = 16;
    private static final int BOTTOM_BAR_H = FOOTER_ROW_H + WARNING_AREA_H;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int LEFT_PCT = 25;
    private static final int PREVIEW_PCT = 40;          // right column width

    /// Share of the centre column above the "Export Options" strip.
    private static final int BONE_SECTION_PCT = 65;

    /// Space between one section's list and the next section's label strip.
    private static final int SECTION_GAP = 6;

    private static final int EXPORT_BTN_W = 70;
    private static final int BROWSE_BTN_W = FOOTER_ROW_H - 6;
    private static final int FOOTER_WIDGET_H = FOOTER_ROW_H - 6;
    private static final int EXPORT_TO_LABEL_W = 50;    // reserve for "Export To:" text

    // Property-row geometry (centre column).
    private static final int PROPERTY_ROW_H = 22;
    private static final int PROPERTY_LABEL_W = 90;
    private static final int EDIT_BOX_H = 16;
    private static final int EDIT_BOX_MAX_W = 160;
    private static final int CHECKBOX_H = 12;
    private static final int REVERT_BTN = 12; // the ✕ a picked armature carries

    /// Rows the Model section holds: the export name, the armature and the rebake button.
    private static final int MODEL_ROWS = 3;

    private static final int POPUP_W = 320;

    private static final long MESSAGE_DURATION_MS = 5000;

    /// Active instance for routing global events (file drops). Set on `open(...)`, cleared on
    /// `destroy()`.
    @Nullable
    private static ModelTransformerModal active = null;

    public static void open(Font font) {
        ModelTransformerModal modal = new ModelTransformerModal(
            font,
            AnchoredWidget.fraction(85),
            AnchoredWidget.fraction(90),
            Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_TITLE)
        );
        active = modal;
        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Routed from `MainEditorScreen.onFilesDrop` so dropping model files anywhere over the screen
    /// reaches the open transformer modal.
    public static boolean onFilesDrop(List<Path> paths) {
        if (active == null) return false;
        active.handleDrops(paths);
        return true;
    }

    private final Font font;

    private final ModelList modelList;
    private final ResultList resultList;
    private final PropertyPanel modelPanel;
    private final PropertyPanel bonePanel;
    private final PropertyPanel exportOptionsPanel;
    private final Model3DViewer preview;

    private final EditorEditBox<String> exportPathBox;
    private final AkytheraButton exportPathBrowseBtn;
    private final AkytheraButton exportAllBtn;
    private final AkytheraButton closeScreenButton;

    /// Every model that loaded, in drop order. Owns the baked meshes.
    private final List<ModelEntry> entries = new ArrayList<>();

    /// Sources whose last export attempt threw.
    private final Set<Path> failedExports = new HashSet<>();

    /// The model the previewer currently shows — what tells a row switch from a model switch, and
    /// which entry's renames the Bone section edits.
    @Nullable
    private ModelEntry displayed;

    @Nullable
    private ArmatureViewComponent armatureView;

    /// Whether the export wraps each armature in a fresh origin root. See
    /// `AssetExportManager#exportAllModels`.
    private boolean generateRootInOrigin = false;

    /// Inline error shown for ~5s under the lists.
    @Nullable
    private Component failedMessage;
    private long failedMessageUntil;

    /// Work a row asks for from inside its own click that rebuilds the panel the row sits in. Run at the
    /// start of the next frame instead, once the click has let go of the row.
    private final List<Runnable> deferred = new ArrayList<>();

    public ModelTransformerModal(Font font, Unit x2, Unit y2, Component message) {
        super(x2, y2, message);

        this.font = font;
        this.modelList = new ModelList();
        this.resultList = new ResultList();
        this.modelPanel = new PropertyPanel();
        this.bonePanel = new PropertyPanel();
        this.exportOptionsPanel = new PropertyPanel();

        // Preview: anchored to the right edge with RIGHT_WIDTH → x1=right offset, x2=width.
        this.preview = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(PREVIEW_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(BOTTOM_BAR_H),
            HorizontalAnchorType.RIGHT_WIDTH,
            VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));
        preview.addComponent(new GridFloorComponent(1.0F, 50, 68, 68, 68, 255));

        // Footer widgets: all centered in the top FOOTER_ROW_H band of the bottom bar.
        int widgetBottomOffset = WARNING_AREA_H + (FOOTER_ROW_H - FOOTER_WIDGET_H) / 2;

        this.exportPathBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(8 + EXPORT_TO_LABEL_W + 4),                       // left = right of label
            AnchoredWidget.pixel(8 + EXPORT_BTN_W + BROWSE_BTN_W + 8),             // right offset
            AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(FOOTER_WIDGET_H),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.BOTTOM_HEIGHT,
            ModelTransformerModal::defaultExportPath,
            _ -> {},
            Component.empty()
        );
        exportPathBox.setEditable(false);
        exportPathBox.setValueByString(defaultExportPath());

        this.exportPathBrowseBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> onPressBrowseExportPath())
            .xUnits(AnchoredWidget.pixel(8 + EXPORT_BTN_W + 4), AnchoredWidget.pixel(BROWSE_BTN_W))
            .yUnits(AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(BROWSE_BTN_W))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.EXPORT_PATH_BROWSE_BUTTON)
            .build();

        this.exportAllBtn = AkytheraButton.buttonBuilder(
                font,
                LocalizedMessage.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_EXPORT_ALL),
                () -> onPressExportAll()
            )
            .xUnits(AnchoredWidget.pixel(8), AnchoredWidget.pixel(EXPORT_BTN_W))
            .yUnits(AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(FOOTER_WIDGET_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        this.closeScreenButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        rebuildModelPanel();
        rebuildBonePanel();
        exportOptionsPanel.rebuild(List.of(new GenerateRootRow()));
    }

    /// The open project's `animmodels` directory — the one holding the `meshes` and `armatures`
    /// folders an export writes into.
    private static String defaultExportPath() {
        return AkytheraProject.getCurrentProject().getProjectRoot()
            .resolve("assets").resolve("animmodels")
            .toString();
    }

    // ***************************************************************
    // Layout
    // ***************************************************************

    /// Bottom of every column, relative to the modal.
    private int columnBottom() {
        return getHeight() - BOTTOM_BAR_H - 4;
    }

    /// Top of the "Results" label strip, relative to the modal. The left column splits into two
    /// equal list bodies, each under its own strip.
    private int resultsSectionTop() {
        int listTop = TITLE_H + LIST_LABEL_H;
        int listHeight = (columnBottom() - listTop - LIST_LABEL_H - SECTION_GAP) / 2;

        return listTop + listHeight + SECTION_GAP;
    }

    /// Top of the "Bone" label strip, relative to the modal. The Model section above it is
    /// [#MODEL_ROWS] rows tall.
    private int boneSectionTop() {
        return TITLE_H + LIST_LABEL_H + MODEL_ROWS * PROPERTY_ROW_H + SECTION_GAP;
    }

    /// Top of the "Export Options" label strip, relative to the modal.
    private int exportOptionsSectionTop() {
        return TITLE_H + (int) ((columnBottom() - TITLE_H) * (BONE_SECTION_PCT / 100.0F));
    }

    /// Right edge of the left column, relative to the modal.
    private int leftColumnRight() {
        return (int) (getWidth() * (LEFT_PCT / 100.0F));
    }

    /// Right edge of the centre column, relative to the modal.
    private int centreColumnRight() {
        return getWidth() - (int) (getWidth() * (PREVIEW_PCT / 100.0F));
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        super.relocate(screenRectangle);

        int listTop = TITLE_H + LIST_LABEL_H;
        int bottom = columnBottom();
        int leftRight = leftColumnRight();
        int centreRight = centreColumnRight();

        int resultsTop = resultsSectionTop();
        int resultsListTop = resultsTop + LIST_LABEL_H;

        modelList.setX1(AnchoredWidget.pixel(1));
        modelList.setX2(AnchoredWidget.pixel(Math.max(0, leftRight - 2)));
        modelList.setY1(AnchoredWidget.pixel(listTop));
        modelList.setY2(AnchoredWidget.pixel(Math.max(0, resultsTop - SECTION_GAP - listTop)));

        resultList.setX1(AnchoredWidget.pixel(1));
        resultList.setX2(AnchoredWidget.pixel(Math.max(0, leftRight - 2)));
        resultList.setY1(AnchoredWidget.pixel(resultsListTop));
        resultList.setY2(AnchoredWidget.pixel(Math.max(0, bottom - resultsListTop)));

        int centreLeft = leftRight + 2;
        int centreWidth = Math.max(0, centreRight - leftRight - 4);
        int boneListTop = boneSectionTop() + LIST_LABEL_H;
        int exportTop = exportOptionsSectionTop();
        int exportListTop = exportTop + LIST_LABEL_H;

        modelPanel.setX1(AnchoredWidget.pixel(centreLeft));
        modelPanel.setX2(AnchoredWidget.pixel(centreWidth));
        modelPanel.setY1(AnchoredWidget.pixel(listTop));
        modelPanel.setY2(AnchoredWidget.pixel(MODEL_ROWS * PROPERTY_ROW_H));

        bonePanel.setX1(AnchoredWidget.pixel(centreLeft));
        bonePanel.setX2(AnchoredWidget.pixel(centreWidth));
        bonePanel.setY1(AnchoredWidget.pixel(boneListTop));
        bonePanel.setY2(AnchoredWidget.pixel(Math.max(0, exportTop - SECTION_GAP - boneListTop)));

        exportOptionsPanel.setX1(AnchoredWidget.pixel(centreLeft));
        exportOptionsPanel.setX2(AnchoredWidget.pixel(centreWidth));
        exportOptionsPanel.setY1(AnchoredWidget.pixel(exportListTop));
        exportOptionsPanel.setY2(AnchoredWidget.pixel(Math.max(0, bottom - exportListTop)));

        ScreenRectangle rect = getRectangle();
        modelList.relocate(rect);
        resultList.relocate(rect);
        modelPanel.relocate(rect);
        bonePanel.relocate(rect);
        exportOptionsPanel.relocate(rect);
    }

    @Override
    public void onResizeCompleted() {
        preview.onResizeCompleted();
    }

    @Override
    public void destroy() {
        preview.destroy();

        for (ModelEntry entry : entries) {
            entry.mesh().get().close();
        }

        entries.clear();

        if (active == this) active = null;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(
            closeScreenButton,
            modelList, resultList,
            modelPanel, bonePanel, exportOptionsPanel,
            preview,
            exportPathBox, exportPathBrowseBtn, exportAllBtn
        );
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getRight()
            && mouseY >= getY() && mouseY < getY() + TITLE_H;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return modelList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || resultList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || modelPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || bonePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || exportOptionsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();
        return focused != null && focused.keyPressed(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!deferred.isEmpty()) {
            List<Runnable> pending = List.copyOf(deferred);

            deferred.clear();
            pending.forEach(Runnable::run);
        }

        super.extractWidgetRenderState(g, mouseX, mouseY, a);    // dimmer

        // Modal frame
        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        g.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        g.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        g.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        g.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        // Title bar
        g.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        g.text(font, getMessage(), getX() + 8, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        int bottomY = getBottom() - BOTTOM_BAR_H;
        int footerEndY = bottomY + FOOTER_ROW_H;

        g.fill(getX(), bottomY, getRight(), getBottom(), 0xFF222222);
        g.fill(getX(), bottomY, getRight(), bottomY + 1, 0xFF3A3A3A);
        g.fill(getX() + 4, footerEndY, getRight() - 4, footerEndY + 1, 0xFF2E2E2E);

        int labelY = bottomY + (FOOTER_ROW_H - font.lineHeight) / 2;
        g.text(font, Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_EXPORT_TO), getX() + 8, labelY, 0xFFAAAAAA, false);

        // Vertical separators between left / centre / right columns
        int leftRight = getX() + leftColumnRight();
        int centreRight = getX() + centreColumnRight();
        g.fill(leftRight - 1, getY() + TITLE_H, leftRight, bottomY, 0xFF333333);
        g.fill(centreRight - 1, getY() + TITLE_H, centreRight, bottomY, 0xFF333333);

        // Section label strips: the left column stacks models over results, the centre stacks the
        // displayed model over the picked bone over the export options.
        int columnTop = getY() + TITLE_H;
        int resultsTop = getY() + resultsSectionTop();
        int exportTop = getY() + exportOptionsSectionTop();

        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_JSON_MODELS, getX() + 1, leftRight - 1, columnTop);
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_RESULTS, getX() + 1, leftRight - 1, resultsTop);
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_MODEL, leftRight + 1, centreRight - 1, columnTop);
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_BONE, leftRight + 1, centreRight - 1, getY() + boneSectionTop());
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_EXPORT_OPTIONS, leftRight + 1, centreRight - 1, exportTop);

        // Drop hint over the empty models list
        if (entries.isEmpty()) {
            Component hint = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_DRAG_AND_DROP_MODELS);
            List<FormattedCharSequence> split = font.split(hint, Math.max(16, modelList.getWidth() - 8));
            int hintY = modelList.getY() + modelList.getHeight() / 2 - (font.lineHeight * split.size()) / 2;

            for (FormattedCharSequence line : split) {
                g.text(font, line, getX() + 8, hintY, 0xFF888888, false);
                hintY += font.lineHeight;
            }
        }

        for (GuiEventListener e : children()) {
            if (e instanceof AbstractWidget w) w.extractRenderState(g, mouseX, mouseY, a);
        }

        if (failedMessage != null) {
            if (System.currentTimeMillis() > failedMessageUntil) {
                failedMessage = null;
            } else {
                g.text(font, failedMessage, getX() + 6, getBottom() - BOTTOM_BAR_H - 12, 0xFFFF5555, false);
            }
        }
    }

    /// A filled strip carrying one section's name, spanning `left` to `right` at `top`.
    private void drawSectionLabel(GuiGraphicsExtractor g, String langKey, int left, int right, int top) {
        g.fill(left, top, right, top + LIST_LABEL_H, 0xFF2A2A2A);
        g.text(
            font, Component.translatable(langKey),
            left + 7, top + (LIST_LABEL_H - font.lineHeight) / 2,
            0xFFCCCCCC, false
        );
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // Import
    // ***************************************************************

    /// Bake every dropped `.json` as a skeletal mesh, plus the armature where the file carries one.
    /// The first failure raises the import-failure modal and stops the batch — a stack of popups
    /// would bury the first message.
    private void handleDrops(List<Path> paths) {
        for (Path src : paths) {
            if (!src.getFileName().toString().endsWith(".json")) continue;

            try {
                replaceOrAdd(load(src));
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to import model {}", src, e);
                openImportFailureModal(e);
                break;
            }
        }

        modelList.rebuild();
        resultList.rebuild();
    }

    /// Puts `entry` where the same source is already listed, closing the mesh the old one baked —
    /// a re-import starts over, so the picked armature, the renames and the export name go with the
    /// entry that held them. The previewer is rebuilt when it was showing the model that just went
    /// away.
    private void replaceOrAdd(ModelEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            ModelEntry old = entries.get(i);

            if (!old.source().equals(entry.source())) continue;

            entries.set(i, entry);
            old.mesh().get().close();
            entry.setExportName(freeExportName(entry));

            if (displayed == old) {
                displayed = null;
                showEntry(entry);
            }

            return;
        }

        entries.add(entry);
        entry.setExportName(freeExportName(entry));
    }

    /// `entry`'s own export name, or the first free `<name>_N` where another model already answers to
    /// it — a seed can collide with a name some other model was renamed to.
    private String freeExportName(ModelEntry entry) {
        String seed = entry.exportName();

        if (!isExportNameTaken(entry, seed)) return seed;

        for (int i = 1; ; i++) {
            String candidate = seed + "_" + i;

            if (!isExportNameTaken(entry, candidate)) return candidate;
        }
    }

    private ModelEntry load(Path src) throws IOException {
        Identifier id = synthIdFromDropPath(src);
        JsonObject root = AssetExportManager.readJsonObject(src);
        ModelProbe probe = AssetExportManager.INSTANCE.probeModel(root);
        AssetAccessor<Armature> originalArmature = null;

        if (probe.hasArmature()) {
            Armature armature = ArmatureLoader.getLoader(id, new SingleFileResourceManager(src)).loadArmature(Armature::new);
            originalArmature = InMemoryAssetAccessor.armature(id, armature);
        }

        return new ModelEntry(src, bakeMesh(id, root), originalArmature, probe.highestWeightBoneId());
    }

    /// Bakes `root` — already remapped — into the mesh the previewer draws. No sidecar metadata and no
    /// cloth: a dropped `.json` never resolved either, since nothing sits beside it.
    @SuppressWarnings("deprecation") // legacy .json is what this modal reads
    private static AssetAccessor<SkeletalMesh> bakeMesh(Identifier id, JsonObject root) {
        SkeletalMesh mesh = new JsonMeshLoader(id, root, MeshLoader.Metadata.DEFAULT, null)
            .loadSkeletalMesh(SkeletalMesh::new);

        return InMemoryAssetAccessor.mesh(id, mesh);
    }

    /// Re-bakes `entry`'s mesh from its source with its weight remap as it stands, closing the mesh it
    /// replaces, and shows it again. Read off disk and remapped the way the export is, so the rebaked
    /// preview skins what the export writes.
    private void rebakeMesh(ModelEntry entry) {
        try {
            JsonObject root = AssetExportManager.readJsonObject(entry.source());
            AssetExportManager.INSTANCE.remapWeightBoneIds(root, entry.resolvedWeightRemap());

            AssetAccessor<SkeletalMesh> old = entry.mesh();

            entry.setMesh(
                bakeMesh(synthIdFromDropPath(entry.source()), root),
                AssetExportManager.INSTANCE.probeModel(root).highestWeightBoneId(),
                Map.copyOf(entry.weightRemap())
            );
            old.get().close();
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to rebake model {}", entry.source(), e);
            openImportFailureModal(e);
            return;
        }

        if (displayed == entry) showEntry(entry);
    }

    /// Drop files live outside the project resource manager — we only need a unique identifier for
    /// the loader.
    private static Identifier synthIdFromDropPath(Path src) {
        String safe = FileSystemHelper.stem(src.getFileName().toString())
            .toLowerCase().replaceAll("[^a-z0-9_/.-]", "_");

        return AkytheraLabs.identifier("model_transformer_drop/" + safe);
    }

    /// The thrown message, plus its cause's — `ResourceLoadingFailureException` names only the
    /// resource when it wraps another exception.
    private void openImportFailureModal(Exception e) {
        StringBuilder body = new StringBuilder(String.valueOf(e.getMessage()));

        if (e.getCause() != null) body.append('\n').append(e.getCause().getMessage());

        int buttonWidth = WidgetContainerModal.evenButtonWidth(POPUP_W, 1);

        WidgetContainerModal modal = WidgetContainerModal.builder(
                font,
                Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_IMPORT_FAILED_TITLE),
                POPUP_W
            )
            .textLines(Component.literal(body.toString()))
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                buttonWidth
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    // ***************************************************************
    // Preview
    // ***************************************************************

    /// Shows `entry`'s mesh skinned to its armature in the pose the author set, with the bones drawn
    /// over it and posable through a gizmo. The camera only re-frames when the model itself changes.
    ///
    /// An armature short of the bones the mesh's weights address can't skin it — the CPU skinning path
    /// indexes the bone matrices by those ids unchecked and would throw mid-draw. The mesh then stands
    /// in its bind pose under a plain bone overlay, and the warning bar says why.
    private void showEntry(ModelEntry entry) {
        boolean modelChanged = displayed != entry;
        Bone previousPick = armatureView == null ? null : armatureView.getSelectedBone();

        // Releasing first drops a gizmo bound to the overlay about to go; removing it alone would not.
        preview.focus(null);
        preview.removeAllComponents(EntityModelComponent.class);
        preview.removeAllComponents(ArmatureViewComponent.class);
        preview.removeAllComponents(BonePoseComponent.class);
        armatureView = null;

        AssetAccessor<? extends Armature> armature = entry.armature();
        Armature resolved = armature == null ? null : armature.getOrHandleException(AkytheraLabs.LOGGER::error);
        boolean skinned = resolved != null && resolved.getBoneCount() > entry.highestWeightBoneId();

        EntityModelComponent model = skinned
            ? new EntityModelComponent(entry.mesh(), armature)
            : new EntityModelComponent(entry.mesh());

        preview.addComponent(model);

        if (armature != null) {
            armatureView = new ArmatureViewComponent(armature);

            // The pick survives a rebake or an armature swap wherever the name still resolves. Set
            // before the listener, so it rebuilds nothing mid-setup.
            if (previousPick != null && resolved != null) {
                armatureView.setSelectedBone(resolved.getBoneByName(previousPick.getName()));
            }

            armatureView.setBoneNameProvider(this::displayNameOf);
            armatureView.setOnSelectionChanged(this::rebuildBonePanel);

            if (skinned) {
                model.setPoseSource(entry::previewPose);
                armatureView.setPoseSource(entry::previewPose);
                preview.addComponent(new BonePoseComponent(armatureView, armature, entry::previewPose));
            } else {
                preview.addComponent(armatureView);
            }
        }

        if (resolved != null && !skinned) {
            failedMessage = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_ARMATURE_TOO_FEW_BONES);
            failedMessageUntil = System.currentTimeMillis() + MESSAGE_DURATION_MS;
        }

        displayed = entry;

        rebuildModelPanel();
        rebuildBonePanel();

        if (modelChanged) {
            MeshLoader.Metadata metadata = entry.mesh().get().getMetadata();
            preview.frameCamera(metadata.width(), metadata.height());
        }
    }

    // ***************************************************************
    // Model properties
    // ***************************************************************

    /// The Model section names the files the displayed model exports as, and the armature it rides.
    private void rebuildModelPanel() {
        modelPanel.rebuild(
            displayed == null
                ? List.of(new NoModelRow())
                : List.of(new ExportNameRow(displayed), new ArmatureRow(displayed), new RebakeRow(displayed))
        );
    }

    /// The export name a freshly imported `source` starts with: its stem, lowercased with anything
    /// outside `[a-z0-9_.-]` folded to `_`. A seed is folded rather than refused because the author
    /// has typed nothing to correct — a typed name is refused, see [ExportNameRow].
    private static String seedExportName(Path source) {
        return FileSystemHelper.stem(source.getFileName().toString())
            .toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    /// Whether `name` is one file the export could write: a path the resource system accepts, and a
    /// single component — a `/` passes [Identifier#isValidPath], which grades whole paths, but would
    /// nest a directory under `meshes/`.
    private static boolean isValidExportName(String name) {
        return !name.isEmpty() && !name.contains("/") && Identifier.isValidPath(name);
    }

    /// Whether a model other than `self` already exports under `name`.
    private boolean isExportNameTaken(ModelEntry self, String name) {
        for (ModelEntry entry : entries) {
            if (entry != self && entry.exportName().equals(name)) return true;
        }

        return false;
    }

    /// A one-button popup over the transformer, naming the export name that was refused.
    private void openNameRefusedPopup(String titleKey, String bodyKey, String name) {
        int buttonWidth = WidgetContainerModal.evenButtonWidth(POPUP_W, 1);

        WidgetContainerModal modal = WidgetContainerModal.builder(font, Component.translatable(titleKey), POPUP_W)
            .textLines(Component.translatable(bodyKey, name))
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                buttonWidth
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Moves `entry` onto `armature` — null puts it back on the one its `.json` carried. Re-lists the
    /// results because the armature row comes and goes with the pick.
    private void applyArmaturePick(ModelEntry entry, @Nullable AssetAccessor<? extends Armature> armature) {
        entry.setPickedArmature(armature);

        resultList.rebuild();
        showEntry(entry);
    }

    /// Opens the armature picker for `entry`. A model still riding the armature its `.json` carried
    /// asks first: the pick takes that armature off the export.
    private void onPressPickArmature(ModelEntry entry) {
        if (entry.usingOriginalArmature()) {
            openReplaceConfirm(
                LangKeys.GUI_MODEL_TRANSFORMER_MODAL_REPLACE_ARMATURE_TITLE,
                Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_REPLACE_ARMATURE),
                () -> openArmaturePicker(entry)
            );
            return;
        }

        openArmaturePicker(entry);
    }

    /// The picker stacks over this modal and pops itself on confirm. Its pool is the project's own
    /// armatures — the game's loaded packs are not somewhere an export may point. No `<none>` row:
    /// [ArmatureRow]'s ✕ is the way back.
    private void openArmaturePicker(ModelEntry entry) {
        AccessorSelectionModal modal = new AccessorSelectionModal(
            font,
            false,
            AccessorSelectionModal.listProjectAccessors(AssetAccessor.Type.ARMATURE),
            accessor -> {
                @SuppressWarnings("unchecked")
                AssetAccessor<? extends Armature> armature = (AssetAccessor<? extends Armature>) accessor;

                applyArmaturePick(entry, armature);
            }
        );

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// A Cancel / Replace prompt over the transformer; `proceed` runs on Replace.
    private void openReplaceConfirm(String titleKey, Component body, Runnable proceed) {
        int buttonWidth = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal modal = WidgetContainerModal.builder(font, Component.translatable(titleKey), POPUP_W)
            .textLines(body)
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                buttonWidth
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_REPLACE), () -> {
                        ModalManager.getInstance().closeModal();
                        proceed.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                buttonWidth
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    // ***************************************************************
    // Bone properties
    // ***************************************************************

    /// The Bone section reads whatever the previewer has picked, so its rows are rebuilt on every
    /// pick and on every previewer swap.
    private void rebuildBonePanel() {
        Bone bone = armatureView == null ? null : armatureView.getSelectedBone();

        bonePanel.rebuild(
            bone == null
                ? List.of(new NoBoneRow())
                : List.of(new BoneNameRow(bone), new WeightsFromRow(bone))
        );
    }

    /// The name a bone is shown and exported under — its rename where the user set one.
    private String displayNameOf(Bone bone) {
        return displayed == null
            ? bone.getName()
            : displayed.renames().getOrDefault(bone.getName(), bone.getName());
    }

    /// Records a rename for `bone` on the displayed model. A blank name, the bone's own name, or one
    /// another bone already answers to is dropped: an armature addresses its bones by name, so a
    /// collision would collapse two of them on load.
    ///
    /// A rename keys on the source `.json`'s bone name, so it is only recorded while that file's
    /// armature is the one on screen.
    private void renameBone(Bone bone, String typed) {
        if (displayed == null || !displayed.usingOriginalArmature()) return;

        Map<String, String> renames = displayed.renames();
        String trimmed = typed.trim();

        if (trimmed.isEmpty() || trimmed.equals(bone.getName())) {
            renames.remove(bone.getName());
            return;
        }

        if (isNameTaken(trimmed, bone)) return;

        renames.put(bone.getName(), trimmed);
    }

    /// Whether a bone other than `self` already answers to `name` on the displayed model.
    private boolean isNameTaken(String name, Bone self) {
        AssetAccessor<? extends Armature> accessor = displayed == null ? null : displayed.armature();

        if (accessor == null) return false;

        Set<String> taken = new HashSet<>();

        accessor.get().visitBones(other -> {
            if (!other.getName().equals(self.getName())) taken.add(displayNameOf(other));
        });

        return taken.contains(name);
    }

    // ***************************************************************
    // Export
    // ***************************************************************

    private void onPressBrowseExportPath() {
        String current = exportPathBox.getValue();

        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog("Choose Export Folder", current))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                exportPathBox.setValueByString(result);
            }, Minecraft.getInstance());
    }

    private void onPressExportAll() {
        if (entries.isEmpty()) return;

        Path destDir = Path.of(exportPathBox.getValue());
        List<ExportResult<Path>> results = AssetExportManager.INSTANCE.exportAllModels(
            entries.stream()
                .map(e -> new ModelExport(
                    e.source(), e.exportName(), e.renames(), e.resolvedWeightRemap(), e.usingOriginalArmature()
                ))
                .toList(),
            destDir,
            generateRootInOrigin
        );

        failedExports.clear();

        for (ExportResult<Path> r : results) {
            if (r instanceof ExportResult.Failure<Path> f) failedExports.add(f.entry());
        }

        if (failedExports.isEmpty()) {
            FileSystemHelper.openInFileExplorer(destDir);
        } else {
            failedMessage = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_EXPORT_FAILED);
            failedMessageUntil = System.currentTimeMillis() + MESSAGE_DURATION_MS;
        }
    }

    // ***************************************************************
    // Rows
    // ***************************************************************

    /// One imported model. The accessors wrap the baked assets and the mesh is closed by [#destroy]
    /// or by a re-import of the same file; [#renames] is the modal-side name mapper, keyed by the
    /// name the `.json` carries, since [Bone] itself is immutable.
    private static final class ModelEntry {
        private final Path source;

        /// Baked from [#source] with [#bakedRemap] applied.
        private AssetAccessor<SkeletalMesh> mesh;

        /// The armature baked out of [#source], or null where the file carries no `armature` block.
        @Nullable
        private final AssetAccessor<Armature> originalArmature;

        /// The highest bone id [#mesh]'s weights address as baked, -1 where no bone deforms it. An
        /// armature skins the mesh only with more bones than this.
        private int highestWeightBoneId;

        private final Map<String, String> renames = new LinkedHashMap<>();

        /// Source weight ids, each rebound to the bone named here: the vertices the `.json` weighted to
        /// that id follow this bone instead. One bone per id; a bone may take several. Names bones of
        /// [#armature], so it starts over with it.
        private final Map<Integer, String> weightRemap = new LinkedHashMap<>();

        /// [#weightRemap] as it stood when [#mesh] was baked. The preview trails the remap until they
        /// match again.
        private Map<Integer, String> bakedRemap = Map.of();

        /// The pose the preview skins the mesh in, keyed by the bone names of [#armature]. Starts as the
        /// bind pose, and starts over whenever [#armature] changes.
        private Pose previewPose = new Pose();

        /// The stem both exported files are written under. Seeded from [#source]'s, and the Model
        /// section edits it; the file on disk keeps its own name either way.
        private String exportName;

        /// The project armature shadowing [#originalArmature]. Clearing it brings that one back.
        @Nullable
        private AssetAccessor<? extends Armature> pickedArmature;

        ModelEntry(
            Path source,
            AssetAccessor<SkeletalMesh> mesh,
            @Nullable AssetAccessor<Armature> originalArmature,
            int highestWeightBoneId
        ) {
            this.source = source;
            this.mesh = mesh;
            this.originalArmature = originalArmature;
            this.highestWeightBoneId = highestWeightBoneId;
            this.exportName = seedExportName(source);
        }

        Path source() {
            return source;
        }

        AssetAccessor<SkeletalMesh> mesh() {
            return mesh;
        }

        /// Replaces the baked mesh, with the highest id its weights address and the remap it was baked
        /// with. Closing the old one is the caller's.
        void setMesh(AssetAccessor<SkeletalMesh> mesh, int highestWeightBoneId, Map<Integer, String> bakedRemap) {
            this.mesh = mesh;
            this.highestWeightBoneId = highestWeightBoneId;
            this.bakedRemap = bakedRemap;
        }

        /// Whether [#mesh] was baked with a remap other than the one [#weightRemap] holds now.
        boolean isBakeStale() {
            return !bakedRemap.equals(weightRemap);
        }

        Map<Integer, String> weightRemap() {
            return weightRemap;
        }

        /// The source weight ids rebound to `boneName`, ascending.
        List<Integer> weightSourcesOf(String boneName) {
            return weightRemap.entrySet().stream()
                .filter(e -> e.getValue().equals(boneName))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        }

        /// Makes `ids` the source weight ids `boneName` takes over — replacing its own, and taking each
        /// from whichever bone held it.
        void setWeightSources(String boneName, Set<Integer> ids) {
            weightRemap.values().removeIf(boneName::equals);
            ids.forEach(id -> weightRemap.put(id, boneName));
        }

        /// [#weightRemap] resolved to [#armature]'s bone ids, for baking and export.
        Map<Integer, Integer> resolvedWeightRemap() {
            AssetAccessor<? extends Armature> accessor = armature();
            Armature armature = accessor == null ? null : accessor.getOrHandleException(AkytheraLabs.LOGGER::error);
            Map<Integer, Integer> resolved = new LinkedHashMap<>();

            if (armature == null) return resolved;

            weightRemap.forEach((sourceId, boneName) -> {
                Bone bone = armature.getBoneByName(boneName);

                if (bone != null) resolved.put(sourceId, bone.getId());
            });

            return resolved;
        }

        Map<String, String> renames() {
            return renames;
        }

        String exportName() {
            return exportName;
        }

        void setExportName(String exportName) {
            this.exportName = exportName;
        }

        @Nullable
        AssetAccessor<? extends Armature> pickedArmature() {
            return pickedArmature;
        }

        /// A pose and a weight remap both name bones of one armature, so moving to another starts them
        /// over.
        void setPickedArmature(@Nullable AssetAccessor<? extends Armature> pickedArmature) {
            this.pickedArmature = pickedArmature;
            this.previewPose = new Pose();
            this.weightRemap.clear();
        }

        int highestWeightBoneId() {
            return highestWeightBoneId;
        }

        Pose previewPose() {
            return previewPose;
        }

        /// The armature this model is drawn against — the pick where there is one.
        @Nullable
        AssetAccessor<? extends Armature> armature() {
            return pickedArmature != null ? pickedArmature : originalArmature;
        }

        /// Whether [#armature] is the one [#source] carried, rather than a pick shadowing it. The
        /// export writes an armature file, and the Bone rows take edits, exactly where this holds.
        boolean usingOriginalArmature() {
            return pickedArmature == null && originalArmature != null;
        }

        String fileName() {
            return source.getFileName().toString();
        }
    }

    /// One `.akasset` the export would write for `entry`, named by the folder it lands in — its
    /// armature half when `armature`.
    private record ResultRow(ModelEntry entry, boolean armature) {
        String label() {
            String directory = armature ? AssetExportManager.ARMATURE_DIRECTORY : AssetExportManager.MESH_DIRECTORY;

            return directory + "/" + entry.exportName() + "." + AssetLoader.BINARY_EXTENSION;
        }
    }

    /// What the Model section shows while nothing is picked in the Results list.
    private final class NoModelRow extends PropertyRow {
        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component hint = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_NO_MODEL_SELECTED);
            g.text(font, hint, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFF888888, false);
        }
    }

    /// The name the displayed model's files are exported under. The dropped `.json` keeps its own.
    private final class ExportNameRow extends PropertyRow {
        private final ModelEntry entry;
        private final EditorEditBox<String> nameBox;
        private final List<GuiEventListener> children;

        ExportNameRow(ModelEntry entry) {
            this.entry = entry;
            this.nameBox = EditorEditBox.stringBox(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EDIT_BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                entry::exportName,
                _ -> {},
                Component.empty()
            );

            nameBox.setValue(entry.exportName());

            // On commit, not on the value consumer: that one fires per character, so a name typed
            // toward `cape_02` would raise a popup at every keystroke on the way.
            nameBox.setOnCommitted((_, typed) -> onNameCommitted(typed));

            this.children = List.of(nameBox);
        }

        /// Takes the finished name, or refuses it and puts the box back to the one the model carries.
        private void onNameCommitted(String typed) {
            String trimmed = typed.trim();

            if (trimmed.equals(entry.exportName())) return;

            if (!isValidExportName(trimmed)) {
                nameBox.setValue(entry.exportName());
                openNameRefusedPopup(
                    LangKeys.GUI_MODEL_TRANSFORMER_MODAL_INVALID_NAME_TITLE,
                    LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_INVALID_NAME,
                    trimmed
                );
                return;
            }

            if (isExportNameTaken(entry, trimmed)) {
                nameBox.setValue(entry.exportName());
                openNameRefusedPopup(
                    LangKeys.GUI_MODEL_TRANSFORMER_MODAL_DUPLICATED_NAME_TITLE,
                    LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_DUPLICATED_NAME,
                    trimmed
                );
                return;
            }

            entry.setExportName(trimmed);
            resultList.rebuild();
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(nameBox);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_FIELD_EXPORT_NAME);
            int labelX = getX() + 6;
            g.text(font, label, labelX, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxX = labelX + PROPERTY_LABEL_W;
            int boxY = getY() + (getHeight() - EDIT_BOX_H) / 2;
            int boxW = Math.clamp(getRight() - boxX - 6, 0, EDIT_BOX_MAX_W);
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            nameBox.setX1(AnchoredWidget.pixel(boxX - modalX));
            nameBox.setX2(AnchoredWidget.pixel(boxW));
            nameBox.setY1(AnchoredWidget.pixel(boxY - modalY));
            nameBox.setY2(AnchoredWidget.pixel(EDIT_BOX_H));
            nameBox.relocate(modalRect);
            nameBox.extractRenderState(g, mx, my, a);
        }
    }

    /// The armature the displayed model rides, picked out of the project's own pool. While a pick
    /// stands the row carries a ✕ that puts the model back on the armature it was imported with.
    private final class ArmatureRow extends PropertyRow {
        private final ModelEntry entry;
        private final PopupBox<AssetAccessor<? extends Armature>> armatureBox;
        private final List<GuiEventListener> children;

        ArmatureRow(ModelEntry entry) {
            this.entry = entry;
            this.armatureBox = new PopupBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EDIT_BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                entry::armature,
                _ -> {},
                AccessorSelectionModal::displayName,
                Component.empty(),
                _ -> onPressPickArmature(entry)
            );

            armatureBox.setValue(entry.armature());

            this.children = List.of(armatureBox);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(armatureBox);
        }

        private int boxLeft() {
            return getX() + 6 + PROPERTY_LABEL_W;
        }

        /// Width of the picker box, leaving the [#REVERT_BTN] gutter clear for the ✕.
        private int boxWidth() {
            return Math.clamp(getRight() - boxLeft() - 6 - REVERT_BTN, 0, EDIT_BOX_MAX_W);
        }

        /// Left edge of the ✕, just right of the picker box.
        private int revertX() {
            return boxLeft() + boxWidth() + 4;
        }

        private boolean overRevert(double mouseX, double mouseY) {
            return entry.pickedArmature() != null
                && mouseX >= revertX() && mouseX < revertX() + REVERT_BTN
                && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && overRevert(event.x(), event.y())) {
                // Next frame: the roll-back rebuilds the panel this row sits in.
                deferred.add(() -> applyArmaturePick(entry, null));
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_FIELD_ARMATURE);
            g.text(font, label, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxY = getY() + (getHeight() - EDIT_BOX_H) / 2;
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            armatureBox.setX1(AnchoredWidget.pixel(boxLeft() - modalX));
            armatureBox.setX2(AnchoredWidget.pixel(boxWidth()));
            armatureBox.setY1(AnchoredWidget.pixel(boxY - modalY));
            armatureBox.setY2(AnchoredWidget.pixel(EDIT_BOX_H));
            armatureBox.relocate(modalRect);
            armatureBox.extractRenderState(g, mx, my, a);

            if (entry.pickedArmature() == null) return;

            g.text(
                font, Component.literal("✕"),
                revertX(), getY() + (getHeight() - font.lineHeight) / 2,
                overRevert(mx, my) ? 0xFFFFFFFF : 0xFF888888, false
            );
        }
    }

    /// Base class for every row of a [PropertyPanel]. Fixes the recursive type parameter —
    /// `ContainerObjectSelectionList.Entry<E>` requires `E extends Entry<E>`.
    private abstract static class PropertyRow extends ContainerObjectSelectionList.Entry<PropertyRow> {
        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            // Re-focusing the already-focused child clears the focus in vanilla's implementation.
            if (getFocused() == focused) return;

            super.setFocused(focused);
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /// Rebakes the displayed model's mesh with its weight remap. Lit while the preview trails the remap.
    private final class RebakeRow extends PropertyRow {
        private final AkytheraButton button;
        private final List<GuiEventListener> children;

        RebakeRow(ModelEntry entry) {
            this.button = AkytheraButton
                .buttonBuilder(
                    font,
                    LocalizedMessage.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_REBAKE_MESH),
                    // Next frame: the rebake rebuilds the panel this button sits in.
                    () -> deferred.add(() -> rebakeMesh(entry))
                )
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                .isHighlighted(_ -> entry.isBakeStale())
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build();

            this.children = List.of(button);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int boxX = getX() + 6 + PROPERTY_LABEL_W;
            int boxY = getY() + (getHeight() - EDIT_BOX_H) / 2;
            int boxW = Math.clamp(getRight() - boxX - 6, 0, EDIT_BOX_MAX_W);
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            button.setX1(AnchoredWidget.pixel(boxX - modalX));
            button.setX2(AnchoredWidget.pixel(boxW));
            button.setY1(AnchoredWidget.pixel(boxY - modalY));
            button.setY2(AnchoredWidget.pixel(EDIT_BOX_H));
            button.relocate(modalRect);
            button.extractRenderState(g, mx, my, a);
        }
    }

    /// The source weight ids the picked bone takes over, comma-separated: every vertex the `.json`
    /// weighted to one of them is skinned to this bone instead, in the export and in the preview once
    /// rebaked. Blank takes over none.
    ///
    /// Editable under any armature, a picked one included — the mesh is exported either way.
    private final class WeightsFromRow extends PropertyRow {
        private final Bone bone;
        private final EditorEditBox<String> idBox;
        private final List<GuiEventListener> children;

        WeightsFromRow(Bone bone) {
            this.bone = bone;
            this.idBox = new EditorEditBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EDIT_BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                this::shownIds,
                _ -> {},
                s -> s.chars().allMatch(c -> Character.isDigit(c) || c == ',' || c == ' '),
                s -> s,
                s -> s,
                Component.empty()
            );

            idBox.setValue(shownIds());
            idBox.setOnCommitted((_, typed) -> onIdsCommitted(typed));

            this.children = List.of(idBox);
        }

        /// The ids this bone takes over as the box shows them.
        private String shownIds() {
            return displayed == null
                ? ""
                : displayed.weightSourcesOf(bone.getName()).stream().map(String::valueOf).collect(Collectors.joining(", "));
        }

        /// Takes the listed ids for this bone. Ids another bone holds revert the box and ask before
        /// moving, and a list that doesn't parse just reverts.
        private void onIdsCommitted(String typed) {
            ModelEntry entry = displayed;

            if (entry == null) return;

            Set<Integer> ids = new TreeSet<>();

            try {
                for (String token : typed.split(",")) {
                    if (!token.isBlank()) ids.add(Integer.parseInt(token.trim()));
                }
            } catch (NumberFormatException e) {
                idBox.setValue(shownIds());
                return;
            }

            Map<Integer, String> taken = new TreeMap<>();

            for (int id : ids) {
                String holder = entry.weightRemap().get(id);

                if (holder != null && !holder.equals(bone.getName())) taken.put(id, holder);
            }

            if (taken.isEmpty()) {
                entry.setWeightSources(bone.getName(), ids);
                idBox.setValue(shownIds());
                return;
            }

            idBox.setValue(shownIds());
            openReplaceConfirm(
                LangKeys.GUI_MODEL_TRANSFORMER_MODAL_WEIGHTS_FROM_TAKEN_TITLE,
                Component.translatable(
                    LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_WEIGHTS_FROM_TAKEN,
                    taken.entrySet().stream()
                        .map(e -> e.getValue() + " (" + e.getKey() + ")")
                        .collect(Collectors.joining(", "))
                ),
                () -> {
                    entry.setWeightSources(bone.getName(), ids);
                    rebuildBonePanel();
                }
            );
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(idBox);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_FIELD_WEIGHTS_FROM_IDS);
            int labelX = getX() + 6;
            g.text(font, label, labelX, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxX = labelX + PROPERTY_LABEL_W;
            int boxY = getY() + (getHeight() - EDIT_BOX_H) / 2;
            int boxW = Math.clamp(getRight() - boxX - 6, 0, EDIT_BOX_MAX_W);
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            idBox.setX1(AnchoredWidget.pixel(boxX - modalX));
            idBox.setX2(AnchoredWidget.pixel(boxW));
            idBox.setY1(AnchoredWidget.pixel(boxY - modalY));
            idBox.setY2(AnchoredWidget.pixel(EDIT_BOX_H));
            idBox.relocate(modalRect);
            idBox.extractRenderState(g, mx, my, a);
        }
    }

    /// What the Bone section shows while the previewer has nothing picked.
    private final class NoBoneRow extends PropertyRow {
        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component hint = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_MESSAGE_NO_BONE_SELECTED);
            g.text(font, hint, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFF888888, false);
        }
    }

    /// The picked bone's name, editable. Edits land in the displayed model's rename map rather than
    /// on [Bone], which is immutable, and are applied when the armature is encoded.
    private final class BoneNameRow extends PropertyRow {
        private final EditorEditBox<String> nameBox;
        private final List<GuiEventListener> children;

        BoneNameRow(Bone bone) {
            this.nameBox = EditorEditBox.stringBox(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EDIT_BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> displayNameOf(bone),
                typed -> renameBone(bone, typed),
                Component.empty()
            );

            // Seeded with setValue, not setValueByString: the latter pushes through the change
            // callback, which would write the bone's own name back as a rename.
            nameBox.setValue(displayNameOf(bone));
            nameBox.setEditable(displayed != null && displayed.usingOriginalArmature());

            this.children = List.of(nameBox);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(nameBox);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_FIELD_BONE_NAME);
            int labelX = getX() + 6;
            g.text(font, label, labelX, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxX = labelX + PROPERTY_LABEL_W;
            int boxY = getY() + (getHeight() - EDIT_BOX_H) / 2;
            int boxW = Math.clamp(getRight() - boxX - 6, 0, EDIT_BOX_MAX_W);
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            nameBox.setX1(AnchoredWidget.pixel(boxX - modalX));
            nameBox.setX2(AnchoredWidget.pixel(boxW));
            nameBox.setY1(AnchoredWidget.pixel(boxY - modalY));
            nameBox.setY2(AnchoredWidget.pixel(EDIT_BOX_H));
            nameBox.relocate(modalRect);
            nameBox.extractRenderState(g, mx, my, a);
        }
    }

    /// The "Generate root bone in origin" toggle. Its provider / consumer pair targets the modal's
    /// own field, so the state outlives a panel rebuild.
    private final class GenerateRootRow extends PropertyRow {
        private final CheckBox checkbox;
        private final List<GuiEventListener> children;

        GenerateRootRow() {
            this.checkbox = new CheckBox(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHECKBOX_H),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHECKBOX_H),
                HorizontalAnchorType.LEFT_WIDTH,
                VerticalAnchorType.TOP_HEIGHT,
                () -> generateRootInOrigin,
                v -> generateRootInOrigin = v,
                Component.empty(),
                false
            );

            this.children = List.of(checkbox);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(checkbox);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_OPTION_GENERATE_ROOT_IN_ORIGIN);
            int labelX = getX() + 6;
            g.text(font, label, labelX, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxX = Math.max(labelX + font.width(label) + 8, getRight() - CHECKBOX_H - 8);
            int boxY = getY() + (getHeight() - CHECKBOX_H) / 2;
            int modalX = ModelTransformerModal.this.getX();
            int modalY = ModelTransformerModal.this.getY();
            ScreenRectangle modalRect = ModelTransformerModal.this.getRectangle();

            checkbox.setX1(AnchoredWidget.pixel(boxX - modalX));
            checkbox.setX2(AnchoredWidget.pixel(CHECKBOX_H));
            checkbox.setY1(AnchoredWidget.pixel(boxY - modalY));
            checkbox.setY2(AnchoredWidget.pixel(CHECKBOX_H));
            checkbox.relocate(modalRect);
            checkbox.extractRenderState(g, mx, my, a);
        }
    }

    /// A centre-column section body: scrollable rows that host their own widgets.
    private final class PropertyPanel extends ContainerObjectSelectionList<PropertyRow> implements AnchoredWidget {
        PropertyPanel() {
            super(Minecraft.getInstance(), 0, 0, 0, PROPERTY_ROW_H);
        }

        void rebuild(List<? extends PropertyRow> rows) {
            // Dropping focus first commits a box still being edited against the rows it was built for,
            // before they go.
            setFocused(null);
            clearEntries();

            for (PropertyRow row : rows) addEntry(row);
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override public int getRowWidth() { return getWidth() - SCROLLBAR_WIDTH; }
        @Override public int scrollbarWidth() { return SCROLLBAR_W; }
        @Override protected int scrollBarX() { return getRight() - scrollbarWidth(); }
        @Override protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {}
        @Override protected boolean entriesCanBeSelected() { return false; }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (height * height) / (float) contentHeight()), 8, height - 8);
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;
            g.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            g.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        public void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        // x/y units are set every relocate() — initial values just keep the widget non-degenerate.
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(0);
        private Unit y2 = AnchoredWidget.pixel(0);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

        @Override public Unit getX1() { return x1; }
        @Override public Unit getX2() { return x2; }
        @Override public Unit getY1() { return y1; }
        @Override public Unit getY2() { return y2; }
        @Override public void setX1(Unit unit) { x1 = unit; }
        @Override public void setX2(Unit unit) { x2 = unit; }
        @Override public void setY1(Unit unit) { y1 = unit; }
        @Override public void setY2(Unit unit) { y2 = unit; }
        @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
        @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
        @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }

    /// Dropped `.json` files, listed as they came in. Display only — the results list is what drives
    /// the previewer.
    private final class ModelList extends AbstractSelectionList<ModelList.@NonNull Entry> implements AnchoredWidget {
        ModelList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild() {
            clearEntries();
            for (ModelEntry entry : entries) addEntry(new Entry(entry));
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override public int getRowWidth() { return getWidth() - SCROLLBAR_WIDTH; }
        @Override public int scrollbarWidth() { return SCROLLBAR_W; }
        @Override protected int scrollBarX() { return getRight() - scrollbarWidth(); }
        @Override protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {}
        @Override protected boolean entriesCanBeSelected() { return false; }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (height * height) / (float) contentHeight()), 8, height - 8);
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;
            g.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            g.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            private final ModelEntry entry;

            Entry(ModelEntry entry) { this.entry = entry; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                int color = failedExports.contains(entry.source()) ? 0xFFFF5555 : 0xFFCCCCCC;
                g.text(font, Component.literal(entry.fileName()), getX() + 6, getY() + (getHeight() - 8) / 2, color, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean doubleClicked) {
                return false;
            }
        }

        // x/y units are set every relocate() — initial values just keep the widget non-degenerate.
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(0);
        private Unit y2 = AnchoredWidget.pixel(0);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

        @Override public Unit getX1() { return x1; }
        @Override public Unit getX2() { return x2; }
        @Override public Unit getY1() { return y1; }
        @Override public Unit getY2() { return y2; }
        @Override public void setX1(Unit unit) { x1 = unit; }
        @Override public void setX2(Unit unit) { x2 = unit; }
        @Override public void setY1(Unit unit) { y1 = unit; }
        @Override public void setY2(Unit unit) { y2 = unit; }
        @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
        @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
        @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }

    /// The two `.akasset` files each imported model becomes. Selecting a row drives the previewer.
    private final class ResultList extends AbstractSelectionList<ResultList.@NonNull Entry> implements AnchoredWidget {
        ResultList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild() {
            clearEntries();

            for (ModelEntry entry : entries) {
                Entry meshRow = new Entry(new ResultRow(entry, false));
                addEntry(meshRow);

                if (entry.usingOriginalArmature()) addEntry(new Entry(new ResultRow(entry, true)));

                // super: re-listing the displayed model must not re-run the previewer setup.
                if (entry == displayed) super.setSelected(meshRow);
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override public int getRowWidth() { return getWidth() - SCROLLBAR_WIDTH; }
        @Override public int scrollbarWidth() { return SCROLLBAR_W; }
        @Override protected int scrollBarX() { return getRight() - scrollbarWidth(); }
        @Override protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {}
        @Override protected boolean entriesCanBeSelected() { return true; }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (height * height) / (float) contentHeight()), 8, height - 8);
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;
            g.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            g.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor g, Entry entry, int outlineColor) {
            g.fill(entry.getX(), entry.getY(), entry.getContentRight(), entry.getY() + entry.getContentHeight(), 0xFF0A2833);
            g.outline(entry.getX(), entry.getY(), entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        public void setSelected(@Nullable Entry selected) {
            super.setSelected(selected);
            if (selected != null) showEntry(selected.row.entry());
        }

        @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            final ResultRow row;

            Entry(ResultRow row) { this.row = row; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                int color = failedExports.contains(row.entry().source())
                    ? 0xFFFF5555
                    : getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC;

                g.text(font, Component.literal(row.label()), getX() + 6, getY() + (getHeight() - 8) / 2, color, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean doubleClicked) {
                setSelected(this);
                return true;
            }
        }

        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(0);
        private Unit y2 = AnchoredWidget.pixel(0);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

        @Override public Unit getX1() { return x1; }
        @Override public Unit getX2() { return x2; }
        @Override public Unit getY1() { return y1; }
        @Override public Unit getY2() { return y2; }
        @Override public void setX1(Unit unit) { x1 = unit; }
        @Override public void setX2(Unit unit) { x2 = unit; }
        @Override public void setY1(Unit unit) { y1 = unit; }
        @Override public void setY2(Unit unit) { y2 = unit; }
        @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
        @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
        @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }
}
