package de.marhali.easyi18n.core.domain.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Translation key candidate.
 *
 * @param canonical Canonical translation key candidate. Relative to the namespace if one is set
 * @param namespace Namespace the candidate is scoped to (e.g. {@code footer} from {@code useTranslations('footer')})
 *
 * @author marhali
 */
public record I18nKeyCandidate(
    @NotNull String canonical,
    @Nullable String namespace
) {
    /**
     * Characters that may separate a namespace from the nested key, depending on the module key layout
     * (e.g. {@code footer.title} for nested keys or {@code footer:title} for namespace files).
     */
    public static final List<String> NAMESPACE_SEPARATORS = List.of(".", ":");

    private static final String NESTED_SEPARATOR = ".";
    private static final String FILE_NAMESPACE_SEPARATOR = ":";

    /**
     * Converts a dot-separated key into the namespace file layout, where the first segment denotes the
     * namespace file (e.g. {@code connector.domains.title} to {@code connector:domains.title}).
     * @param key Dot-separated key
     * @return Key in namespace file layout or {@code null} if the key cannot be converted
     */
    public static @Nullable String toNamespaceFileLayout(@NotNull String key) {
        int index = key.indexOf(NESTED_SEPARATOR);
        if (index <= 0 || key.contains(FILE_NAMESPACE_SEPARATOR)) {
            return null;
        }
        return key.substring(0, index) + FILE_NAMESPACE_SEPARATOR + key.substring(index + 1);
    }

    /**
     * Shorthand to construct a translation key candidate.
     * @param canonical Canonical translation key candidate
     * @return {@link I18nKeyCandidate}
     */
    public static @NotNull I18nKeyCandidate of(@NotNull String canonical) {
        return new I18nKeyCandidate(canonical, null);
    }

    /**
     * Shorthand to construct a translation key candidate that is scoped to a namespace.
     * @param namespace Namespace or {@code null} / empty if unscoped
     * @param canonical Canonical translation key candidate relative to the namespace
     * @return {@link I18nKeyCandidate}
     */
    public static @NotNull I18nKeyCandidate scoped(@Nullable String namespace, @NotNull String canonical) {
        return new I18nKeyCandidate(canonical, namespace == null || namespace.isEmpty() ? null : namespace);
    }

    /**
     * Fully qualified translation key variants of this candidate, in order of preference.
     * Scoped candidates resolve to nested keys ({@code connector.domains.title}) as well as to the
     * namespace file layout ({@code connector:domains.title}).
     * @return List of canonical translation keys
     */
    public @NotNull List<@NotNull String> qualified() {
        if (namespace == null) {
            return List.of(canonical);
        }

        String nested = namespace + NESTED_SEPARATOR + canonical;
        String namespaceFile = toNamespaceFileLayout(nested);

        return namespaceFile != null ? List.of(nested, namespaceFile) : List.of(nested);
    }

    /**
     * Converts a fully qualified translation key into a key relative to the namespace of this candidate.
     * @param qualifiedKey Fully qualified translation key (e.g. {@code footer.title} or {@code footer:title})
     * @return Relative key (e.g. {@code title}) or {@code null} if the key is not part of the namespace
     */
    public @Nullable String relativize(@NotNull String qualifiedKey) {
        return relativize(namespace, qualifiedKey);
    }

    /**
     * Converts a fully qualified translation key into a key relative to the given namespace.
     * @param namespace Namespace or {@code null} if unscoped
     * @param qualifiedKey Fully qualified translation key (e.g. {@code footer.title} or {@code footer:title})
     * @return Relative key (e.g. {@code title}) or {@code null} if the key is not part of the namespace
     */
    public static @Nullable String relativize(@Nullable String namespace, @NotNull String qualifiedKey) {
        if (namespace == null || namespace.isEmpty()) {
            return qualifiedKey;
        }

        // Normalize namespace file layout (connector:domains.title) to nested layout (connector.domains.title)
        String nestedKey = !namespace.contains(FILE_NAMESPACE_SEPARATOR)
            ? qualifiedKey.replaceFirst(FILE_NAMESPACE_SEPARATOR, NESTED_SEPARATOR)
            : qualifiedKey;
        String prefix = namespace + NESTED_SEPARATOR;

        return nestedKey.startsWith(prefix) && nestedKey.length() > prefix.length()
            ? nestedKey.substring(prefix.length())
            : null;
    }

    /**
     * Preferred fully qualified translation key, e.g. for display purposes.
     * @return Canonical translation key
     */
    public @NotNull String display() {
        return qualified().get(0);
    }
}
