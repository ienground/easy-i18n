package de.marhali.easyi18n.core.application.query.handler;

import de.marhali.easyi18n.core.application.cqrs.PossiblyUnavailable;
import de.marhali.easyi18n.core.application.cqrs.SynchronousQueryHandler;
import de.marhali.easyi18n.core.application.query.I18nNamespacePreviewQuery;
import de.marhali.easyi18n.core.application.service.I18nKeyCandidateResolver;
import de.marhali.easyi18n.core.application.state.I18nStore;
import de.marhali.easyi18n.core.domain.model.*;
import de.marhali.easyi18n.core.ports.ProjectConfigPort;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Query handler for {@link I18nNamespacePreviewQuery}.
 *
 * @author marhali
 */
public class I18nNamespacePreviewQueryHandler implements SynchronousQueryHandler<I18nNamespacePreviewQuery, List<I18nEntryPreview>> {

    private final @NotNull I18nStore store;
    private final @NotNull I18nKeyCandidateResolver keyResolver;
    private final @NotNull ProjectConfigPort projectConfigPort;

    public I18nNamespacePreviewQueryHandler(@NotNull I18nStore store, @NotNull I18nKeyCandidateResolver keyResolver, @NotNull ProjectConfigPort projectConfigPort) {
        this.store = store;
        this.keyResolver = keyResolver;
        this.projectConfigPort = projectConfigPort;
    }

    @Override
    public @NotNull PossiblyUnavailable<List<I18nEntryPreview>> handle(@NotNull I18nNamespacePreviewQuery query) {
        ModuleId moduleId = query.moduleId();

        if (!store.getSnapshot().hasModule(moduleId)) {
            return PossiblyUnavailable.unavailable();
        }

        LocaleId previewLocaleId = projectConfigPort.read().previewLocale();

        List<I18nEntryPreview> children = keyResolver.resolveNamespace(moduleId, query.keyCandidate()).stream()
            .map(entry -> I18nEntryPreview.fromEntry(entry, previewLocaleId))
            .toList();

        return PossiblyUnavailable.available(children);
    }
}
