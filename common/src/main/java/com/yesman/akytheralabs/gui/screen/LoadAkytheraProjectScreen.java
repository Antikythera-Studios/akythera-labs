package com.yesman.akytheralabs.gui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.BuiltInButtonTheme;
import com.yesman.akythera.client.api.gui.BuiltInEditBoxTheme;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.dev.ProjectReloader;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// A screen to select an existing project or create a new one.
///
/// Identifies directories that contain `project_settings.akproject` right below
/// the projects root. Projects are shown as grid cards sorted by last edit time
/// (newest first). Each card displays the project's `icon.png`, name, and last
/// edit timestamp.
///
/// An "Add" card at the end of the grid opens a [WidgetContainerModal] to enter a new
/// project name. After creation the screen refreshes in place — the editor is
/// not opened until the user explicitly presses "Open".
///
/// When pressing "Open", calls [AkytheraProject#loadProject] and transitions to
/// the editor layout.
public final class LoadAkytheraProjectScreen extends Screen {
    private static final int HEADER_H = PanelHeader.DEFAULT_HEADER_HEIGHT;
    private static final int FOOTER_H = 36;
    private static final int GRID_PADDING = 18;
    private static final int CELL_W = 120;
    private static final int CELL_H = 140;
    private static final int GRID_PAD = 12;
    private static final int FOOTER_BTN_W = 80;
    private static final int FOOTER_BTN_H = 20;
    private static final int FOOTER_PAD = 8;
    private static final int NEW_PROJECT_BOX_W = 240;

    private final PanelHeader header;
    private final Grid projectGrid;
    private final AkytheraButton closeScreenButton;
    private final AkytheraButton openButton;
    private final AkytheraButton cancelButton;

    /// This modal is not controlled by [AkytheraEditorLayoutManager] since this screen is opened before any Akythera
    /// project is loaded.
    @Nullable
    private WidgetContainerModal modal;

    /// Memorize the setting scale to roll back when the screen closed
    private final int oldScale;

