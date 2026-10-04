package de.marhali.easyi18n.idea.assistance.javascript;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import de.marhali.easyi18n.idea.assistance.I18nKeyPsiReference;
import de.marhali.easyi18n.idea.assistance.I18nNamespacePsiReference;
import org.jetbrains.annotations.Nullable;

/**
 * Prefers translation keys and namespaces over other declarations when navigating from string literals.
 * Without it, TypeScript contributes unrelated declarations with the same property name
 * (e.g. typed next-intl messages), which hides the translation target in a chooser popup.
 *
 * @author marhali
 */
public class JavaScriptI18nGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        JSLiteralExpression literal = PsiTreeUtil.getParentOfType(sourceElement, JSLiteralExpression.class, false);
        if (literal == null || !literal.isStringLiteral()) {
            return null;
        }

        for (PsiReference reference : literal.getReferences()) {
            if (reference instanceof I18nKeyPsiReference<?> || reference instanceof I18nNamespacePsiReference<?>) {
                PsiElement target = reference.resolve();
                return target != null ? new PsiElement[] { target } : null;
            }
        }

        return null;
    }
}
