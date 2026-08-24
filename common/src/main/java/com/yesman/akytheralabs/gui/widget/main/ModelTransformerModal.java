package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.CheckBox;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
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
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager.ExportResult;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager.ModelExport;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

    private static final int IMPORT_FAILURE_MODAL_W = 320;

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
        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
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
    private final PropertyPanel bonePanel;
    private final PropertyPanel exportOptionsPanel;
    private final Model3DViewer preview;

    private final AdvancedEditBox<String> exportPathBox;
    private final AnchoredButton exportPathBrowseBtn;
    private final AnchoredButton exportAllBtn;
    private final AnchoredButton closeScreenButton;

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

    public ModelTransformerModal(Font font, Unit x2, Unit y2, Component message) {
        super(x2, y2, message);

        this.font = font;
        this.modelList = new ModelList();
        this.resultList = new ResultList();
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

        this.exportPathBox = AdvancedEditBox.stringBox(
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

        this.exportPathBrowseBtn = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onPressBrowseExportPath())
            .xUnits(AnchoredWidget.pixel(8 + EXPORT_BTN_W + 4), AnchoredWidget.pixel(BROWSE_BTN_W))
            .yUnits(AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(BROWSE_BTN_W))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.EXPORT_PATH_BROWSE_BUTTON)
            .build();

        this.exportAllBtn = AnchoredButton.buttonBuilder(
                font,
                Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_EXPORT_ALL),
                _ -> onPressExportAll()
            )
            .xUnits(AnchoredWidget.pixel(8), AnchoredWidget.pixel(EXPORT_BTN_W))
            .yUnits(AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(FOOTER_WIDGET_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        this.closeScreenButton = AnchoredButton
            .buttonBuilder(font, Component.literal("X"), _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

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
        int exportTop = exportOptionsSectionTop();
        int exportListTop = exportTop + LIST_LABEL_H;

        bonePanel.setX1(AnchoredWidget.pixel(centreLeft));
        bonePanel.setX2(AnchoredWidget.pixel(centreWidth));
        bonePanel.setY1(AnchoredWidget.pixel(listTop));
        bonePanel.setY2(AnchoredWidget.pixel(Math.max(0, exportTop - SECTION_GAP - listTop)));

        exportOptionsPanel.setX1(AnchoredWidget.pixel(centreLeft));
        exportOptionsPanel.setX2(AnchoredWidget.pixel(centreWidth));
        exportOptionsPanel.setY1(AnchoredWidget.pixel(exportListTop));
        exportOptionsPanel.setY2(AnchoredWidget.pixel(Math.max(0, bottom - exportListTop)));

        ScreenRectangle rect = getRectangle();
        modelList.relocate(rect);
        resultList.relocate(rect);
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
            bonePanel, exportOptionsPanel,
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
            || bonePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || exportOptionsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();
        return focused != null && focused.keyPressed(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
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
        // picked bone over the export options.
        int columnTop = getY() + TITLE_H;
        int resultsTop = getY() + resultsSectionTop();
        int exportTop = getY() + exportOptionsSectionTop();

        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_JSON_MODELS, getX() + 1, leftRight - 1, columnTop);
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_RESULTS, getX() + 1, leftRight - 1, resultsTop);
        drawSectionLabel(g, LangKeys.GUI_MODEL_TRANSFORMER_MODAL_SECTION_BONE, leftRight + 1, centreRight - 1, columnTop);
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

        // Selected bone name, over the previewer's top-right corner.
        Bone selectedBone = armatureView == null ? null : armatureView.getSelectedBone();

        if (selectedBone != null) {
            Component name = Component.literal(displayNameOf(selectedBone));
            int nameX = preview.getRight() - 6 - font.width(name);
            int nameY = preview.getY() + 6;
            g.fill(nameX - 4, nameY - 3, preview.getRight() - 2, nameY + font.lineHeight + 3, 0xAA000000);
            g.text(font, name, nameX, nameY, 0xFFFFCC00, false);
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

    /// Bake every dropped `.json` as a skeletal mesh plus an armature. The first failure raises the
    /// import-failure modal and stops the batch — a stack of popups would bury the first message.
    private void handleDrops(List<Path> paths) {
        for (Path src : paths) {
            if (!src.getFileName().toString().endsWith(".json")) continue;

            try {
                entries.add(load(src));
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to import model {}", src, e);
                openImportFailureModal(e);
                break;
            }
        }

        modelList.rebuild();
        resultList.rebuild();
    }

    private ModelEntry load(Path src) {
        Identifier id = synthIdFromDropPath(src);
        SingleFileResourceManager resourceManager = new SingleFileResourceManager(src);

        SkeletalMesh mesh = MeshLoader.getLoader(id, resourceManager, AssetLoader::readMetadata)
            .loadSkeletalMesh(SkeletalMesh::new);
        Armature armature = ArmatureLoader.getLoader(id, resourceManager).loadArmature(Armature::new);

        return new ModelEntry(
            src,
            InMemoryAssetAccessor.mesh(id, mesh),
            InMemoryAssetAccessor.armature(id, armature),
            new LinkedHashMap<>()
        );
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

        int buttonWidth = WidgetContainerModal.evenButtonWidth(IMPORT_FAILURE_MODAL_W, 1);

        WidgetContainerModal modal = WidgetContainerModal.builder(
                font,
                Component.translatable(LangKeys.GUI_MODEL_TRANSFORMER_MODAL_IMPORT_FAILED_TITLE),
                IMPORT_FAILURE_MODAL_W
            )
            .textLines(Component.literal(body.toString()))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                buttonWidth
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    // ***************************************************************
    // Preview
    // ***************************************************************

    /// Shows `row`'s model, adding the bone overlay for an armature row. The camera only re-frames
    /// when the model itself changes.
    private void onSelectResultRow(ResultRow row) {
        boolean modelChanged = displayed != row.entry();

        preview.removeAllComponents(EntityModelComponent.class);

        if (armatureView != null) {
            preview.removeComponent(armatureView);
            armatureView = null;
        }

        preview.addComponent(new EntityModelComponent(row.entry().mesh()));

        if (row.armature()) {
            armatureView = new ArmatureViewComponent(row.entry().armature());
            armatureView.setOnSelectionChanged(this::rebuildBonePanel);
            preview.addComponent(armatureView);
        }

        displayed = row.entry();
        rebuildBonePanel();

        if (modelChanged) {
            MeshLoader.Metadata metadata = row.entry().mesh().get().getMetadata();
            preview.frameCamera(metadata.width(), metadata.height());
        }
    }

    // ***************************************************************
    // Bone properties
    // ***************************************************************

    /// The Bone section reads whatever the previewer has picked, so its rows are rebuilt on every
    /// pick and on every previewer swap.
    private void rebuildBonePanel() {
        Bone bone = armatureView == null ? null : armatureView.getSelectedBone();

        bonePanel.rebuild(bone == null ? List.of(new NoBoneRow()) : List.of(new BoneNameRow(bone)));
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
    private void renameBone(Bone bone, String typed) {
        if (displayed == null) return;

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
        Armature armature = displayed == null ? null : displayed.armature().get();

        if (armature == null) return false;

        Set<String> taken = new HashSet<>();

        armature.visitBones(other -> {
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
            entries.stream().map(e -> new ModelExport(e.source(), e.renames())).toList(),
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

    /// One imported model. The accessors wrap the baked assets and the mesh is closed by [#destroy];
    /// `renames` is the modal-side name mapper, keyed by the name the `.json` carries, since [Bone]
    /// itself is immutable.
    private record ModelEntry(
        Path source,
        AssetAccessor<SkeletalMesh> mesh,
        AssetAccessor<Armature> armature,
        Map<String, String> renames
    ) {
        String fileName() {
            return source.getFileName().toString();
        }

        String stem() {
            return FileSystemHelper.stem(fileName());
        }
    }

    /// One `.akasset` the export would write for `entry` — its armature half when `armature`.
    private record ResultRow(ModelEntry entry, boolean armature) {
        String label() {
            return entry.stem() + (armature ? "_armature." : "_mesh.") + AssetLoader.BINARY_EXTENSION;
        }
    }

    /// Base class for every row of a [PropertyPanel]. Exists only to fix the recursive type
    /// parameter — `ContainerObjectSelectionList.Entry<E>` requires `E extends Entry<E>`.
    private abstract static class PropertyRow extends ContainerObjectSelectionList.Entry<PropertyRow> {
        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of();
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
        private final AdvancedEditBox<String> nameBox;
        private final List<GuiEventListener> children;

        BoneNameRow(Bone bone) {
            this.nameBox = AdvancedEditBox.stringBox(
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
        private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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
        private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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
    }

    /// The two `.akasset` files each imported model becomes. Selecting a row drives the previewer.
    private final class ResultList extends AbstractSelectionList<ResultList.@NonNull Entry> implements AnchoredWidget {
        ResultList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild() {
            clearEntries();

            for (ModelEntry entry : entries) {
                addEntry(new Entry(new ResultRow(entry, false)));
                addEntry(new Entry(new ResultRow(entry, true)));
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
            if (selected != null) onSelectResultRow(selected.row);
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
        private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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
    }
}
