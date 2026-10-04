package de.marhali.easyi18n.idea.assistance;

import com.intellij.model.Pointer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import de.marhali.easyi18n.idea.icons.PluginIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Documentation target for a namespace, i.e. an intermediate node of nested translation keys.
 * Lists the nested keys together with their preview values.
 *
 * @author marhali
 */
public class I18nNamespaceDocumentationTarget implements DocumentationTarget {

    private static final int MAX_DISPLAYED_CHILDREN = 50;

    private final @NotNull SmartPsiElementPointer<PsiFile> filePointer;
    private final @NotNull ModuleId moduleId;
    private final @NotNull String namespace;
    private final @NotNull List<I18nEntryPreview> children;

    public I18nNamespaceDocumentationTarget(
        @NotNull PsiFile file,
        @NotNull ModuleId moduleId, @NotNull String namespace, @NotNull List<I18nEntryPreview> children
    ) {
        this.filePointer = SmartPointerManager.createPointer(file);
        this.moduleId = moduleId;
        this.namespace = namespace;
        this.children = children;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        SmartPsiElementPointer<PsiFile> stableFilePointer = filePointer;
        List<I18nEntryPreview> stableChildren = children;

        return () -> {
            PsiFile file = stableFilePointer.getElement();
            return file != null
                ? new I18nNamespaceDocumentationTarget(file, moduleId, namespace, stableChildren)
                : null;
        };
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(namespace)
            .containerText(computeDocumentationHint())
            .icon(PluginIcon.TRANSLATE_ICON)
            .locationText(moduleId.name(), PluginIcon.TRANSLATE_ICON)
            .presentation();
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return namespace + " (" + children.size() + " keys)";
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        StringBuilder html = new StringBuilder();
        html.append("<b>").append(StringUtil.escapeXmlEntities(namespace)).append("</b>");
        html.append(" (").append(children.size()).append(" keys)<br/>");

        for (I18nEntryPreview child : children.subList(0, Math.min(children.size(), MAX_DISPLAYED_CHILDREN))) {
            String value = child.previewValue() != null ? child.previewValue().toInputString() : "";
            html.append("<br/>")
                .append(StringUtil.escapeXmlEntities(child.key().canonical()))
                .append("=")
                .append(StringUtil.escapeXmlEntities(value));
        }

        if (children.size() > MAX_DISPLAYED_CHILDREN) {
            html.append("<br/>").append(StringUtil.THREE_DOTS);
        }

        return DocumentationResult.documentation(html.toString());
    }
}
