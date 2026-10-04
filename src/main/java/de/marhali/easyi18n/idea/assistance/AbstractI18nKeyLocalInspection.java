package de.marhali.easyi18n.idea.assistance;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import de.marhali.easyi18n.core.application.cqrs.PossiblyUnavailable;
import de.marhali.easyi18n.core.application.query.GuessNullableI18nEntryQuery;
import de.marhali.easyi18n.core.application.query.I18nEntryPreviewQuery;
import de.marhali.easyi18n.core.application.query.I18nNamespacePreviewQuery;
import de.marhali.easyi18n.core.application.query.MatchEditorElementQuery;
import de.marhali.easyi18n.core.application.query.ModuleIdByEditorFilePathQuery;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.I18nKeyCandidate;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.core.domain.model.NullableI18nEntry;
import de.marhali.easyi18n.core.domain.rules.EditorElement;
import de.marhali.easyi18n.core.domain.rules.EditorFilePath;
import de.marhali.easyi18n.idea.messages.PluginBundle;
import de.marhali.easyi18n.idea.service.I18nProjectService;
import de.marhali.easyi18n.idea.service.ScheduledModuleLoaderService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for all language-specific i18n key inspections.
 *
 * <p>Subclasses implement {@link #buildVisitor} using a language-specific PSI visitor.
 * The visitor should call {@link #checkI18nLiteral} for each string literal it encounters,
 * after extracting the key and editor element using the language-specific extractor.
 *
 * @author marhali
 */
public abstract class AbstractI18nKeyLocalInspection extends LocalInspectionTool {

    @Override
    public @Nullable String getDescriptionFileName() {
        return "UnresolvedI18nKey";
    }

    /**
     * Checks a single string literal for an unresolved i18n key and registers a problem if needed.
     *
     * <p>Call this from language-specific PSI visitors after extracting the literal element,
     * its string value, and the {@link EditorElement} from the language-specific extractor.
     * If {@code editorElement} is {@code null}, the literal is not matched by any editor rule
     * and no problem is registered.
     *
     * @param literalElement the PSI element representing the string literal (used as problem target)
     * @param key            the non-blank string value of the literal. The translation key is taken from
     *                       {@link EditorElement#keyCandidate()} to respect scoped namespaces
     * @param editorElement  the extracted editor element, or {@code null} if no rule matches
     * @param containingFile the file containing the literal (used for module lookup)
     * @param holder         the problems holder to register problems into
     */
    protected static void checkI18nLiteral(
        @NotNull PsiElement literalElement,
        @NotNull String key,
        @Nullable EditorElement editorElement,
        @NotNull PsiFile containingFile,
        @NotNull ProblemsHolder holder
    ) {
        if (editorElement == null) return;

        Project project = literalElement.getProject();
        I18nProjectService projectService = project.getService(I18nProjectService.class);

        EditorFilePath editorFilePath = EditorFilePathExtractor.extract(containingFile);
        Optional<ModuleId> moduleIdOpt = projectService.query(new ModuleIdByEditorFilePathQuery(editorFilePath));
        if (moduleIdOpt.isEmpty()) return;

        ModuleId moduleId = moduleIdOpt.get();
        I18nKeyCandidate keyCandidate = editorElement.keyCandidate();

        Boolean matched = projectService.query(new MatchEditorElementQuery(moduleId, editorElement));
        if (!matched) return;

        PossiblyUnavailable<Optional<I18nEntryPreview>> entryResponse =
            projectService.query(new I18nEntryPreviewQuery(moduleId, keyCandidate));

        if (!entryResponse.available()) {
            project.getService(ScheduledModuleLoaderService.class).loadModule(moduleId);
            return;
        }

        if (entryResponse.result() == null || entryResponse.result().isPresent()) return;

        // Namespaces (intermediate nodes like "footer" for "footer.title") are valid references
        PossiblyUnavailable<List<I18nEntryPreview>> namespaceResponse =
            projectService.query(new I18nNamespacePreviewQuery(moduleId, keyCandidate));
        if (namespaceResponse.result() != null && !namespaceResponse.result().isEmpty()) return;

        String missingKey = qualifyMissingKey(projectService, moduleId, keyCandidate);
        NullableI18nEntry entry = projectService.query(new GuessNullableI18nEntryQuery(moduleId, missingKey));
        holder.registerProblem(
            literalElement,
            PluginBundle.message("editor.intention.unresolved.description", missingKey),
            new I18nKeyQuickFixIntentionAction(literalElement, moduleId, entry)
        );
    }

    /**
     * Qualifies a missing scoped key using the layout of existing keys within the same namespace,
     * e.g. {@code connector:domains.title} if the module already contains {@code connector:...} keys.
     */
    private static @NotNull String qualifyMissingKey(
        @NotNull I18nProjectService projectService,
        @NotNull ModuleId moduleId,
        @NotNull I18nKeyCandidate keyCandidate
    ) {
        String namespace = keyCandidate.namespace();
        if (namespace == null) {
            return keyCandidate.canonical();
        }

        PossiblyUnavailable<List<I18nEntryPreview>> siblingsResponse =
            projectService.query(new I18nNamespacePreviewQuery(moduleId, I18nKeyCandidate.of(namespace)));

        List<String> qualifiedKeys = keyCandidate.qualified();
        boolean namespaceFileLayout = siblingsResponse.result() != null
            && !siblingsResponse.result().isEmpty()
            && siblingsResponse.result().get(0).key().canonical().contains(":");

        // Second variant denotes the namespace file layout
        return namespaceFileLayout && qualifiedKeys.size() > 1 ? qualifiedKeys.get(1) : keyCandidate.display();
    }
}
