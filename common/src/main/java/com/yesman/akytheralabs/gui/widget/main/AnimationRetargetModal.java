package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.*;
import com.yesman.akythera.client.gui.widget.viewer.*;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent.SimpleHeadlessAnimator;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.SingleFileResourceManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.main.AnimationRetargetManager.ExportResult;
import com.yesman.akytheralabs.gui.widget.main.AnimationRetargetManager.TemplateEntry;
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
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

public final class AnimationRetargetModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_LABEL_H = 12;
    /// Bottom area: top FOOTER_ROW_H carries widgets, remaining WARNING_AREA_H reserved for messages.
    private static final int FOOTER_ROW_H = 24;
    private static final int WARNING_AREA_H = 16;
    private static final int BOTTOM_BAR_H = FOOTER_ROW_H + WARNING_AREA_H;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int LEFT_PCT = 25;
    private static final int PREVIEW_PCT = 40;          // right column width
    private static final int EXPORT_BTN_W = 70;
    private static final int BROWSE_BTN_W = FOOTER_ROW_H - 6;
    private static final int FOOTER_WIDGET_H = FOOTER_ROW_H - 6;
    private static final int EXPORT_TO_LABEL_W = 50;    // reserve for "Export To:" text
    private static final int CHECKBOX_H = 12;

    // Source-Armature section (one-row drop slot between the armature list and the animation list).
    private static final int SRC_ARM_SLOT_H = 20;
    private static final int SRC_ARM_SECTION_H = LIST_LABEL_H + 4 + SRC_ARM_SLOT_H + 4;

    // Bone-mapping row geometry (middle column)
    private static final int BONE_ROW_H = 18;
    private static final int BONE_LABEL_W = 96;       // clip bone name
    private static final int BONE_ARROW_W = 18;       // "->"
    private static final int BONE_COMBO_H = 14;

    // Inline widget dimensions for rows inside BoneMappingPanel
    // (template combo / import / export-as-template).
    private static final int TEMPLATE_BTN_H = BONE_COMBO_H;
    private static final int IMPORT_BTN_W = 40;
    private static final int EXPORT_AS_TEMPLATE_BTN_W = 120;

    private static final long INCOMPAT_MESSAGE_DURATION_MS = 5000;

    /// Active instance for routing global events (file drops). Set on `open(...)`,
    /// cleared on `destroy()` — `AkytheraEditorLayoutManager.closeModal()` invokes
    /// `CloseableWidget#destroy` on the closing modal.
    @Nullable
    private static AnimationRetargetModal active = null;

    public static void open(Font font) {
        AnimationRetargetModal modal = new AnimationRetargetModal(
            font,
            AnchoredWidget.fraction(85),
            AnchoredWidget.fraction(90),
            Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_TITLE)
        );
        active = modal;
        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Routed from `MainEditorScreen.onFilesDrop` so dropping animation files anywhere
    /// over the screen reaches the open retarget modal.
    public static boolean onFilesDrop(List<Path> paths) {
        if (active == null) return false;
        active.handleDrops(paths);
        return true;
    }

    private final Font font;

    private final AdvancedEditBox<String> modelSearchBox;
    private final AdvancedEditBox<String> armatureSearchBox;
    private final ModelList modelList;
    private final ArmatureList armatureList;

    private final Model3DViewer preview;

    private final AdvancedEditBox<String> exportPathBox;
    private final AnchoredButton exportPathBrowseBtn;
    private final AnchoredButton exportAllBtn;
    private final AnchoredButton closeScreenButton;

    private boolean generateRootBone = false;

    /// Entries whose last export attempt threw.
    private final Set<RetargetEntry> failedExports = new HashSet<>();

    /// Bones extracted from the currently-selected armature. Drives the candidate set in
    /// every bone-mapping combo box.
    @Nullable
    private AssetAccessor<? extends Armature> currentArmature;
    private List<String> currentArmatureBoneNames = List.of();

    @SuppressWarnings("unused")
    @Nullable
    private AssetAccessor<AnimationClip> previewAnimation;

    private final AnimList animList;
    private final BoneMappingPanel boneMappingPanel;

    private List<TemplateEntry> templateEntries = List.of();
    @Nullable
    private TemplateEntry selectedTemplate;

    /// Source armature loaded from an external drop. Used by `JsonAnimationLoader` to
    /// unbind keyframe local transforms. Not part of the project's armatures.
    @Nullable
    private AssetAccessor<Armature> sourceArmature;
    @Nullable
    private String sourceArmatureFilename;

    /// Dropped-clip list. Selection-independent baseline.
    private final List<RetargetEntry> entries = new ArrayList<>();
    /// Entries that receive the same MappingTemplate edits as the focused entry.
    private final Set<RetargetEntry> selectedEntries = new LinkedHashSet<>();
    /// The entry whose clip bones populate the Bone Mapping rows.
    @Nullable
    private RetargetEntry focusedEntry;

    /// Inline warning shown for ~3s on a rejected Ctrl-click selection.
    @Nullable
    private Component incompatMessage;
    private long incompatMessageUntil;

    /// Inline error shown for ~3s when a dropped file fails to bake.
    @Nullable
    private Component failedReadMessage;
    private long failedReadMessageUntil;

    public AnimationRetargetModal(Font font, Unit x2, Unit y2, Component message) {
        super(x2, y2, message);

        this.font = font;
        this.modelList = new ModelList();
        this.modelList.rebuild("");
        this.armatureList = new ArmatureList();
        this.armatureList.rebuild("");
        this.animList = new AnimList();
        this.boneMappingPanel = new BoneMappingPanel();

        this.modelSearchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(1), AnchoredWidget.fraction(LEFT_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            modelList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        // Armature search anchors to the modelList's bottom — laid out after relocate().
        this.armatureSearchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(1), AnchoredWidget.fraction(LEFT_PCT),
            AnchoredWidget.fraction(40), AnchoredWidget.pixel(SEARCH_H),
            HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            armatureList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

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
        // bottom offset (BOTTOM_HEIGHT y1) = WARNING_AREA_H + (FOOTER_ROW_H - FOOTER_WIDGET_H) / 2.
        int widgetBottomOffset = WARNING_AREA_H + (FOOTER_ROW_H - FOOTER_WIDGET_H) / 2;

        this.exportPathBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(8 + EXPORT_TO_LABEL_W + 4),                       // left = right of label
            AnchoredWidget.pixel(8 + EXPORT_BTN_W + BROWSE_BTN_W + 8),             // right offset
            AnchoredWidget.pixel(widgetBottomOffset), AnchoredWidget.pixel(FOOTER_WIDGET_H),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.BOTTOM_HEIGHT,
            () -> defaultExportPath(),
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
                Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_EXPORT_ALL),
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
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        refreshTemplateList();
    }

    private static String defaultExportPath() {
        return AkytheraProject.getCurrentProject().getProjectRoot()
            .resolve("assets").resolve("animmodels").resolve("animationsToReplace")
            .toString();
    }

    /// Left column splits into 3 equal-height sections (preview models / armatures /
    /// animationsToReplace-to-retarget) plus a fixed one-row "Source Armature" slot between
    /// armatures and animationsToReplace. Pixel heights are recomputed every relocate.
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        // 1. Run base relocate — positions the modal and runs an initial child iteration
        //    against the old y units.
        super.relocate(screenRectangle);

        // 2. Lay out the left column in pixels relative to the modal top.
        int usable = getHeight() - TITLE_H - BOTTOM_BAR_H - SRC_ARM_SECTION_H;
        int sectionH = usable / 3;

        int modelSectionTop = TITLE_H + 4;
        int armSectionTop = TITLE_H + sectionH;
        int srcArmSectionTop = TITLE_H + sectionH * 2;
        int animSectionTop = srcArmSectionTop + SRC_ARM_SECTION_H;

        // Model section (search + label + list)
        modelSearchBox.setY1(AnchoredWidget.pixel(modelSectionTop));
        modelSearchBox.setY2(AnchoredWidget.pixel(SEARCH_H));

        int modelListTop = modelSectionTop + SEARCH_H + LIST_LABEL_H;
        modelList.setY1(AnchoredWidget.pixel(modelListTop));
        modelList.setY2(AnchoredWidget.pixel(Math.max(0, armSectionTop - modelListTop - 4)));

        // Armature section
        armatureSearchBox.setY1(AnchoredWidget.pixel(armSectionTop));
        armatureSearchBox.setY2(AnchoredWidget.pixel(SEARCH_H));

        int armListTop = armSectionTop + SEARCH_H + LIST_LABEL_H;
        armatureList.setY1(AnchoredWidget.pixel(armListTop));
        armatureList.setY2(AnchoredWidget.pixel(Math.max(0, srcArmSectionTop - armListTop - 4)));

        // Animations-to-Retarget list, inside the dark backplate
        int animListTop = animSectionTop + LIST_LABEL_H;
        int animListBottom = getHeight() - BOTTOM_BAR_H - 4;
        animList.setY1(AnchoredWidget.pixel(animListTop));
        animList.setY2(AnchoredWidget.pixel(Math.max(0, animListBottom - animListTop)));

        // Bone Mapping panel (middle column): from just below the "Bone Mapping" header to
        // the bottom bar, between the left and right column separators. The Template /
        // Bones / Export-as-Template strips live as rows inside the list.
        int boneLeft = (int)(getWidth() * LEFT_PCT    / 100.0F) + 4;
        int boneRight = getWidth() - (int)(getWidth() * PREVIEW_PCT / 100.0F);
        int boneTop = TITLE_H + LIST_LABEL_H + 8;
        int boneBottom = getHeight() - BOTTOM_BAR_H - 4;
        boneMappingPanel.setX1(AnchoredWidget.pixel(boneLeft));
        boneMappingPanel.setX2(AnchoredWidget.pixel(Math.max(0, boneRight - boneLeft)));
        boneMappingPanel.setY1(AnchoredWidget.pixel(boneTop));
        boneMappingPanel.setY2(AnchoredWidget.pixel(Math.max(0, boneBottom - boneTop)));

        // 3. Re-relocate the widgets whose y was just rewritten + the bone mapping panel.
        ScreenRectangle rect = getRectangle();
        modelSearchBox.relocate(rect);
        modelList.relocate(rect);
        armatureSearchBox.relocate(rect);
        armatureList.relocate(rect);
        animList.relocate(rect);
        boneMappingPanel.relocate(rect);
    }

    /// Top-Y of the "Source Armature" section in absolute screen coords.
    private int srcArmSectionTopY() {
        int usable = getHeight() - TITLE_H - BOTTOM_BAR_H - SRC_ARM_SECTION_H;
        int sectionH = usable / 3;

        return getY() + TITLE_H + sectionH * 2;
    }

    /// Top-Y of the "Animations to Retarget" section in absolute screen coords.
    private int animSectionTopY() {
        return srcArmSectionTopY() + SRC_ARM_SECTION_H;
    }

    @Override
    public void onResizeCompleted() {
        preview.onResizeCompleted();
    }

    @Override
    public void destroy() {
        preview.destroy();
        if (active == this) active = null;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(
            closeScreenButton,
            modelSearchBox, modelList,
            armatureSearchBox, armatureList,
            animList,
            boneMappingPanel,
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
            || armatureList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || animList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || boneMappingPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
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

        // Bottom-bar geometry: top FOOTER_ROW_H carries widgets, bottom WARNING_AREA_H reserved for messages.
        int bottomY = getBottom() - BOTTOM_BAR_H;
        int footerEndY = bottomY + FOOTER_ROW_H;
        g.fill(getX(), bottomY, getRight(), getBottom(), 0xFF222222);
        g.fill(getX(), bottomY, getRight(), bottomY + 1, 0xFF3A3A3A);

        // Separator between footer row and warning area
        g.fill(getX() + 4, footerEndY, getRight() - 4, footerEndY + 1, 0xFF2E2E2E);

        // "Export To:" label inline with the footer widgets
        int labelY = bottomY + (FOOTER_ROW_H - font.lineHeight) / 2;
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_EXPORT_TO), getX() + 8, labelY, 0xFFAAAAAA, false);

        // Vertical separators between left / middle / right columns
        int leftRight = getX() + (int)(getWidth() * (LEFT_PCT / 100.0F)) + 3;
        int rightLeft = getRight() - (int)(getWidth() * (PREVIEW_PCT / 100.0F));
        g.fill(leftRight - 1, getY() + TITLE_H, leftRight, bottomY, 0xFF333333);
        g.fill(rightLeft - 1, getY() + TITLE_H, rightLeft, bottomY, 0xFF333333);

        // "Source Armature" — one-row slot between armatures and animationsToReplace.
        int srcArmTop = srcArmSectionTopY();
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_SOURCE_ARMATURE), getX() + 6, srcArmTop + 2, 0xFFAAAAAA, false);
        int slotTop = srcArmTop + LIST_LABEL_H + 2;
        int slotBottom = slotTop + SRC_ARM_SLOT_H;
        int slotLeft = getX() + 4;
        int slotRight = leftRight - 4;

        // Dashed-ish (1px stipple) outline + dark fill so it reads as a drop target.
        g.fill(slotLeft, slotTop, slotRight, slotBottom, 0xFF111111);
        g.outline(slotLeft, slotTop, slotRight - slotLeft, slotBottom - slotTop, sourceArmature == null ? 0xFF555555 : 0xFF37D5FF);

        Component label = sourceArmature == null
            ? Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_MESSAGE_DRAG_DROP_ARMATURE_HERE)
            : Component.literal(sourceArmatureFilename == null ? "" : sourceArmatureFilename);
        int lblW = font.width(label);
        int lblX = slotLeft + Math.max(4, ((slotRight - slotLeft) - lblW) / 2);
        int lblY = slotTop + (SRC_ARM_SLOT_H - font.lineHeight) / 2;
        g.text(font, label, lblX, lblY, sourceArmature == null ? 0xFF888888 : 0xFFFFFFFF, false);

        // "Animations to Retarget" — dark backplate over the bottom-most section of the left column.
        int animTop = animSectionTopY();
        g.fill(getX() + 1, animTop, leftRight - 1, bottomY, 0xFF161616);
        g.fill(getX() + 1, animTop, leftRight - 1, animTop + 1, 0xFF3A3A3A);
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_ANIMATIONS_TO_RETARGET), getX() + 6, animTop + 4, 0xFFAAAAAA, false);

        // Hint in the animationsToReplace area when source armature is ready and no entries yet.
        if (sourceArmature != null && animList.children().isEmpty()) {
            Component hint = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_MESSAGE_DRAG_AND_DROP_ANIMATIONS);
            List<FormattedCharSequence> split = font.split(hint, animList.getWidth() - 4);
            int hintW = font.width(hint);
            int hintX = getX() + 1 + ((leftRight - 1) - (getX() + 1) - hintW) / 2;
            int hintY = animTop + (bottomY - animTop) / 2 - (font.lineHeight * split.size()) / 2;

            for (FormattedCharSequence fcs : split) {
                g.text(font, fcs, Math.max(getX() + 6, hintX), hintY, 0xFF888888, false);
                hintY += font.lineHeight;
            }
        }

        // Section labels above each list
        int labelOriginX = getX() + 6;
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_PREVIEW_MODEL), labelOriginX, modelList.getY() - LIST_LABEL_H + 2, 0xFFAAAAAA, false);
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_ARMATURES), labelOriginX, armatureList.getY() - LIST_LABEL_H + 2, 0xFFAAAAAA, false);

        // "Bone Mapping" header
        g.fill(leftRight + 1, getY() + TITLE_H + 1, rightLeft - 1, boneMappingPanel.getY(), 0xFF111111);
        g.text(font, Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_BONE_MAPPING), leftRight + 6, getY() + TITLE_H + 4, 0xFFCCCCCC, false);

        for (GuiEventListener e : children()) {
            if (e instanceof AbstractWidget w) w.extractRenderState(g, mouseX, mouseY, a);
        }

        // Inline incompat warning under the animationsToReplace list, fades after duration.
        long now = System.currentTimeMillis();
        int warnBaseY = getBottom() - BOTTOM_BAR_H - 12;

        if (failedReadMessage != null) {
            if (now > failedReadMessageUntil) {
                failedReadMessage = null;
            } else {
                int y = incompatMessage != null && now <= incompatMessageUntil
                    ? warnBaseY - font.lineHeight - 2
                    : warnBaseY;
                g.text(font, failedReadMessage, getX() + 6, y, 0xFFFF5555, false);
            }
        }

        if (incompatMessage != null) {
            if (now > incompatMessageUntil) {
                incompatMessage = null;
            } else {
                g.text(font, incompatMessage, getX() + 6, warnBaseY, 0xFFFF8866, false);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// Re-applies preview state to the previewer after a model swap. The new
    /// `EntityModelComponent` has a fresh `SimpleHeadlessAnimator`, so the previously-
    /// built `previewAnimation` needs to be pushed onto it again — no need to re-remap.
    private void replayPreviewState() {
        applyPreviewAnimationToComponent();
    }

    /// Rebuilds `previewAnimation` from the focused entry's clip + template and pushes it
    /// onto the previewer. Cleared (→ bind pose) when no entry is focused.
    private void reloadPreviewAnimation() {
        if (focusedEntry == null) {
            previewAnimation = null;
        } else {
            AnimationClip remapped = AnimationRetargetManager.INSTANCE.remap(focusedEntry.clip, focusedEntry.template);
            Identifier id = AkytheraLabs.identifier("retarget_preview/" + focusedEntry.source.getFileName().toString().toLowerCase());
            previewAnimation = new AnimationRetargetManager.DirectAccessor(id, remapped);
        }

        applyPreviewAnimationToComponent();
    }

    /// Pushes the current `previewAnimation` to the previewer's `EntityModelComponent`.
    /// Reaches through `getAnimator()` to clear (null), since the component's own
    /// `setSimplePlayingAnimation` is `@NonNull` while `SimpleHeadlessAnimator.setAnimation`
    /// accepts `@Nullable`.
    private void applyPreviewAnimationToComponent() {
        EntityModelComponent c = preview.getComponent(EntityModelComponent.class);

        if (c == null) return; // no model selected yet

        if (previewAnimation != null) {
            c.setSimplePlayingAnimation(previewAnimation);
        } else if (c.getAnimator() instanceof SimpleHeadlessAnimator anim) {
            anim.setAnimation(null);
        }
    }

    /// Called when the user selects an armature row. Re-collects the candidate-bone list,
    /// resets every existing entry's template (target armature changed), and rebuilds the
    /// bone-mapping panel.
    private void onSelectArmature(AssetAccessor<? extends Armature> accessor) {
        currentArmature = accessor;
        Armature arm = accessor.get();
        List<String> names = new ArrayList<>(arm.getBoneCount());
        arm.visitBones(b -> names.add(b.getName()));
        currentArmatureBoneNames = names;

        // Existing entries lose their mappings — target armature changed.
        Identifier newId = accessor.identifier();
        for (RetargetEntry e : entries) {
            e.template.setArmatureId(newId);
            e.template.mapping().clear();
        }

        selectedTemplate = null;
        refreshTemplateList();
        failedExports.clear();          // prior failures referenced now-stale mappings

        rebuildBoneMappingPanel();      // rebuilds the TemplateRow combo with the new armature's templates
        reloadPreviewAnimation();       // wiped templates → preview goes back to bind pose
    }

    /// Project root subdirectory where templates live.
    private Path templatesDir() {
        return AkytheraProject.getCurrentProject().getProjectRoot().resolve("retargeter_templates");
    }

    /// Refresh `templateEntries` from disk. Cheap enough to call on armature swap / import.
    private void refreshTemplateList() {
        templateEntries = AnimationRetargetManager.INSTANCE.loadTemplates(templatesDir());
    }

    private void onPickTemplate(@Nullable TemplateEntry picked) {
        selectedTemplate = picked;

        if (focusedEntry == null) return;

        if (picked != null) {
            // Replace mapping on the focused entry + every selected entry with the
            // template's. `(none)` leaves existing picks alone — the lower-surprise choice.
            Map<String, String> tmplMap = picked.template().mapping();
            Identifier armId = picked.template().armatureId();
            for (RetargetEntry e : selectedEntries) {
                e.template.mapping().clear();
                e.template.mapping().putAll(tmplMap);
                e.template.setArmatureId(armId);
            }
        }

        rebuildBoneMappingPanel();
        reloadPreviewAnimation();
    }

    private void onPressImport() {
        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_openFileDialog(
                "Select Retarget Template", "", null, "Template JSON (*.json)", false))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                try {
                    Path dest = AnimationRetargetManager.INSTANCE.importTemplate(
                        Path.of(result), templatesDir());
                    refreshTemplateList();
                    rebuildBoneMappingPanel();
                    selectImportedIfCompatible(dest);
                } catch (IOException e) {
                    AkytheraLabs.LOGGER.error("Failed to import template", e);
                    showFailedReadMessage();
                }
            }, Minecraft.getInstance());
    }

    /// Auto-select the freshly imported template if its armature matches the modal's
    /// selection; otherwise flash the incompat warning. The file is already on disk in
    /// either case.
    private void selectImportedIfCompatible(Path dest) {
        TemplateEntry imported = templateEntries.stream()
            .filter(e -> e.file().equals(dest))
            .findFirst().orElse(null);
        if (imported == null) return;

        if (currentArmature != null
            && Objects.equals(imported.template().armatureId(), currentArmature.identifier())) {
            onPickTemplate(imported);
        } else {
            incompatMessage = Component.translatable(
                LangKeys.GUI_ANIMATION_RETARGET_MODAL_MESSAGE_TEMPLATE_INCOMPATIBLE);
            incompatMessageUntil = System.currentTimeMillis() + INCOMPAT_MESSAGE_DURATION_MS;
        }
    }

    private void onPressBrowseExportPath() {
        String current = exportPathBox.getValue();
        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog(
                "Choose Export Folder", current))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                exportPathBox.setValueByString(result);
            }, Minecraft.getInstance());
    }

    /// Export every entry as a `.akasset` into the user-selected directory. Partial
    /// mappings are valid — `remap` drops unmapped clip-bones safely. Per-entry I/O or
    /// encoding failures red-mark the affected row; other entries still export.
    private void onPressExportAll() {
        if (entries.isEmpty() || sourceArmature == null || currentArmature == null) return;

        Path destDir = Path.of(exportPathBox.getValue());
        List<ExportResult> results = AnimationRetargetManager.INSTANCE.exportAll(
            new ArrayList<>(entries),
            sourceArmature.get(),
            currentArmature.get(),
            destDir,
            generateRootBone
        );

        failedExports.clear();

        for (ExportResult r : results) {
            if (r instanceof ExportResult.Failure f) failedExports.add(f.entry());
        }

        if (failedExports.isEmpty()) {
            Util.getPlatform().openFile(destDir.toFile());
        } else {
            failedReadMessage = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_MESSAGE_EXPORT_FAILED);
            failedReadMessageUntil = System.currentTimeMillis() + INCOMPAT_MESSAGE_DURATION_MS;
        }
    }

    private void onPressExportAsTemplate() {
        if (focusedEntry == null) return;
        // No completeness gate — `remap()` skips unmapped bones safely, so partial
        // templates are valid artifacts. Unfilled rows render with a red combo outline.

        Path defaultPath = templatesDir().resolve("untitled.json");
        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Retarget Template", defaultPath.toString(),
                null, "Template JSON (*.json)"))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                Path dest = Path.of(result.endsWith(".json") ? result : result + ".json");
                if (AnimationRetargetManager.INSTANCE.saveTemplate(dest, focusedEntry.template)) {
                    Util.getPlatform().openFile(dest.toFile());
                    refreshTemplateList();
                    rebuildBoneMappingPanel();
                } else {
                    showFailedReadMessage();
                }
            }, Minecraft.getInstance());
    }

    /// Route each dropped file to either the source-armature slot or the animation list.
    /// Tries armature-first when the source slot is empty, animation-first when it's set;
    /// the other loader is the fallback. Failed files trigger the inline "Failed at
    /// reading animation file." alarm.
    private void handleDrops(List<Path> paths) {
        for (Path src : paths) {
            String name = src.getFileName().toString();
            if (!name.endsWith(".json") && !name.endsWith("." + AssetLoader.BINARY_EXTENSION)) continue;

            Identifier id = synthIdFromDropPath(src);
            SingleFileResourceManager rm = new SingleFileResourceManager(src);

            boolean armatureFirst = sourceArmature == null;
            boolean ok = armatureFirst
                ? tryLoadAsArmature(name, id, rm) || tryLoadAsAnimation(src, id, rm)
                : tryLoadAsAnimation(src, id, rm) || tryLoadAsArmature(name, id, rm);

            if (!ok) {
                AkytheraLabs.LOGGER.error("Drop {} matched neither armature nor animation loader", src);
                showFailedReadMessage();
            }
        }

        animList.rebuild();
    }

    private boolean tryLoadAsArmature(String filename, Identifier id, SingleFileResourceManager rm) {
        try {
            Armature arm = ArmatureLoader.getLoader(id, rm).loadArmature(Armature::new);
            sourceArmature = new InMemoryArmatureAccessor(id, arm);
            sourceArmatureFilename = filename;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryLoadAsAnimation(Path src, Identifier id, SingleFileResourceManager rm) {
        if (sourceArmature == null) return false;       // JsonAnimationLoader needs an armature

        try {
            AnimationClip clip = AnimationLoader.getLoader(id, rm).loadAnimationClip(sourceArmature);
            Set<String> boneNames = new LinkedHashSet<>();
            clip.visitBoneTracks((bone, _) -> boneNames.add(bone));

            Identifier templateArmId = currentArmature == null ? null : currentArmature.identifier();
            RetargetEntry entry = new RetargetEntry(
                src, clip, boneNames,
                new AnimationRetargetManager.MappingTemplate(templateArmId)
            );

            entries.add(entry);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showFailedReadMessage() {
        failedReadMessage = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_MESSAGE_FAILED_TO_READ);
        failedReadMessageUntil = System.currentTimeMillis() + INCOMPAT_MESSAGE_DURATION_MS;
    }

    /// Drop files live outside the project resource manager — we only need a unique
    /// identifier for the loader.
    private static Identifier synthIdFromDropPath(Path src) {
        String safe = src.getFileName().toString()
            .replaceFirst("\\.(json|" + AssetLoader.BINARY_EXTENSION + ")$", "")
            .toLowerCase().replaceAll("[^a-z0-9_/.-]", "_");
        return AkytheraLabs.identifier("retarget_drop/" + safe);
    }

    /// Click rules: plain = clear-and-focus, Ctrl = toggle in selection with compat check,
    /// Shift = fill selection from `focusedEntry` to clicked. Focus is the anchor — Shift
    /// never moves it, so the user can keep adjusting the range from the same primary.
    private void onClickEntry(RetargetEntry e, boolean ctrl, boolean shift) {
        if (shift && focusedEntry != null) {
            int from = entries.indexOf(focusedEntry);
            int to = entries.indexOf(e);
            if (from < 0 || to < 0) return;

            int lo = Math.min(from, to);
            int hi = Math.max(from, to);

            for (int i = lo; i <= hi; i++) {
                RetargetEntry r = entries.get(i);

                if (r != focusedEntry && !areCompatible(focusedEntry, r)) {
                    showIncompatMessage();
                    return;
                }
            }

            selectedEntries.clear();

            for (int i = lo; i <= hi; i++) {
                selectedEntries.add(entries.get(i));
            }

            reloadPreviewAnimation();

            return;
        }

        if (ctrl) {
            if (selectedEntries.contains(e)) {
                selectedEntries.remove(e);
            } else {
                if (focusedEntry != null && !areCompatible(focusedEntry, e)) {
                    showIncompatMessage();
                    return;
                }

                selectedEntries.add(e);
            }
        } else {
            selectedEntries.clear();
            selectedEntries.add(e);
            focusedEntry = e;
            rebuildBoneMappingPanel();
        }

        reloadPreviewAnimation();
    }

    /// Two entries are compat-multi-selectable if one's bone set is a subset of the other's.
    private static boolean areCompatible(RetargetEntry a, RetargetEntry b) {
        return a.boneNames.containsAll(b.boneNames) || b.boneNames.containsAll(a.boneNames);
    }

    private void showIncompatMessage() {
        incompatMessage = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_INCOMPATIBLE);
        incompatMessageUntil = System.currentTimeMillis() + INCOMPAT_MESSAGE_DURATION_MS;
    }

    private void rebuildBoneMappingPanel() {
        boneMappingPanel.rebuild();
    }

    /// Propagates a bone pick to every selected entry's template, then rebuilds the panel so each
    /// row refreshes its available-targets list.
    private void onPick(String clipBone, @Nullable String pickedArmBone) {
        if (focusedEntry == null) return;

        for (RetargetEntry e : selectedEntries) {
            if (pickedArmBone == null || pickedArmBone.isEmpty()) {
                e.template.clearMapping(clipBone);
            } else {
                e.template.putMapping(clipBone, pickedArmBone);
            }
        }

        // Diverged from the template — back to freeform mode. Combo gets reset when the
        // panel rebuilds (TemplateRow's ctor reads `selectedTemplate`).
        selectedTemplate = null;

        rebuildBoneMappingPanel();
        reloadPreviewAnimation();
    }

    /// ---------------- Model rows ----------------

    private sealed interface ModelRow permits TitleRow, SkeletalMeshRow, VanillaModelRow {
        Component label();
    }

    private record TitleRow(Component label) implements ModelRow {}

    private record SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) implements ModelRow {
        @Override
        public Component label() {
            return Component.literal(accessor.identifier().toString());
        }
    }

    private record VanillaModelRow(EntityType<?> type, LivingEntityRenderer<?, ?, ?> renderer) implements ModelRow {
        @Override
        public Component label() {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return Component.literal(id.getPath());
        }
    }

    @SuppressWarnings("unchecked")
    private List<ModelRow> buildModelCatalog(String query) {
        String lower = query.toLowerCase();
        List<ModelRow> rows = new ArrayList<>();

        List<SkeletalMeshRow> skeletal = AkytheraProject.getCurrentProject()
            .listAllMeshAccessors()
            .filter(acc -> acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .map(acc -> new SkeletalMeshRow((AssetAccessor<SkeletalMesh>) acc))
            .filter(row -> lower.isEmpty() || ParseUtils.toLowerCase(row.accessor().identifier()).contains(lower))
            .sorted(Comparator.comparing(row -> row.accessor().identifier().toString()))
            .toList();

        if (!skeletal.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_SKELETAL)));
            rows.addAll(skeletal);
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        List<VanillaModelRow> vanilla = Stream.concat(
                dispatcher.renderers.entrySet().stream()
                    .filter(e -> e.getValue() instanceof LivingEntityRenderer<?, ?, ?>)
                    .map(e -> new VanillaModelRow(e.getKey(), (LivingEntityRenderer<?, ?, ?>) e.getValue())),
                Stream.of(new VanillaModelRow(EntityTypes.PLAYER, dispatcher.playerRenderers.get(DefaultPlayerSkin.getDefaultSkin().model())))
            )
            .filter(row -> lower.isEmpty() || row.label().getString().toLowerCase().contains(lower))
            .sorted(Comparator.comparing(row -> row.label().getString()))
            .toList();

        if (!vanilla.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_VANILLA)));
            rows.addAll(vanilla);
        }

        return rows;
    }

    private void onSelectModelRow(ModelRow row) {
        switch (row) {
            case SkeletalMeshRow sr -> preview.displayEntity(
                sr.accessor().identifier(),
                EntityModelComponent.ModelType.SKELETAL_MESH,
                true, true
            );
            case VanillaModelRow vr -> preview.displayEntity(
                BuiltInRegistries.ENTITY_TYPE.getKey(vr.type()),
                EntityModelComponent.ModelType.VANILLA_ENTITY,
                true, true
            );
            case TitleRow _ -> {}
        }
        replayPreviewState();
    }

    private final class ModelList extends AbstractSelectionList<ModelList.@NonNull Entry> implements AnchoredWidget {
        ModelList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();
            for (ModelRow row : buildModelCatalog(query)) {
                if (row instanceof TitleRow tr) addEntry(new TitleEntry(tr));
                else addEntry(new ModelEntry(row));
            }
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override public int getRowWidth()    { return getWidth() - SCROLLBAR_WIDTH; }
        @Override public int scrollbarWidth() { return SCROLLBAR_W; }
        @Override protected int scrollerHeight() {
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
        }
        @Override protected int scrollBarX()  { return getRight() - scrollbarWidth(); }
        @Override protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {}
        @Override protected boolean entriesCanBeSelected() { return true; }

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
            if (selected != null) onSelectModelRow(selected.getRow());
        }

        @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        abstract static class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            abstract ModelRow getRow();
        }

        final class TitleEntry extends Entry {
            private final TitleRow row;
            TitleEntry(TitleRow row) { this.row = row; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF141414);
                g.text(font, row.label(), getX() + 6, getY() + (getHeight() - 8) / 2, 0xFF888888, false);
            }

            @Override public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) { return false; }
            @Override ModelRow getRow() { return row; }
        }

        final class ModelEntry extends Entry {
            private final ModelRow row;
            ModelEntry(ModelRow row) { this.row = row; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }
                g.text(font, row.label(), getX() + 12, getY() + (getHeight() - 8) / 2,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean doubleClicked) {
                setSelected(this);
                return true;
            }

            @Override ModelRow getRow() { return row; }
        }

        // y1/y2 are set every frame by the modal's relocate() — initial values just keep
        // the widget non-degenerate before the first layout pass.
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(LEFT_PCT);
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

    /// ---------------- Armature list ----------------

    private record ArmatureRow(AssetAccessor<? extends Armature> accessor) {
        Component label() {
            Identifier id = accessor.identifier();
            String path = id.getPath();
            if (path.startsWith(ArmatureManager.DIRECTORY)) {
                path = path.substring(ArmatureManager.DIRECTORY.length());
            }
            return Component.literal(id.getNamespace() + ":" + path);
        }
    }

    private List<ArmatureRow> buildArmatureCatalog(String query) {
        String lower = ParseUtils.toLowerCase(query);
        return AkytheraProject.getCurrentProject()
            .listAllArmatureAccessors()
            .filter(a -> lower.isEmpty() || ParseUtils.toLowerCase(a.identifier()).contains(lower))
            .sorted(Comparator.comparing(a -> a.identifier().toString()))
            .map(ArmatureRow::new)
            .toList();
    }

    private final class ArmatureList extends AbstractSelectionList<ArmatureList.@NonNull Entry> implements AnchoredWidget {
        ArmatureList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();

            for (ArmatureRow row : buildArmatureCatalog(query)) {
                addEntry(new Entry(row));
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
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
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
            if (selected != null) onSelectArmature(selected.row.accessor());
        }

        @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            final ArmatureRow row;
            Entry(ArmatureRow row) { this.row = row; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }
                g.text(font, row.label(), getX() + 6, getY() + (getHeight() - 8) / 2,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean doubleClicked) {
                setSelected(this);
                return true;
            }
        }

        // y1/y2 are set every frame by the modal's relocate(). Initial zeros are harmless.
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(LEFT_PCT);
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

    /// ---------------- Animations-to-Retarget list ----------------

    private final class AnimList extends AbstractSelectionList<AnimList.@NonNull Entry> implements AnchoredWidget {
        AnimList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild() {
            clearEntries();
            for (RetargetEntry re : entries) addEntry(new Entry(re));
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
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
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
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            final RetargetEntry entry;

            Entry(RetargetEntry entry) { this.entry = entry; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                boolean isSel = selectedEntries.contains(entry);
                boolean isFoc = focusedEntry == entry;

                if (isSel) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF0A2833);
                } else if (hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                if (isFoc) {
                    g.outline(getX(), getY(), getWidth(), getHeight(), 0xFFFFCC00);
                }

                boolean failed = failedExports.contains(entry);

                int textColor = failed
                    ? 0xFFFF5555
                    : isSel || isFoc
                        ? 0xFFFFFFFF
                        : 0xFFCCCCCC;

                g.text(font, Component.literal(entry.label()), getX() + 6, getY() + (getHeight() - 8) / 2, textColor, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                if (ev.button() != 0) return false;
                int mods = ev.modifiers();
                boolean ctrl = (mods & InputConstants.MOD_CONTROL) != 0;
                boolean shift = (mods & InputConstants.MOD_SHIFT) != 0;
                onClickEntry(entry, ctrl, shift);

                return true;
            }
        }

        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(LEFT_PCT);
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

    // ---------------- Bone Mapping list ----------------

    /// Scrollable list of bone-mapping rows + the Template/Bones sections inline. Switched
    /// to `ContainerObjectSelectionList` so each row can host child widgets — the template
    /// combo + Import button live inside `TemplateRow`, the per-bone combo inside `MappingRow`,
    /// and the Export-as-Template button inside `ExportAsTemplateRow`. Row order:
    ///   `SectionTitleRow("Template")` → `LineSeparatorRow` → `TemplateRow`
    ///   `SectionTitleRow("Bones")`    → `LineSeparatorRow` → one `MappingRow` per clip bone
    ///   `ExportAsTemplateRow`
    private final class BoneMappingPanel extends ContainerObjectSelectionList<BoneMappingRow> implements AnchoredWidget {
        BoneMappingPanel() {
            super(Minecraft.getInstance(), 0, 0, 0, BONE_ROW_H);
        }

        void rebuild() {
            clearEntries();

            if (focusedEntry == null || currentArmature == null) {
                return;
            }

            addEntry(new SectionTitleRow(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_TEMPLATE));
            addEntry(new LineSeparatorRow());
            addEntry(new TemplateRow());
            addEntry(new SectionTitleRow(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_BONES));
            addEntry(new LineSeparatorRow());

            for (String clipBone : focusedEntry.boneNames) {
                addEntry(new MappingRow(clipBone));
            }

            addEntry(new LineSeparatorRow());
            addEntry(new ExportAsTemplateRow());
            addEntry(new SectionTitleRow(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_OPTIONS));
            addEntry(new LineSeparatorRow());
            addEntry(new OptionsRow());

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
            return Mth.clamp((int)((float)(height * height) / (float)contentHeight()), 8, height - 8);
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

        // y1/y2 (and x1/x2) are set every frame by the modal's relocate().
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

    /// Base class for every entry in `BoneMappingPanel`. Exists only to fix the recursive
    /// type parameter — `ContainerObjectSelectionList.Entry<E>` requires `E extends Entry<E>`.
    private abstract static class BoneMappingRow extends ContainerObjectSelectionList.Entry<BoneMappingRow> {
        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /// Section title row (e.g. "Template", "Bones"). Renders the label left-aligned, no children.
    private final class SectionTitleRow extends BoneMappingRow {
        private final Component label;

        SectionTitleRow(String langKey) {
            this.label = Component.translatable(langKey);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            g.text(font, label, getX() + 4, getY() + getHeight() - font.lineHeight, 0xFFCCCCCC, false);
        }
    }

    /// Thin horizontal divider drawn through the middle of the row's height band.
    private static final class LineSeparatorRow extends BoneMappingRow {
        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int midY = getY() + getHeight() / 2;
            g.fill(getX() + 4, midY, getX() + getWidth() - 4, midY + 1, 0xFF333333);
        }
    }

    /// Template picker + Import button. Combo items are filtered to templates whose
    /// `armatureId` matches `currentArmature`. The row is reconstructed on every
    /// `BoneMappingPanel.rebuild()` so changes to `templateEntries` / `currentArmature` /
    /// `selectedTemplate` flow through naturally.
    private final class TemplateRow extends BoneMappingRow {
        private final AdvancedComboBox<TemplateEntry> combo;
        private final AnchoredButton importBtn;
        private final List<GuiEventListener> children;

        TemplateRow() {
            List<TemplateEntry> compatible = currentArmature == null
                ? List.of()
                : templateEntries.stream()
                    .filter(e -> Objects.equals(e.template().armatureId(), currentArmature.identifier()))
                    .toList();

            this.combo = new AdvancedComboBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(TEMPLATE_BTN_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> selectedTemplate,
                AnimationRetargetModal.this::onPickTemplate,
                Math.clamp(compatible.size() + 1, 4, 12),
                Component.empty(),
                compatible,
                TemplateEntry::displayName
            );

            if (selectedTemplate != null && compatible.contains(selectedTemplate)) {
                combo.setValueQuiet(selectedTemplate);
            }

            this.importBtn = AnchoredButton.buttonBuilder(
                    font,
                    Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_IMPORT_TEMPLATE),
                    _ -> onPressImport()
                )
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(IMPORT_BTN_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TEMPLATE_BTN_H))
                .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build();

            this.children = List.of(combo, importBtn);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(combo, importBtn);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            Component label = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_SECTION_TEMPLATE);
            int labelX = getX() + 4;
            int labelY = getY() + (getHeight() - font.lineHeight) / 2;
            int labelW = font.width(label) + 8;
            g.text(font, label, labelX, labelY, 0xFFAAAAAA, false);

            int rowRight = getX() + getWidth();
            int btnY = getY() + (getHeight() - TEMPLATE_BTN_H) / 2;
            int importX = rowRight - IMPORT_BTN_W - 4;
            int comboLeft = labelX + labelW;
            int comboW = Math.max(0, importX - 6 - comboLeft);

            int modalX = AnimationRetargetModal.this.getX();
            int modalY = AnimationRetargetModal.this.getY();
            ScreenRectangle modalRect = AnimationRetargetModal.this.getRectangle();

            combo.setX1(AnchoredWidget.pixel(comboLeft - modalX));
            combo.setX2(AnchoredWidget.pixel(comboW));
            combo.setY1(AnchoredWidget.pixel(btnY - modalY));
            combo.setY2(AnchoredWidget.pixel(TEMPLATE_BTN_H));
            combo.relocate(modalRect);

            importBtn.setX1(AnchoredWidget.pixel(importX - modalX));
            importBtn.setX2(AnchoredWidget.pixel(IMPORT_BTN_W));
            importBtn.setY1(AnchoredWidget.pixel(btnY - modalY));
            importBtn.setY2(AnchoredWidget.pixel(TEMPLATE_BTN_H));
            importBtn.relocate(modalRect);

            combo.extractRenderState(g, mx, my, a);
            importBtn.extractRenderState(g, mx, my, a);
        }
    }

    /// One-bone mapping row: `<clipBone>  ->  [combo]`. The combo is marked red when the
    /// row's source bone has no target — works the same under template-lock so the user
    /// sees which slots the template doesn't fill.
    private final class MappingRow extends BoneMappingRow {
        private final String clipBone;
        private final AdvancedComboBox<String> combo;
        private final List<GuiEventListener> children;

        MappingRow(String clipBone) {
            this.clipBone = clipBone;
            Set<String> avail = availableTargets(clipBone);

            this.combo = new AdvancedComboBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BONE_COMBO_H),
                HorizontalAnchorType.RIGHT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> focusedEntry == null ? null : focusedEntry.template.getTarget(clipBone),
                picked -> onPick(clipBone, picked),
                Math.clamp(avail.size() + 1, 8, 12),
                Component.empty(),
                avail,
                Function.identity()
            );

            // Pre-fill from existing template state. Quiet variant: re-binding to the
            // template's current value must not fire onPick — that would recursively call
            // rebuild() and loop forever.
            if (focusedEntry != null) {
                String existing = focusedEntry.template.getTarget(clipBone);
                if (existing != null) combo.setValueQuiet(existing);
            }

            combo.active = (selectedTemplate == null);
            this.children = List.of(combo);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(combo);
        }

        private Set<String> availableTargets(String clipBone) {
            if (focusedEntry == null) return Set.of();

            Set<String> used = new HashSet<>(focusedEntry.template.mapping().values());
            String selfTarget = focusedEntry.template.getTarget(clipBone);
            if (selfTarget != null) used.remove(selfTarget); // keep self pickable

            Set<String> avail = new TreeSet<>();

            for (String armBone : currentArmatureBoneNames) {
                if (!used.contains(armBone)) avail.add(armBone);
            }

            return avail;
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int textBaseY = getY() + (getHeight() - font.lineHeight) / 2;
            int labelX = getX() + 6;
            g.text(font, Component.literal(clipBone), labelX, textBaseY, 0xFFCCCCCC, false);
            g.text(font, Component.literal("->"), labelX + BONE_LABEL_W, textBaseY, 0xFF888888, false);

            int labelEnd = labelX + BONE_LABEL_W + BONE_ARROW_W;
            int comboY = getY() + (getHeight() - BONE_COMBO_H) / 2;
            int comboW = Math.clamp(getRight() - labelEnd - 4, 0, 150);
            int modalY = AnimationRetargetModal.this.getY();
            ScreenRectangle modalRect = AnimationRetargetModal.this.getRectangle();
            int rightLeft = (int)(AnimationRetargetModal.this.getWidth() * (PREVIEW_PCT / 100.0F));

            combo.setX1(AnchoredWidget.pixel(rightLeft + 8));
            combo.setX2(AnchoredWidget.pixel(comboW));
            combo.setY1(AnchoredWidget.pixel(comboY - modalY));
            combo.setY2(AnchoredWidget.pixel(BONE_COMBO_H));
            combo.relocate(modalRect);
            combo.extractRenderState(g, mx, my, a);
        }
    }

    /// Inline "Generate Root Bone" option row. The CheckBox's data provider / consumer
    /// pair targets the modal's `generateRootBone` field, so toggle state survives panel
    /// rebuilds even though the row itself is re-instantiated.
    private final class OptionsRow extends BoneMappingRow {
        private final CheckBox checkbox;
        private final List<GuiEventListener> children;

        OptionsRow() {
            this.checkbox = new CheckBox(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHECKBOX_H),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHECKBOX_H),
                HorizontalAnchorType.LEFT_WIDTH,
                VerticalAnchorType.TOP_HEIGHT,
                () -> generateRootBone,
                v -> generateRootBone = v,
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
            Component label = Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_OPTION_GENERATE_ROOT_BONE);
            int labelX = getX() + 6;
            int labelY = getY() + (getHeight() - font.lineHeight) / 2;
            g.text(font, label, labelX, labelY, 0xFFCCCCCC, false);

            int boxX = labelX + font.width(label) + 6;
            int boxY = getY() + (getHeight() - CHECKBOX_H) / 2;
            int modalX = AnimationRetargetModal.this.getX();
            int modalY = AnimationRetargetModal.this.getY();
            ScreenRectangle modalRect = AnimationRetargetModal.this.getRectangle();

            checkbox.setX1(AnchoredWidget.pixel(boxX - modalX));
            checkbox.setX2(AnchoredWidget.pixel(CHECKBOX_H));
            checkbox.setY1(AnchoredWidget.pixel(boxY - modalY));
            checkbox.setY2(AnchoredWidget.pixel(CHECKBOX_H));
            checkbox.relocate(modalRect);
            checkbox.extractRenderState(g, mx, my, a);
        }
    }

    /// Right-aligned Export-as-Template button at the bottom of the bone-mapping list.
    /// Disabled when a template is selected (re-exporting would just rewrite the source file).
    private final class ExportAsTemplateRow extends BoneMappingRow {
        private final AnchoredButton exportBtn;
        private final List<GuiEventListener> children;

        ExportAsTemplateRow() {
            this.exportBtn = AnchoredButton.buttonBuilder(
                    font,
                    Component.translatable(LangKeys.GUI_ANIMATION_RETARGET_MODAL_EXPORT_AS_TEMPLATE),
                    _ -> onPressExportAsTemplate()
                )
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EXPORT_AS_TEMPLATE_BTN_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TEMPLATE_BTN_H))
                .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build();
            exportBtn.active = (selectedTemplate == null);
            this.children = List.of(exportBtn);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(exportBtn);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int rowRight = getX() + getWidth();
            int btnX = rowRight - EXPORT_AS_TEMPLATE_BTN_W - 6;
            int btnY = getY() + (getHeight() - TEMPLATE_BTN_H) / 2;
            int modalX = AnimationRetargetModal.this.getX();
            int modalY = AnimationRetargetModal.this.getY();
            ScreenRectangle modalRect = AnimationRetargetModal.this.getRectangle();

            exportBtn.setX1(AnchoredWidget.pixel(btnX - modalX));
            exportBtn.setX2(AnchoredWidget.pixel(EXPORT_AS_TEMPLATE_BTN_W));
            exportBtn.setY1(AnchoredWidget.pixel(btnY - modalY));
            exportBtn.setY2(AnchoredWidget.pixel(TEMPLATE_BTN_H));
            exportBtn.relocate(modalRect);
            exportBtn.extractRenderState(g, mx, my, a);
        }
    }

    /// Wraps a dropped, in-memory `Armature` instance as an `AssetAccessor` so it can be
    /// passed to `AnimationLoader.loadAnimationClip`. The asset isn't backed by any
    /// resource manager — `isResourcePresent` returns true unconditionally because the
    /// `Armature` is already in memory.
    private record InMemoryArmatureAccessor(Identifier id, Armature armature) implements AssetAccessor<Armature> {
        @Override
        public Identifier identifier() {
            return id;
        }

        @Override
        public Armature get() {
            return armature;
        }

        @Override
        public Type type() {
            return Type.ARMATURE;
        }

        @Override
        public boolean isResourcePresent() {
            return true;
        }
    }
}