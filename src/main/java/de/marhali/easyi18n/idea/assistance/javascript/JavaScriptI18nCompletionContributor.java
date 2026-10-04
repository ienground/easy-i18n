package de.marhali.easyi18n.idea.assistance.javascript;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import de.marhali.easyi18n.core.application.cqrs.PossiblyUnavailable;
import de.marhali.easyi18n.core.application.query.AllModuleI18nEntryPreviewQuery;
import de.marhali.easyi18n.core.application.query.MatchEditorElementQuery;
import de.marhali.easyi18n.core.application.query.ModuleIdByEditorFilePathQuery;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.I18nKeyCandidate;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.core.domain.rules.EditorElement;
import de.marhali.easyi18n.core.domain.rules.EditorFilePath;
import de.marhali.easyi18n.core.domain.rules.EditorLanguage;
import de.marhali.easyi18n.idea.assistance.AbstractI18nCompletionContributor;
import de.marhali.easyi18n.idea.assistance.EditorFilePathExtractor;
import de.marhali.easyi18n.idea.icons.PluginIcon;
import de.marhali.easyi18n.idea.service.I18nProjectService;
import de.marhali.easyi18n.idea.service.ScheduledModuleLoaderService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author marhali
 */
public class JavaScriptI18nCompletionContributor extends AbstractI18nCompletionContributor {

    private final EditorLanguage language;

    public JavaScriptI18nCompletionContributor() {
        this(EditorLanguage.JAVASCRIPT);
    }

    protected JavaScriptI18nCompletionContributor(EditorLanguage language) {
        this.language = language;
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(JSLiteralExpression.class),
            new CompletionProvider<>() {

                @Override
                protected void addCompletions(
                    @NotNull CompletionParameters completionParameters,
                    @NotNull ProcessingContext processingContext,
                    @NotNull CompletionResultSet completionResultSet
                ) {
                    PsiElement position = completionParameters.getPosition();
                    JSLiteralExpression literal = findParentOfType(position, JSLiteralExpression.class);

                    if (literal == null || !literal.isStringLiteral()) {
                        return;
                    }

                    Project project = literal.getProject();

                    I18nProjectService projectService = project.getService(I18nProjectService.class);

                    EditorFilePath editorFilePath = EditorFilePathExtractor.extract(completionParameters.getOriginalFile());

                    Optional<ModuleId> moduleIdResponse = projectService.query(new ModuleIdByEditorFilePathQuery(editorFilePath));

                    if (moduleIdResponse.isEmpty()) {
                        return;
                    }

                    ModuleId moduleId = moduleIdResponse.get();

                    EditorLanguage effectiveLang = effectiveLanguage(completionParameters.getOriginalFile());
                    JavaScriptEditorElementExtractor extractor = new JavaScriptEditorElementExtractor(effectiveLang);
                    EditorElement editorElement = extractor.extract(literal, completionParameters.getOriginalFile());

                    if (editorElement == null) {
                        return;
                    }

                    Boolean editorElementMatched = projectService.query(new MatchEditorElementQuery(moduleId, editorElement));

                    if (!editorElementMatched) {
                        // Not targeted by editor rules
                        return;
                    }

                    PossiblyUnavailable<List<I18nEntryPreview>> entriesResponse
                        = projectService.query(new AllModuleI18nEntryPreviewQuery(moduleId));

                    if (!entriesResponse.available()) {
                        // Response is not available - module is not loaded yet
                        project.getService(ScheduledModuleLoaderService.class).loadModule(moduleId);
                        return;
                    }

                    if (entriesResponse.result() == null || entriesResponse.result().isEmpty()) {
                        return;
                    }

                    List<I18nEntryPreview> suggestions = entriesResponse.result();

                    String currentValue = literal.getStringValue();

                    if (currentValue == null) {
                        currentValue = "";
                    }

                    TextRange valueRangeInLiteral = ElementManipulators.getValueTextRange(literal);
                    TextRange absoluteValueRange = valueRangeInLiteral.shiftRight(literal.getTextRange().getStartOffset());

                    int caretOffset = completionParameters.getOffset();
                    if (caretOffset < absoluteValueRange.getStartOffset()) {
                        return;
                    }

                    int relativeCaretOffset = Math.min(
                        Math.max(0, caretOffset - absoluteValueRange.getStartOffset()),
                        currentValue.length()
                    );
                    String prefix = currentValue.substring(0, relativeCaretOffset);

                    CompletionResultSet prefixed = completionResultSet.withPrefixMatcher(prefix);

                    if (JavaScriptTranslatorResolver.isNamespaceDeclaration(literal)) {
                        // useTranslations('...') expects a namespace instead of a translation key
                        for (String namespace : collectNamespaces(suggestions)) {
                            prefixed.addElement(LookupElementBuilder.create(namespace)
                                .withInsertHandler(AbstractI18nCompletionContributor::replaceCompletionRange)
                                .withTypeText("namespace")
                                .withIcon(PluginIcon.TRANSLATE_ICON));
                        }
                        return;
                    }

                    I18nKeyCandidate keyCandidate = editorElement.keyCandidate();

                    for (I18nEntryPreview suggestion : suggestions) {
                        // Scoped translation function, e.g. const t = useTranslations('footer')
                        String key = keyCandidate.relativize(suggestion.key().canonical());
                        if (key == null) {
                            continue;
                        }

                        LookupElementBuilder builder = LookupElementBuilder.create(key)
                            .withInsertHandler(AbstractI18nCompletionContributor::replaceCompletionRange)
                            .withPresentableText(key)
                            .withIcon(PluginIcon.TRANSLATE_ICON);

                        if (suggestion.previewValue() != null) {
                            builder = builder.withTailText(" = " + suggestion.previewValue().toInputString(), true);
                        }

                        prefixed.addElement(builder);
                    }
                }
            }
        );
    }

    private static @NotNull Set<String> collectNamespaces(@NotNull List<I18nEntryPreview> entries) {
        Set<String> namespaces = new TreeSet<>();
        for (I18nEntryPreview entry : entries) {
            // Namespace file layout (connector:domains.title) is declared as useTranslations('connector.domains')
            String key = entry.key().canonical().replaceFirst(":", ".");
            for (int index = key.indexOf('.'); index > 0; index = key.indexOf('.', index + 1)) {
                namespaces.add(key.substring(0, index));
            }
        }
        return namespaces;
    }

    private @NotNull EditorLanguage effectiveLanguage(@NotNull com.intellij.psi.PsiFile file) {
        if (language == EditorLanguage.JAVASCRIPT
                && JavaScriptEditorElementExtractor.isTypeScript(file)) {
            return EditorLanguage.TYPESCRIPT;
        }
        return language;
    }
}
