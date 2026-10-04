package de.marhali.easyi18n.idea.assistance.javascript;

import com.intellij.lang.javascript.psi.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Resolves translation functions that are bound to a variable, such as
 * {@code const t = useTranslations('footer')} from use-intl / next-intl.
 *
 * <p>Calls like {@code t('title')}, {@code t.rich('title')} or {@code t.has('title')} are traced back to
 * the factory call that created {@code t}. Its name is exposed as callable origin and a statically known
 * namespace is exposed as key prefix ({@code footer.}) so that the call resolves to {@code footer.title}.
 *
 * @author marhali
 */
public final class JavaScriptTranslatorResolver {

    /**
     * Factory functions which return a translation function scoped to a namespace (use-intl / next-intl).
     */
    private static final Set<String> NAMESPACED_FACTORIES = Set.of("useTranslations", "getTranslations", "createTranslator");

    /**
     * Methods available on a translation function (e.g. {@code t.rich('key')}).
     */
    private static final Set<String> TRANSLATOR_METHODS = Set.of("rich", "markup", "raw", "has");

    private static final String NAMESPACE_OPTION = "namespace";
    private static final String NAMESPACE_SEPARATOR = ".";

    private JavaScriptTranslatorResolver() {}

    /**
     * Translation function details.
     *
     * @param origin Name of the factory function that created the translation function
     * @param namespace Statically known namespace or {@code null} if unscoped or unknown
     */
    public record Translator(@NotNull String origin, @Nullable String namespace) {
        public @Nullable String keyPrefix() {
            return namespace != null && !namespace.isEmpty() ? namespace + NAMESPACE_SEPARATOR : null;
        }
    }

    /**
     * Resolves the translation function invoked by the given call expression.
     * @param callExpression Call expression (e.g. {@code t('key')})
     * @return {@link Translator} or {@code null} if the callee is not bound to a factory call
     */
    public static @Nullable Translator resolve(@NotNull JSCallExpression callExpression) {
        JSReferenceExpression translatorReference = translatorReference(callExpression.getMethodExpression());
        if (translatorReference == null || DumbService.isDumb(callExpression.getProject())) {
            return null;
        }

        PsiElement resolved;
        try {
            resolved = translatorReference.resolve();
        } catch (PsiInvalidElementAccessException ignored) {
            return null;
        }

        if (!(resolved instanceof JSVariable variable)
            || !(unwrap(variable.getInitializer()) instanceof JSCallExpression factoryCall)) {
            return null;
        }

        String factoryName = callableName(factoryCall);
        if (factoryName == null) {
            return null;
        }

        String namespace = NAMESPACED_FACTORIES.contains(factoryName) ? extractNamespace(factoryCall) : null;
        return new Translator(factoryName, namespace);
    }

    /**
     * Resolves the key prefix applied to the given literal if it is the key argument of a translation function.
     * @param literal String literal
     * @return Key prefix (e.g. {@code footer.}) or {@code null}
     */
    public static @Nullable String resolveKeyPrefix(@NotNull JSLiteralExpression literal) {
        if (!(literal.getParent() instanceof JSArgumentList argumentList)
            || !(argumentList.getParent() instanceof JSCallExpression callExpression)) {
            return null;
        }

        JSExpression[] arguments = argumentList.getArguments();
        if (arguments.length == 0 || arguments[0] != literal) {
            return null;
        }

        Translator translator = resolve(callExpression);
        return translator != null ? translator.keyPrefix() : null;
    }

    /**
     * Checks whether the given literal declares the namespace of a translation function factory,
     * e.g. {@code useTranslations('footer')} or {@code getTranslations({namespace: 'footer'})}.
     * @param literal String literal
     * @return {@code true} if the literal denotes a namespace, otherwise {@code false}
     */
    public static boolean isNamespaceDeclaration(@NotNull JSLiteralExpression literal) {
        PsiElement parent = literal.getParent();

        if (parent instanceof JSProperty property
            && NAMESPACE_OPTION.equals(property.getName())
            && property.getParent() instanceof JSObjectLiteralExpression objectLiteral) {
            parent = objectLiteral.getParent();
            return parent instanceof JSArgumentList argumentList && isNamespacedFactoryCall(argumentList);
        }

        return parent instanceof JSArgumentList argumentList
            && argumentList.getArguments().length > 0
            && argumentList.getArguments()[0] == literal
            && isNamespacedFactoryCall(argumentList);
    }

    private static boolean isNamespacedFactoryCall(@NotNull JSArgumentList argumentList) {
        return argumentList.getParent() instanceof JSCallExpression callExpression
            && NAMESPACED_FACTORIES.contains(callableName(callExpression));
    }

    private static @Nullable JSReferenceExpression translatorReference(@Nullable JSExpression methodExpression) {
        if (!(methodExpression instanceof JSReferenceExpression reference)) {
            return null;
        }

        JSExpression qualifier = reference.getQualifier();
        if (qualifier == null) {
            return reference; // t('key')
        }

        if (qualifier instanceof JSReferenceExpression qualifierReference
            && qualifierReference.getQualifier() == null
            && TRANSLATOR_METHODS.contains(reference.getReferenceName())) {
            return qualifierReference; // t.rich('key')
        }

        return null;
    }

    private static @Nullable String extractNamespace(@NotNull JSCallExpression factoryCall) {
        JSExpression[] arguments = factoryCall.getArguments();
        if (arguments.length == 0) {
            return null;
        }

        JSExpression argument = unwrap(arguments[0]);

        if (argument instanceof JSObjectLiteralExpression options) {
            JSProperty namespaceProperty = options.findProperty(NAMESPACE_OPTION);
            argument = namespaceProperty != null ? unwrap(namespaceProperty.getValue()) : null;
        }

        return argument instanceof JSLiteralExpression literal && literal.isStringLiteral()
            ? literal.getStringValue()
            : null;
    }

    private static @Nullable String callableName(@NotNull JSCallExpression callExpression) {
        return callExpression.getMethodExpression() instanceof JSReferenceExpression reference
            ? reference.getReferenceName()
            : null;
    }

    private static @Nullable JSExpression unwrap(@Nullable JSExpression expression) {
        JSExpression current = expression;
        while (true) {
            if (current instanceof JSParenthesizedExpression parenthesized) {
                current = parenthesized.getInnerExpression();
            } else if (current instanceof JSPrefixExpression prefix) {
                current = prefix.getExpression(); // await getTranslations('ns')
            } else {
                return current;
            }
        }
    }
}
