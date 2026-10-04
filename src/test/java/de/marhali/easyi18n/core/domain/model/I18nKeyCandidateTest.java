package de.marhali.easyi18n.core.domain.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link I18nKeyCandidate}.
 *
 * @author marhali
 */
public class I18nKeyCandidateTest {

    @Test
    public void test_unscoped_candidate_is_qualified_as_is() {
        I18nKeyCandidate candidate = I18nKeyCandidate.of("footer.title");

        Assert.assertEquals(List.of("footer.title"), candidate.qualified());
        Assert.assertEquals("any.key", candidate.relativize("any.key"));
    }

    @Test
    public void test_empty_namespace_is_unscoped() {
        Assert.assertNull(I18nKeyCandidate.scoped("", "title").namespace());
        Assert.assertNull(I18nKeyCandidate.scoped(null, "title").namespace());
    }

    @Test
    public void test_scoped_candidate_supports_both_separators() {
        I18nKeyCandidate candidate = I18nKeyCandidate.scoped("connector", "domains.title");

        Assert.assertEquals(List.of("connector.domains.title", "connector:domains.title"), candidate.qualified());
        Assert.assertEquals("connector.domains.title", candidate.display());
    }

    @Test
    public void test_relativize() {
        I18nKeyCandidate candidate = I18nKeyCandidate.scoped("connector", "");

        Assert.assertEquals("domains.title", candidate.relativize("connector.domains.title"));
        Assert.assertEquals("domains.title", candidate.relativize("connector:domains.title"));
        Assert.assertNull(candidate.relativize("connectors.title"));
        Assert.assertNull(candidate.relativize("connector."));
        Assert.assertNull(candidate.relativize("other.title"));
    }

    @Test
    public void test_nested_namespace_supports_namespace_file_layout() {
        I18nKeyCandidate candidate = I18nKeyCandidate.scoped("connector.domains", "patternInvalidToast");

        Assert.assertEquals(
            List.of("connector.domains.patternInvalidToast", "connector:domains.patternInvalidToast"),
            candidate.qualified()
        );
        Assert.assertEquals("patternInvalidToast", candidate.relativize("connector:domains.patternInvalidToast"));
        Assert.assertEquals("patternInvalidToast", candidate.relativize("connector.domains.patternInvalidToast"));
        Assert.assertNull(candidate.relativize("connector:other.patternInvalidToast"));
    }

    @Test
    public void test_namespace_with_colon_is_used_as_is() {
        I18nKeyCandidate candidate = I18nKeyCandidate.scoped("connector:domains", "title");

        Assert.assertEquals(List.of("connector:domains.title"), candidate.qualified());
        Assert.assertEquals("title", candidate.relativize("connector:domains.title"));
    }
}
