package de.marhali.easyi18n.idea.key;

import com.intellij.openapi.util.Key;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.idea.toolwindow.viewmodel.ToolWindowViewModel;

/**
 * Plugin specific IntelliJ keys to interact with.
 *
 * @author marhali
 */
public final class PluginKey {

    private PluginKey() {}

    /**
     * Tracks a {@link ModuleId} association.
     */
    public static final Key<ModuleId> MODULE_ID = Key.create("de.marhali.easyi18n.core.domain.moduleId");

    /**
     * Tracks the {@link ToolWindowViewModel} that manages a tool window content.
     */
    public static final Key<ToolWindowViewModel> TOOL_WINDOW_VIEW_MODEL = Key.create("de.marhali.easyi18n.idea.toolwindow.viewModel");
}
