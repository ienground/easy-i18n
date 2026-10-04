package de.marhali.easyi18n.idea.assistance;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.idea.toolwindow.I18nToolWindowNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link FakePsiElement} that represents a namespace, i.e. an intermediate node of nested translation keys.
 * Navigation focuses the first nested key inside the translations tool window.
 *
 * @author marhali
 */
public class I18nNamespacePsiElement extends FakePsiElement implements SyntheticElement {

    private final @NotNull Project project;
    private final @NotNull SmartPsiElementPointer<PsiElement> contextPointer;
    private final @NotNull ModuleId moduleId;
    private final @NotNull String namespace;
    private final @NotNull List<I18nEntryPreview> children;

    public I18nNamespacePsiElement(
        @NotNull PsiElement context,
        @NotNull ModuleId moduleId,
        @NotNull String namespace,
        @NotNull List<I18nEntryPreview> children
    ) {
        this.project = context.getProject();
        this.contextPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context);
        this.moduleId = moduleId;
        this.namespace = namespace;
        this.children = children;
    }

    @Override
    public String getName() {
        return namespace;
    }

    @Override
    public String getPresentableText() {
        return "I18nNamespace: " + namespace;
    }

    @Override
    public PsiElement getParent() {
        return contextPointer.getElement();
    }

    @Override
    public PsiFile getContainingFile() {
        PsiElement context = contextPointer.getElement();
        return context != null ? context.getContainingFile() : null;
    }

    @Override
    public boolean canNavigate() {
        return !children.isEmpty();
    }

    @Override
    public boolean canNavigateToSource() {
        return false;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (!children.isEmpty()) {
            I18nToolWindowNavigator.focusKey(project, moduleId, children.get(0).key());
        }
    }

    @Override
    public boolean isValid() {
        return !project.isDisposed() && contextPointer.getElement() != null;
    }
}
