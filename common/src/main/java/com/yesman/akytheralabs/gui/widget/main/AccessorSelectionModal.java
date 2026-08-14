package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Search-and-pick modal over the project's known [AssetAccessor]s of one [AssetAccessor.Type]
/// (modeled on [ItemSelectModal]). Lists mod-registered and project-issued accessors alike (the
/// project's merged listings), filtered by the requested type; single-click confirms and closes.
/// With `includeNone`, a `<none>` row leads the list and confirms with a null accessor —
/// callers use it to clear an optional mapping.
public final class AccessorSelectionModal extends OverlayingWindowModal {
    private static final int WIDTH = 260;
    private static final int HEIGHT = 200;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 16;

    /// How every picker spells "nothing selected", rows and PopupBox displays alike.
    public static final String NONE_LABEL = "<none>";

    private final Font font;
    private final AssetAccessor.Type type;
    private final boolean includeNone;
    private final Consumer<@Nullable AssetAccessor<?>> onConfirm;
    private final AdvancedEditBox<String> searchBox;
    private AccessorList accessorList; // assigned after the search box, whose responder captures it

    public AccessorSelectionModal(Font font, AssetAccessor.Type type, Consumer<AssetAccessor<?>> onConfirm) {
        this(font, type, false, onConfirm);
    }

    public AccessorSelectionModal(Font font, AssetAccessor.Type type, boolean includeNone, Consumer<@Nullable AssetAccessor<?>> onConfirm) {
        super(AnchoredWidget.pixel(WIDTH), AnchoredWidget.pixel(HEIGHT), Component.literal("Select Asset"));

        this.font = font;
        this.type = type;
        this.includeNone = includeNone;
        this.onConfirm = onConfirm;

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            str -> accessorList.rebuild(str),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.accessorList = new AccessorList(listAccessors(type));
    }

    /// Whether `accessor`'s id is still in the merged listing this modal shows for its type — the
    /// [com.yesman.akytheralabs.gui.widget.PopupBox] validity probe for accessor cells (a
    /// deleted / renamed asset drops out of the listing, but referrers keep the stale instance).
    /// Enumerates without loading any asset; runs only at screen build and on picks.
    public static boolean resolves(AssetAccessor<?> accessor) {
        return listAccessors(accessor.type()).stream()
            .anyMatch(a -> a.identifier().equals(accessor.identifier()));
    }

    /// The project's merged accessor listing for `type` (mod-registered + project-issued, deduped),
    /// filtered by the accessors' runtime type so mixed listings (the mesh one) narrow correctly.
    private static List<AssetAccessor<?>> listAccessors(AssetAccessor.Type type) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        Stream<? extends AssetAccessor<?>> source = switch (type) {
            case ARMATURE -> project.listAllArmatureAccessors();
            case ANIMATION -> project.listAllAnimationAccessors();
            case STATIC_MESH, SKELETAL_MESH -> project.listAllMeshAccessors();
        };

        return source
            .filter(accessor -> accessor.checkType(type))
            .sorted(Comparator.comparing(accessor -> accessor.identifier().toString()))
            .<AssetAccessor<?>>map(accessor -> accessor)
            .toList();
    }

    /// Display string of an accessor: the identifier with the type's asset directory trimmed
    /// (matching how the editor codecs write the ids), or `<none>` for the
    /// [DataHolder#EMPTY_REFERENCE] / engine empty placeholder. Shared by this modal's rows and
    /// [com.yesman.akytheralabs.editor.EditorWidgetFactory]'s accessor PopupBoxes.
    public static String displayName(AssetAccessor<?> accessor) {
        String directory = switch (accessor.type()) {
            case ARMATURE -> ArmatureManager.DIRECTORY;
            case ANIMATION -> AnimationManager.DIRECTORY;
            case STATIC_MESH, SKELETAL_MESH -> MeshManager.DIRECTORY;
        };

        String trimmed = accessor.identifier().toString().replace(directory, "");

        return trimmed.equals(DataHolder.EMPTY_REFERENCE.toString()) ? NONE_LABEL : trimmed;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, accessorList);
    }

    @Override
    protected int contentHeight() {
        return HEIGHT;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) return;
        super.setFocused(focused);
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= (double) getX()
            && mouseY >= (double) getY()
            && mouseX < (double) getRight()
            && mouseY < (double) getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return accessorList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            if (AkytheraEditorLayoutManager.getInstance().isActive()) {
                AkytheraEditorLayoutManager.getInstance().closeModal();
                return true;
            }
        }

        GuiEventListener focused = getFocused();
        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(event);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // This modal covers the whole screen, always return true to interrupt original screen inputs
        return isActive();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);

        graphics.text(
            font,
            getMessage(),
            getX() + (WIDTH - font.width(getMessage())) / 2,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        accessorList.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    private final class AccessorList extends AbstractSelectionList<AccessorList.@NonNull AccessorEntry> implements AnchoredWidget {
        private final List<AssetAccessor<?>> allAccessors;

        AccessorList(List<AssetAccessor<?>> allAccessors) {
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP, LIST_TOP, ROW_H);
            this.allAccessors = allAccessors;
            rebuild("");
        }

        void rebuild(String query) {
            clearEntries();
            String lower = query.toLowerCase();

            int rowIndex = 0;

            if (includeNone && (lower.isEmpty() || NONE_LABEL.contains(lower))) {
                addEntry(new AccessorEntry(null, rowIndex++));
            }

            for (AssetAccessor<?> accessor : allAccessors) {
                if (!lower.isEmpty() && !displayName(accessor).toLowerCase().contains(lower)) continue;
                addEntry(new AccessorEntry(accessor, rowIndex++));
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_WIDTH;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (this.height * this.height) / (float) this.contentHeight()), 8, this.height - 8);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        final class AccessorEntry extends AbstractSelectionList.Entry<@NonNull AccessorEntry> {
            /// Null marks the `<none>` row — confirms a cleared pick.
            @Nullable
            private final AssetAccessor<?> accessor;
            private final int rowIndex;

            AccessorEntry(@Nullable AssetAccessor<?> accessor, int rowIndex) {
                this.accessor = accessor;
                this.rowIndex = rowIndex;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                int bg = hovered
                    ? 0xFF1E4D72
                    : rowIndex % 2 == 0
                        ? 0xFF000000
                        : 0xFF111111;

                g.fill(getX() - 2, getY(), getX() + getWidth(), getY() + getHeight(), bg);

                String label = font.plainSubstrByWidth(accessor == null ? NONE_LABEL : displayName(accessor), getWidth() - 8);
                g.text(font, label, getX() + 6, getY() + (ROW_H - font.lineHeight) / 2, accessor == null ? 0xFF888888 : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                onConfirm.accept(accessor);
                AkytheraEditorLayoutManager.getInstance().closeModal();
                return true;
            }
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(0);
        private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
