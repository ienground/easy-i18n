package de.marhali.easyi18n.idea.assistance.javascript;

import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import de.marhali.easyi18n.core.domain.rules.EditorElement;
import de.marhali.easyi18n.core.domain.rules.EditorLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for {@link JavaScriptTranslatorResolver} and the namespace facts of {@link JavaScriptEditorElementExtractor}
 * using use-intl / next-intl code.
 *
 * @author marhali
 */
public class JavaScriptTranslatorResolverTest extends BasePlatformTestCase {

    private static final String COMPONENT = """
        import {useTranslations} from 'next-intl';
        import {getTranslations} from 'next-intl/server';

        export function Footer() {
          const t = useTranslations('footer');
          const tShell = useTranslations('shell');
          const tLinks = useTranslations('footer.links');
          const tAll = useTranslations();
          return <div>{t('title')}{tShell('menu.open')}{t.rich('rich')}{tAll('root.key')}{tLinks('about')}</div>;
        }

        export async function generateMetadata({locale}) {
          const tMeta = await getTranslations({locale, namespace: 'meta'});
          return {title: tMeta('description')};
        }

        function translate(key) { return key; }
        const plain = translate('plain');
        """;

    private PsiFile configure() {
        return myFixture.configureByText("Footer.tsx", COMPONENT);
    }

    private @NotNull JSLiteralExpression literal(@NotNull PsiFile file, @NotNull String value) {
        List<JSLiteralExpression> matches = PsiTreeUtil.findChildrenOfType(file, JSLiteralExpression.class).stream()
            .filter(literal -> value.equals(literal.getStringValue()))
            .toList();
        assertEquals("Expected exactly one literal with value: " + value, 1, matches.size());
        return matches.get(0);
    }

    public void testTsxIsTypeScript() {
        assertTrue(JavaScriptEditorElementExtractor.isTypeScript(configure()));
    }

