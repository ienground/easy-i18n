package de.marhali.easyi18n.idea.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import de.marhali.easyi18n.core.domain.model.I18nKey;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.idea.key.PluginKey;
import de.marhali.easyi18n.idea.toolwindow.viewmodel.ToolWindowViewModel;
import org.jetbrains.annotations.NotNull;

/**
 * Navigates to translation keys inside the translations tool window.
 *
 * @author marhali
 */
public final class I18nToolWindowNavigator {

    private static final String TOOL_WINDOW_ID = "de.marhali.easyi18n.idea.toolwindow.I18nToolWindowFactory";

    private I18nToolWindowNavigator() {}

    /**
     * Opens the translations tool window and focuses the given key within the module panel.
     * @param project Project
     * @param moduleId Module identifier
     * @param key Translation key to focus
     */
    public static void focusKey(@NotNull Project project, @NotNull ModuleId moduleId, @NotNull I18nKey key) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }

        toolWindow.activate(() -> {
            for (Content content : toolWindow.getContentManager().getContents()) {
                ToolWindowViewModel vm = content.getUserData(PluginKey.TOOL_WINDOW_VIEW_MODEL);
                if (vm != null) {
                    vm.focusKey(moduleId, key);
                    return;
                }
            }
        });
    }
}