    public LoadAkytheraProjectScreen() {
        super(Component.translatable(LangKeys.GUI_PROJECT_LOAD_SCREEN_TITLE));

        oldScale = Minecraft.getInstance().options.guiScale().get();

        // Force to gui scale 2
        Minecraft.getInstance().options.guiScale().set(AkytheraLabsConfig.getEditorGuiScale());

        header = new PanelHeader(font, Component.translatable(LangKeys.GUI_PROJECT_LOAD_SCREEN_TITLE));

        closeScreenButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> onClose())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(HEADER_H - 1))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(HEADER_H - 1))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        openButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_PROJECT_LOAD_SCREEN_WIDGET_OPEN), () -> openSelectedProject())
            .xUnits(AnchoredWidget.pixel(2 * FOOTER_PAD + FOOTER_BTN_W), AnchoredWidget.pixel(FOOTER_BTN_W))
            .yUnits(AnchoredWidget.pixel(FOOTER_PAD), AnchoredWidget.pixel(FOOTER_BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_PRIMARY_BUTTON)
            .build();

        openButton.active = false;

        cancelButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> onClose())
            .xUnits(AnchoredWidget.pixel(FOOTER_PAD), AnchoredWidget.pixel(FOOTER_BTN_W))
            .yUnits(AnchoredWidget.pixel(FOOTER_PAD), AnchoredWidget.pixel(FOOTER_BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        projectGrid = new Grid(
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(HEADER_H + GRID_PADDING),
            AnchoredWidget.pixel(FOOTER_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            CELL_W,
            CELL_H,
            GRID_PAD,
            Comparator.<AbstractWidget, Boolean>comparing(w -> w instanceof AddProjectCell)
                .thenComparing(w -> ((ProjectCell)w).lastEditTime, String.CASE_INSENSITIVE_ORDER)
        );

        populateGrid();
    }

    @Override
    protected void init() {
        super.init();

        ScreenRectangle rectangle = getRectangle();

        addRenderableWidget(header);
        header.relocate(rectangle);

        addRenderableWidget(closeScreenButton);
        closeScreenButton.relocate(rectangle);

        addRenderableWidget(cancelButton);
        cancelButton.relocate(rectangle);

        addRenderableWidget(openButton);
        openButton.relocate(rectangle);

        addRenderableWidget(projectGrid);
        projectGrid.sort();
        projectGrid.relocate(rectangle);

        if (modal != null) {
            modal.relocate(rectangle);
        }
    }

    private void populateGrid() {
        releaseLogoTextures();
        projectGrid.clearComponents();

        List<Path> projects = AkytheraProject.discoverProjects();

        projects.stream()
            .sorted(Comparator.comparing(ProjectCell::readLastEditInstant, Comparator.reverseOrder()))
            .map(ProjectCell::new)
            .forEach(cell -> projectGrid.addComponent(cell.projectPath.toString(), cell));

        projectGrid.addComponent("add_project", new AddProjectCell());
    }

    private void releaseLogoTextures() {
        if (projectGrid == null) return;

        for (AbstractWidget w : projectGrid.children()) {
            if (w instanceof ProjectCell cell && cell.logoTextureId != null) {
                Minecraft.getInstance().getTextureManager().release(cell.logoTextureId);
            }
        }
    }

    private void openSelectedProject() {
        if (projectGrid.getFocused() instanceof ProjectCell projectCell) {
            AkytheraProject project = AkytheraProject.loadProject(projectCell.projectPath);
            minecraft.gui.setScreen(null);
            ProjectReloader.open(project, () -> AkytheraEditorLayoutManager.getInstance().openEditorLayout());
        }
    }

    private void openNewProjectPopup() {
        EditorEditBox<String> nameBox = EditorEditBox.filteredStringBox(
            font,
            s -> s.chars().allMatch(c -> isValidProjectNameChar((char) c)),
            Component.literal("edit_box.project_name"),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT
        );
        nameBox.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);
        nameBox.setX1(AnchoredWidget.pixel(4));
        nameBox.setX2(AnchoredWidget.pixel(4));
        nameBox.setY2(AnchoredWidget.pixel(13));
        nameBox.addCharReplacement(' ', '_');

        AkytheraButton cancelButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable("gui.cancel"), () -> closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .theme(BuiltInButtonTheme.VANILLA_BUTTON)
            .build();

        AkytheraButton okButton = AkytheraButton
            .buttonBuilder(
                font,
                LocalizedMessage.translatable("gui.ok"),
                () -> {
                    String name = nameBox.getText().trim();
                    if (!name.isEmpty()) {
                        AkytheraProject.startNewProject(name);
                        closeModal();
                        populateGrid();
                        projectGrid.relocate(getRectangle());
                    }
                }
            )
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .theme(BuiltInButtonTheme.VANILLA_BUTTON)
            .build();

        int btnW = WidgetContainerModal.evenButtonWidth(NEW_PROJECT_BOX_W, 2);

        modal = WidgetContainerModal
            .builder(font, Component.translatable(LangKeys.GUI_MESSAGE_PROJECT_LOAD_SCREEN_ENTER_PROJECT_NAME), NEW_PROJECT_BOX_W)
            .singleWidgetRow(26, nameBox)
            .footerButton(cancelButton, btnW)
            .footerButton(okButton, btnW)
            .build();

        modal.relocate(getRectangle());
        modal.setFocused(nameBox);
    }

    private void openDeleteConfirmationPopup(ProjectCell cell) {
        Component message = Component.translatable(
            LangKeys.GUI_PROJECT_LOAD_SCREEN_MESSAGE_DELETE_CONFIRM,
            cell.projectName
        );

        AkytheraButton cancelButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable("gui.cancel"), () -> closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .theme(BuiltInButtonTheme.VANILLA_BUTTON)
            .build();

        AkytheraButton yesButton = AkytheraButton
            .buttonBuilder(
                font,
                LocalizedMessage.translatable("gui.ok"),
                () -> {
                    deleteProject(cell.projectPath);
                    closeModal();
                }
            )
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .theme(BuiltInButtonTheme.VANILLA_BUTTON)
            .build();

        int popupW = 280;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        modal = WidgetContainerModal
            .builder(font, message, popupW)
            .footerButton(cancelButton, btnW)
            .footerButton(yesButton, btnW)
            .build();

        modal.relocate(getRectangle());
    }

    private void deleteProject(Path projectRoot) {
        FileSystemHelper.deleteRecursive(projectRoot);

        populateGrid();
        projectGrid.relocate(getRectangle());
    }

    private void closeModal() {
        modal = null;
    }

    private static boolean isValidProjectNameChar(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9')
            || c == '_';
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // In-game dark background
        graphics.fillGradient(0, 0, width, height, 0xB0000000, 0xB0000000);

        // Footer
        int footerY = height - FOOTER_H;
        graphics.fill(0, footerY, width, height, 0xFF1A1A1A);
        graphics.fill(0, footerY, width, footerY + 1, 0xFF404040);

        // Suppress hover on background widgets while modal is open
        super.extractRenderState(graphics, modal != null ? -1 : mouseX, modal != null ? -1 : mouseY, partialTick);

        // Modal on top
        if (modal != null) {
            modal.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());

        if (modal != null) {
            modal.mouseClicked(event, doubleClick);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (modal != null) {
            modal.mouseReleased(event);
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (modal != null) return modal.keyPressed(event);

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (modal != null) return modal.charTyped(event);

        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return projectGrid.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.guiScale().set(oldScale);
        releaseLogoTextures();
    }

    private class ProjectCell extends AbstractWidget implements PatchComponent {
        private static final DateTimeFormatter EDIT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private static final int DELETE_BTN_SIZE = 9;
        private static final int DELETE_BTN_PAD = 3;
        private static final int LOGO_AREA_RATIO = 70;

        final Path projectPath;
        final String projectName;
        private final String lastEditTime;
        @Nullable
        final Identifier logoTextureId;

        ProjectCell(Path projectPath) {
            super(0, 0, CELL_W, CELL_H, Component.empty());

            this.projectPath = projectPath;
            this.projectName = projectPath.getFileName().toString();
            this.lastEditTime = formatLastEdit(projectPath);
            this.logoTextureId = loadLogo(projectPath, projectName);
        }

        /// Returns the last-edit [Instant] of the project settings file — used for sorting.
        static Instant readLastEditInstant(Path projectPath) {
            try {
                FileTime time = Files.getLastModifiedTime(
                    projectPath.resolve(AkytheraProject.PROJECT_SETTINGS_FILE)
                );
                return time.toInstant();
            } catch (IOException e) {
                return Instant.EPOCH;
            }
        }

        private static String formatLastEdit(Path projectPath) {
            try {
                FileTime time = Files.getLastModifiedTime(
                    projectPath.resolve(AkytheraProject.PROJECT_SETTINGS_FILE)
                );
                LocalDateTime ldt = LocalDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());
                return EDIT_TIME_FORMATTER.format(ldt);
            } catch (IOException e) {
                return "—";
            }
        }

        @Nullable
        private static Identifier loadLogo(Path projectPath, String projectName) {
            Path logoPath = projectPath.resolve(AkytheraProject.PROJECT_ICON_FILE);
            if (!Files.exists(logoPath)) return null;

            CompletableFuture.supplyAsync(() -> {
                try (InputStream in = Files.newInputStream(logoPath)) {
                    return NativeImage.read(in);
                } catch (IOException e) {
                    return null;
                }
            }).thenAcceptAsync(image -> {
                if (image == null) return;
                DynamicTexture texture = new DynamicTexture(() -> projectName, image);
                Identifier id = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "project_logo/" + projectName);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }, Minecraft.getInstance());

            return Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "project_logo/" + projectName);

            /*
            Revert when multithreading doesn't work again
            try {
                NativeImage image = NativeImage.read(Files.newInputStream(logoPath));
                DynamicTexture texture = new DynamicTexture(() -> projectName, image);
                Identifier id = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "project_logo/" + projectName);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                return id;
            } catch (IOException e) {
                return null;
            }
            */
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // Border
            int borderColor = isFocused() ? 0xFF3A7FC1 : 0xFF3A3A3A;
            graphics.fill(x, y, x + w, y + h, borderColor);

            // Fill
            int fillColor = isFocused() ? 0xFF1C2D3F : isHovered() ? 0xFF262626 : 0xFF1C1C1C;
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);

            // ── Logo area (upper 70% of cell) ──
            int logoAreaH = h * LOGO_AREA_RATIO / 100;
            int logoMargin = 8;

            if (logoTextureId != null) {
                int logoSize = Math.min(w - logoMargin * 2, logoAreaH - logoMargin * 2);
                int logoX = x + (w - logoSize) / 2;
                int logoY = y + (logoAreaH - logoSize) / 2;
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    logoTextureId,
                    logoX, logoY,
                    0, 0,
                    logoSize, logoSize,
                    logoSize, logoSize
                );
            } else {
                graphics.fill(
                    x + logoMargin,
                    y + logoMargin,
                    x + w - logoMargin,
                    y + logoAreaH - logoMargin,
                    0xFF333333
                );
            }

            // ── Text area ──
            int textY = y + logoAreaH + 4;
            graphics.text(font, projectName, x + 6, textY, 0xFFEEEEEE, false);

            Component lastEditLabel = Component.translatable(LangKeys.GUI_PROJECT_LOAD_SCREEN_LABEL_LAST_EDIT);

            graphics.text(
                font,
                lastEditLabel,
                x + 6,
                textY + font.lineHeight + 2,
                0xFF888888,
                false
            );

            textY += font.lineHeight;

            graphics.text(
                font,
                lastEditTime,
                x + 6,
                textY + font.lineHeight + 2,
                0xFF888888,
                false
            );

            // ── Delete button ──
            renderDeleteButton(graphics, mouseX, mouseY);
        }

        private void renderDeleteButton(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            int bx = getRight() - DELETE_BTN_SIZE - DELETE_BTN_PAD;
            int by = getY() + DELETE_BTN_PAD;
            int color = isOverDeleteButton(mouseX, mouseY) ? 0xFFCC4444 : 0xFF888888;

            // "x" drawn as a simple border rectangle (box outline) indicating a close zone
            graphics.fill(bx, by, bx + DELETE_BTN_SIZE, by + 1, color);
            graphics.fill(bx, by + DELETE_BTN_SIZE - 1, bx + DELETE_BTN_SIZE, by + DELETE_BTN_SIZE, color);
            graphics.fill(bx, by, bx + 1, by + DELETE_BTN_SIZE, color);
            graphics.fill(bx + DELETE_BTN_SIZE - 1, by, bx + DELETE_BTN_SIZE, by + DELETE_BTN_SIZE, color);
            graphics.text(font, Component.literal("x"), bx + 2, by, color);
        }

        private boolean isOverDeleteButton(double mx, double my) {
            int bx = getRight() - DELETE_BTN_SIZE - DELETE_BTN_PAD;
            int by = getY() + DELETE_BTN_PAD;
            return mx >= bx && mx <= bx + DELETE_BTN_SIZE
                && my >= by && my <= by + DELETE_BTN_SIZE;
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (!isMouseOver(event.x(), event.y())) return false;

            if (event.button() == 0) {
                if (isOverDeleteButton(event.x(), event.y())) {
                    openDeleteConfirmationPopup(this);
                } else {
                    openButton.active = true;

                    if (doubleClick) {
                        openSelectedProject();
                    }
                }

                return true;
            }

            return false;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private class AddProjectCell extends AbstractWidget implements PatchComponent {
        AddProjectCell() {
            super(0, 0, CELL_W, CELL_H, Component.empty());
            setTooltip(Tooltip.create(Component.translatable(
                LangKeys.GUI_PROJECT_LOAD_SCREEN_TOOLTIP_ADD_PROJECT
            )));
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            graphics.fill(x, y, x + w, y + h, 0xFF3A3A3A);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, isHovered() ? 0xFF262626 : 0xFF1C1C1C);

            // "+" — two crossing filled bars
            int plusColor = 0xFF606060;
            int barThick = 2;
            int barLen = 24;
            int cx = x + w / 2;
            int cy = y + h / 2;

            graphics.fill(cx - barLen / 2, cy - barThick / 2, cx + barLen / 2, cy + barThick / 2, plusColor);
            graphics.fill(cx - barThick / 2, cy - barLen / 2, cx + barThick / 2, cy + barLen / 2, plusColor);
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (!isMouseOver(event.x(), event.y())) return false;

            if (event.button() == 0) {
                openNewProjectPopup();
                openButton.active = false;
                return true;
            }

            return false;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}