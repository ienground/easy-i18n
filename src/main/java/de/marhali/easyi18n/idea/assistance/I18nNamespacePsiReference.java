package de.marhali.easyi18n.idea.assistance;

import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Namespace reference for psi elements, e.g. {@code useTranslations('footer')}.
 *
 * @param <T> Psi element type
 *
 * @author marhali
 */
public final class I18nNamespacePsiReference<T extends PsiElement> extends PsiReferenceBase<T> {

    private final @NotNull ModuleId moduleId;
    private final @NotNull String namespace;
    private final @NotNull List<I18nEntryPreview> children;

    public I18nNamespacePsiReference(
        @NotNull T element, @NotNull ModuleId moduleId, @NotNull String namespace, @NotNull List<I18nEntryPreview> children
    ) {
        super(element, ElementManipulators.getValueTextRange(element), true);

        this.moduleId = moduleId;
        this.namespace = namespace;
        this.children = children;
    }

    @Override
    public PsiElement resolve() {
        return new I18nNamespacePsiElement(myElement, moduleId, namespace, children);
    }
}
