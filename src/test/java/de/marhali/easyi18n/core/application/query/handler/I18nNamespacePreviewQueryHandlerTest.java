package de.marhali.easyi18n.core.application.query.handler;

import de.marhali.easyi18n.core.adapters.InMemoryProjectConfigAdapter;
import de.marhali.easyi18n.core.application.cqrs.PossiblyUnavailable;
import de.marhali.easyi18n.core.application.query.I18nNamespacePreviewQuery;
import de.marhali.easyi18n.core.application.service.I18nKeyCandidateResolver;
import de.marhali.easyi18n.core.application.service.SortableImplementationProvider;
import de.marhali.easyi18n.core.application.state.InMemoryI18nStore;
import de.marhali.easyi18n.core.domain.config.ProjectConfig;
import de.marhali.easyi18n.core.domain.config.ProjectConfigModule;
import de.marhali.easyi18n.core.domain.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link I18nNamespacePreviewQueryHandler}.
 *
 * @author marhali
 */
public class I18nNamespacePreviewQueryHandlerTest {

    private static final ModuleId MODULE_ID = new ModuleId("testModule");
    private static final LocaleId EN = new LocaleId("en");

    private record Fixture(I18nNamespacePreviewQueryHandler handler, InMemoryI18nStore store) {}

    private Fixture buildFixture() {
        var projectConfigPort = new InMemoryProjectConfigAdapter(
            ProjectConfig.fromDefaultPreset().toBuilder()
                .previewLocale(EN)
                .modules()
                .module(ProjectConfigModule.fromDefaultPreset().toBuilder()
                    .id(MODULE_ID)
                    .build())
                .build()
        );
        var store = new InMemoryI18nStore(new SortableImplementationProvider(projectConfigPort));
        var resolver = new I18nKeyCandidateResolver(projectConfigPort, store);
        var handler = new I18nNamespacePreviewQueryHandler(store, resolver, projectConfigPort);
        return new Fixture(handler, store);
    }

    private void populateTranslations(Fixture fixture, String... keys) {
        fixture.store().mutate(project -> {
            var module = project.getOrCreateModule(MODULE_ID);
            module.addLocale(EN);
            for (String key : keys) {
                module.getOrCreateTranslation(I18nKey.of(key)).put(EN, I18nValue.fromEscaped(key));
            }
        });
    }

    private List<I18nEntryPreview> query(Fixture fixture, String candidate) {
        PossiblyUnavailable<List<I18nEntryPreview>> response = fixture.handler().handle(
            new I18nNamespacePreviewQuery(MODULE_ID, I18nKeyCandidate.of(candidate))
        );
        Assert.assertTrue(response.available());
        Assert.assertNotNull(response.result());
        return response.result();
    }

    @Test
    public void test_module_not_loaded_returns_unavailable() {
        var fixture = buildFixture();

        PossiblyUnavailable<List<I18nEntryPreview>> response = fixture.handler().handle(
            new I18nNamespacePreviewQuery(MODULE_ID, I18nKeyCandidate.of("footer"))
        );

        Assert.assertFalse(response.available());
    }

    @Test
    public void test_namespace_returns_sorted_nested_entries() {
        var fixture = buildFixture();
        populateTranslations(fixture, "hello.as.dd", "hello.title", "hello.as.cc", "helloWorld", "other.title");

        List<String> keys = query(fixture, "hello").stream().map(entry -> entry.key().canonical()).toList();

        Assert.assertEquals(List.of("hello.as.cc", "hello.as.dd", "hello.title"), keys);
    }

    @Test
    public void test_nested_namespace_returns_nested_entries() {
        var fixture = buildFixture();
        populateTranslations(fixture, "hello.as.dd", "hello.title");

        List<String> keys = query(fixture, "hello.as").stream().map(entry -> entry.key().canonical()).toList();

        Assert.assertEquals(List.of("hello.as.dd"), keys);
    }

    @Test
    public void test_colon_separated_namespace_returns_nested_entries() {
        var fixture = buildFixture();
        populateTranslations(fixture, "common:title");

        Assert.assertEquals(1, query(fixture, "common").size());
    }

    @Test
    public void test_leaf_or_unknown_key_returns_empty_list() {
        var fixture = buildFixture();
        populateTranslations(fixture, "hello.title", "helloWorld");

        Assert.assertTrue(query(fixture, "hello.title").isEmpty());
        Assert.assertTrue(query(fixture, "hell").isEmpty());
        Assert.assertTrue(query(fixture, "unknown").isEmpty());
        Assert.assertTrue(query(fixture, "").isEmpty());
    }
}
