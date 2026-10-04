package de.marhali.easyi18n.core.domain.config.preset.impl;

import de.marhali.easyi18n.core.domain.config.FileCodec;
import de.marhali.easyi18n.core.domain.config.ProjectConfigModule;
import de.marhali.easyi18n.core.domain.config.preset.PresetProvider;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.core.domain.rules.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Preset for next-intl / use-intl (Next.js, React).
 *
 * <p>Matches calls of translation functions created by {@code useTranslations('namespace')} or
 * {@code getTranslations('namespace')}. The namespace is resolved automatically, so {@code t('title')}
 * refers to {@code namespace.title}. Translation files follow the standard next-intl layout:
 * {@code messages/{locale}.json} with nested keys.
 *
 * @author marhali
 */
public class NextIntlModulePreset implements PresetProvider<ProjectConfigModule> {

    private static final String TRANSLATOR_FACTORIES = "useTranslations|getTranslations|createTranslator";

    @Override
    public @NotNull ProjectConfigModule applyPreset(@Nullable ProjectConfigModule previousState) {
        return ProjectConfigModule.builder()
            .id(previousState != null ? previousState.id() : new ModuleId("next-intl"))
            .pathTemplate("$PROJECT_DIR$/messages/{locale}.json")
            .fileCodec(FileCodec.JSON)
            .fileTemplate("[{fileKey}]")
            .keyTemplate("{fileKey:.}")
            .rootDirectory("$PROJECT_DIR$")
            .defaultKeyPrefixes()
            .editorFlavorTemplate("t('{i18nKey}')")
            .editorRules()
            // t('key'), t.rich('key'), t.markup('key'), t.raw('key'), t.has('key')
            // where t originates from useTranslations / getTranslations
            .editorRule(new EditorRule(
                "next-intl-t",
                Set.of(EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT),
                TriggerKind.CALL_ARGUMENT,
                List.of(
                    EditorRuleConstraint.match(RuleConstraintType.CALLABLE_ORIGIN, TRANSLATOR_FACTORIES, TextMatchMode.REGEX),
                    EditorRuleConstraint.exact(RuleConstraintType.ARGUMENT_INDEX, "0")
                ),
                10,
                false
            ))
            // useTranslations('namespace') / getTranslations('namespace')
            .editorRule(new EditorRule(
                "next-intl-namespace",
                Set.of(EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT),
                TriggerKind.CALL_ARGUMENT,
                List.of(
                    EditorRuleConstraint.match(RuleConstraintType.CALLABLE_NAME, TRANSLATOR_FACTORIES, TextMatchMode.REGEX),
                    EditorRuleConstraint.exact(RuleConstraintType.ARGUMENT_INDEX, "0")
                ),
                0,
                false
            ))
            // getTranslations({locale, namespace: 'namespace'})
            .editorRule(new EditorRule(
                "next-intl-namespace-option",
                Set.of(EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT),
                TriggerKind.PROPERTY_VALUE,
                List.of(
                    EditorRuleConstraint.match(RuleConstraintType.CALLABLE_NAME, TRANSLATOR_FACTORIES, TextMatchMode.REGEX),
                    EditorRuleConstraint.exact(RuleConstraintType.PROPERTY_NAME, "namespace")
                ),
                0,
                false
            ))
            .build();
    }
}
