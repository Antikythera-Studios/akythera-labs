package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.EntityPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// [ResourceProcessor] for the entity-patch datapack registry
/// ([AkytheraRegistries.DataPack#ENTITY_PATCH_KEY]). Modeled on
/// [AnimatorScriptResourceProcessor]: an entity patch is keyed by the entity type it targets, so the
/// green `+` first asks for that type, then creates the blueprint entry at
/// `entity_patch/<entityNs>/<entityType>` and opens the [EntityPatchEditorScreen]. One patch per
/// entity type — a duplicate shows a warning instead of overwriting.
public final class EntityPatchResourceProcessor implements ResourceProcessor<EntityPatchBlueprint> {
    public static final EntityPatchResourceProcessor INSTANCE = new EntityPatchResourceProcessor();

    private EntityPatchResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return false;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        ModelSelectModal modal = new ModelSelectModal(
            browser.getFont(), null,
            previewModel -> {
                Identifier modelId = previewModel.modelId();
                String idPath = modelId.getNamespace() + "/" + modelId.getPath();

                ProjectObjectRegistry<EntityPatchBlueprint, EntityPatchBlueprintHolder> registry =
                    project.getModifiableRegistry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);
                Identifier entryId = Identifier.fromNamespaceAndPath(project.getProjectName(), idPath);

                if (registry != null && registry.containsKey(entryId)) {
                    Minecraft.getInstance().execute(() -> openDuplicateWarning(browser.getFont(), modelId));
                    return;
                }

                ResourceKey<EntityPatchBlueprint> key = project.createEntry(
                    AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, idPath, true);

                EntityPatchBlueprintHolder holder = project.getDataHolderOrThrow(key);
                holder.setPreviewModel(previewModel);
                project.saveEntry(key);

                Path focusDir = project.getProjectRoot()
                    .resolve("data")
                    .resolve(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY.identifier().getPath());

                AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                    mainScreen.explorerPanel.rebuildTree();
                    mainScreen.explorerPanel.focusNodeByPath(focusDir);
                });

                AkytheraEditorLayoutManager.getInstance().openNewScreen(
                    new EntityPatchEditorScreen(key, holder));
            },
            ModelSelectModal.entityTypeCatalog()
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Warning shown when the user picks an entity type that already has a patch.
    private void openDuplicateWarning(Font font, Identifier entityTypeId) {
        int popupW = 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 1);

        WidgetContainerModal modal = WidgetContainerModal.builder(
                font,
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_DUPLICATE_TITLE),
                popupW
            )
            .textLines(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_DUPLICATE_BODY, entityTypeId.toString()))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
            ContentWidget.EntityPatchContent::new);
    }

    @Override
    public ContextMenuContainer createContextMenuOnWidget(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN))
                .onPress(_ -> rightPressed.onDoubleClicked())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_DELETE_SELECTED))
                .onPress(_ -> browser.triggerDeleteSelected())
                .done()
            .build();
    }
}