    public void testNamespaceOfScopedTranslator() {
        PsiFile file = configure();

        assertEquals("footer", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "title")));
        assertEquals("shell", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "menu.open")));
        assertEquals("footer.links", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "about")));
    }

    public void testNamespaceOfTranslatorMethod() {
        assertEquals("footer", JavaScriptTranslatorResolver.resolveNamespace(literal(configure(), "rich")));
    }

    public void testNamespaceOfAwaitedNamespaceOption() {
        assertEquals("meta", JavaScriptTranslatorResolver.resolveNamespace(literal(configure(), "description")));
    }

    public void testUnscopedAndUnrelatedCallsHaveNoNamespace() {
        PsiFile file = configure();

        assertNull(JavaScriptTranslatorResolver.resolveNamespace(literal(file, "root.key")));
        assertNull(JavaScriptTranslatorResolver.resolveNamespace(literal(file, "plain")));
    }

    public void testNamespaceDeclaration() {
        PsiFile file = configure();

        assertTrue(JavaScriptTranslatorResolver.isNamespaceDeclaration(literal(file, "footer")));
        assertTrue(JavaScriptTranslatorResolver.isNamespaceDeclaration(literal(file, "meta")));
        assertFalse(JavaScriptTranslatorResolver.isNamespaceDeclaration(literal(file, "rich")));
        assertFalse(JavaScriptTranslatorResolver.isNamespaceDeclaration(literal(file, "plain")));
    }

    public void testExtractorExposesOriginAndNamespace() {
        PsiFile file = configure();
        JavaScriptEditorElementExtractor extractor = new JavaScriptEditorElementExtractor(EditorLanguage.TYPESCRIPT);

        EditorElement scoped = extractor.extract(literal(file, "menu.open"), file);
        assertNotNull(scoped);
        assertEquals("useTranslations", scoped.callableOrigin());
        assertEquals("tShell", scoped.callableName());
        assertEquals(List.of("shell.menu.open", "shell:menu.open"), scoped.keyCandidate().qualified());

        EditorElement unscoped = extractor.extract(literal(file, "root.key"), file);
        assertNotNull(unscoped);
        assertEquals("useTranslations", unscoped.callableOrigin());
        assertEquals(List.of("root.key"), unscoped.keyCandidate().qualified());

        EditorElement plain = extractor.extract(literal(file, "plain"), file);
        assertNotNull(plain);
        assertNull(plain.callableOrigin());
        assertNull(plain.keyNamespace());

        EditorElement namespaceOption = extractor.extract(literal(file, "meta"), file);
        assertNotNull(namespaceOption);
        assertEquals("getTranslations", namespaceOption.callableName());
        assertEquals("namespace", namespaceOption.propertyName());
    }

    public void testFindScopedTranslatorPrefersMostSpecificNamespace() {
        JSLiteralExpression context = literal(configure(), "title"); // inside Footer()

        assertScopedTranslator("t", "title", "footer.title", context);
        assertScopedTranslator("tLinks", "privacy", "footer.links.privacy", context);
        assertScopedTranslator("tLinks", "privacy", "footer:links.privacy", context);
        assertScopedTranslator("tShell", "title", "shell:title", context);
        assertScopedTranslator("tAll", "other.title", "other.title", context);
    }

    public void testFindScopedTranslatorIgnoresOtherScopes() {
        PsiFile file = configure();

        assertScopedTranslator("tMeta", "title", "meta.title", literal(file, "description"));
        assertNull(JavaScriptTranslatorResolver.findScopedTranslator(literal(file, "plain"), "footer.title"));
    }

    private static final String TYPED_COMPONENTS = """
        import {useTranslations} from 'next-intl';
        import {getTranslations} from 'next-intl/server';

        type Props = { t: ReturnType<typeof useTranslations<'alias'>>; label: string };
        interface IProps { t: ReturnType<typeof useTranslations<"iface">> }

        export function Inline({ t }: { t: ReturnType<typeof useTranslations<'inline'>> }) { return t('inlineKey'); }
        export function Alias({ t }: Props) { return t.rich('aliasKey'); }
        export function Member(props: IProps) { return props.t('memberKey'); }
        export function Param(t: ReturnType<typeof useTranslations<'param'>>) { return t('paramKey'); }
        export async function Server(t: Awaited<ReturnType<typeof getTranslations<'srv'>>>) { return t('serverKey'); }
        export function Unscoped(t: ReturnType<typeof useTranslations>) { return t('unscopedKey'); }
        export function Unrelated(t: (key: string) => string) { return t('unrelatedKey'); }
        """;

    public void testTypedTranslatorNamespaces() {
        PsiFile file = myFixture.configureByText("Typed.tsx", TYPED_COMPONENTS);

        assertEquals("inline", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "inlineKey")));
        assertEquals("alias", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "aliasKey")));
        assertEquals("iface", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "memberKey")));
        assertEquals("param", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "paramKey")));
        assertEquals("srv", JavaScriptTranslatorResolver.resolveNamespace(literal(file, "serverKey")));
        assertNull(JavaScriptTranslatorResolver.resolveNamespace(literal(file, "unscopedKey")));
        assertNull(JavaScriptTranslatorResolver.resolveNamespace(literal(file, "unrelatedKey")));
    }

    public void testTypedTranslatorOrigin() {
        PsiFile file = myFixture.configureByText("Typed.tsx", TYPED_COMPONENTS);
        JavaScriptEditorElementExtractor extractor = new JavaScriptEditorElementExtractor(EditorLanguage.TYPESCRIPT);

        EditorElement server = extractor.extract(literal(file, "serverKey"), file);
        assertNotNull(server);
        assertEquals("getTranslations", server.callableOrigin());
        assertEquals("srv", server.keyNamespace());

        EditorElement unscoped = extractor.extract(literal(file, "unscopedKey"), file);
        assertNotNull(unscoped);
        assertEquals("useTranslations", unscoped.callableOrigin());
        assertNull(unscoped.keyNamespace());

        EditorElement unrelated = extractor.extract(literal(file, "unrelatedKey"), file);
        assertNotNull(unrelated);
        assertNull(unrelated.callableOrigin());
    }

    private void assertScopedTranslator(
        @NotNull String expectedVariable, @NotNull String expectedRelativeKey,
        @NotNull String key, @NotNull JSLiteralExpression context
    ) {
        JavaScriptTranslatorResolver.ScopedTranslator scoped = JavaScriptTranslatorResolver.findScopedTranslator(context, key);
        assertNotNull("No translator found for " + key, scoped);
        assertEquals(expectedVariable, scoped.variableName());
        assertEquals(expectedRelativeKey, scoped.relativeKey());
    }
}
