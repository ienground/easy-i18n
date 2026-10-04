package de.marhali.easyi18n.core.application.query;

import de.marhali.easyi18n.core.application.cqrs.SynchronousQuery;
import de.marhali.easyi18n.core.domain.model.I18nEntryPreview;
import de.marhali.easyi18n.core.domain.model.I18nKeyCandidate;
import de.marhali.easyi18n.core.domain.model.ModuleId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Query to retrieve the child entries of a namespace (intermediate node of nested translation keys).
 * Resolves to an empty list if the key candidate does not denote a namespace.
 *
 * @param moduleId Module identifier
 * @param keyCandidate Namespace key candidate
 *
 * @author marhali
 */
public record I18nNamespacePreviewQuery(
    @NotNull ModuleId moduleId,
    @NotNull I18nKeyCandidate keyCandidate
) implements SynchronousQuery<List<I18nEntryPreview>> {
}
