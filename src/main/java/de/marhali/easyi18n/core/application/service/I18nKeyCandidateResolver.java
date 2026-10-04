package de.marhali.easyi18n.core.application.service;

import de.marhali.easyi18n.core.application.state.I18nStore;
import de.marhali.easyi18n.core.domain.config.ProjectConfig;
import de.marhali.easyi18n.core.domain.config.ProjectConfigModule;
import de.marhali.easyi18n.core.domain.model.*;
import de.marhali.easyi18n.core.ports.ProjectConfigPort;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Resolves {@link I18nKeyCandidate}'s against the underlying {@link I18nStore}
 * with support for {@link I18nKeyPrefix}'es.
 *
 * @author marhali
 */
public class I18nKeyCandidateResolver {

    private final @NotNull ProjectConfigPort projectConfigPort;
    private final @NotNull I18nStore store;

    public I18nKeyCandidateResolver(@NotNull ProjectConfigPort projectConfigPort, @NotNull I18nStore store) {
        this.projectConfigPort = projectConfigPort;
        this.store = store;
    }

    public @Nullable I18nEntry resolveExact(@NotNull ModuleId moduleId, @NotNull I18nKeyCandidate keyCandidate) {
        I18nModule moduleStore = store.getSnapshot().getModuleOrThrow(moduleId);

        for (I18nKey key : constructKeys(moduleId, keyCandidate)) {
            if (moduleStore.hasTranslation(key)) {
                // First match will resolve
                return new I18nEntry(key, Objects.requireNonNull(moduleStore.getTranslation(key)));
            }
        }

        return null;
    }

    /**
     * Resolves the given key candidate as a namespace, i.e. an intermediate node of nested translation keys.
     * @param moduleId Module identifier
     * @param keyCandidate Translation key candidate (e.g. {@code footer} for {@code footer.title})
     * @return Sorted list of child entries or an empty list if the candidate does not denote a namespace
     */
    public @NotNull List<@NotNull I18nEntry> resolveNamespace(@NotNull ModuleId moduleId, @NotNull I18nKeyCandidate keyCandidate) {
        I18nModule moduleStore = store.getSnapshot().getModuleOrThrow(moduleId);

        for (I18nKey key : constructNamespaceKeys(moduleId, keyCandidate)) {
            List<I18nEntry> children = new ArrayList<>();

            for (Map.Entry<@NotNull I18nKey, @NotNull I18nContent> entry : moduleStore.translations().entrySet()) {
                if (isNamespaceOf(key, entry.getKey())) {
                    children.add(I18nEntry.fromEntry(entry));
                }
            }

            if (!children.isEmpty()) {
                // First match will resolve
                children.sort(Comparator.comparing(I18nEntry::key));
                return children;
            }
        }

        return List.of();
    }

    private @NotNull Set<@NotNull I18nKey> constructNamespaceKeys(@NotNull ModuleId moduleId, @NotNull I18nKeyCandidate keyCandidate) {
        var keys = new LinkedHashSet<I18nKey>();

        for (I18nKey key : constructKeys(moduleId, keyCandidate)) {
            keys.add(key);

            // Namespace declarations like useTranslations('connector.domains') in namespace file layout
            String namespaceFileKey = I18nKeyCandidate.toNamespaceFileLayout(key.canonical());
            if (namespaceFileKey != null) {
                keys.add(I18nKey.of(namespaceFileKey));
            }
        }

        return keys;
    }

    private boolean isNamespaceOf(@NotNull I18nKey namespace, @NotNull I18nKey key) {
        String ns = namespace.canonical();
        String canonical = key.canonical();

        if (ns.isEmpty() || canonical.length() <= ns.length() + 1 || !canonical.startsWith(ns)) {
            return false;
        }

        String separator = canonical.substring(ns.length(), ns.length() + 1);
        return I18nKeyCandidate.NAMESPACE_SEPARATORS.contains(separator);
    }

    private @NotNull Set<@NotNull I18nKey> constructKeys(@NotNull ModuleId moduleId, @NotNull I18nKeyCandidate keyCandidate) {
        // Ordered by preference: qualified variants first, prefixed variants afterwards
        var keys = new LinkedHashSet<I18nKey>();
        List<String> qualifiedKeys = keyCandidate.qualified();

        for (String qualifiedKey : qualifiedKeys) {
            keys.add(I18nKey.of(qualifiedKey));
        }

        for (I18nKeyPrefix keyPrefix : getModuleDefaultKeyPrefixes(moduleId)) {
            for (String qualifiedKey : qualifiedKeys) {
                keys.add(keyPrefix.withKey(qualifiedKey));
            }
        }

        return keys;
    }

    private @NotNull Set<@NotNull I18nKeyPrefix> getModuleDefaultKeyPrefixes(@NotNull ModuleId moduleId) {
        ProjectConfig projectConfig = projectConfigPort.read();
        ProjectConfigModule moduleConfig = projectConfig.modules().get(moduleId);

        if (moduleConfig == null) {
            throw new IllegalArgumentException("Unknown module: " + moduleId);
        }

        return moduleConfig.defaultKeyPrefixes();
    }
}
