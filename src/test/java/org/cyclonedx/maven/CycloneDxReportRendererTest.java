package org.cyclonedx.maven;

import org.apache.maven.doxia.sink.impl.Xhtml5BaseSink;
import org.cyclonedx.model.Bom;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the SBOM fixtures of {@code src/test/resources/report} with a real Doxia XHTML5 sink.
 */
class CycloneDxReportRendererTest {
    private static final String APP = "pkg:maven/com.example/demo-app@1.0.0?type=jar";
    private static final String COMMONS_TEXT = "pkg:maven/org.apache.commons/commons-text@1.12.0?type=jar";
    private static final String COMMONS_LANG3 = "pkg:maven/org.apache.commons/commons-lang3@3.14.0?type=jar";
    private static final String ODD_LIB = "pkg:maven/com.example/odd-lib@2.0?type=jar";

    static File fixture(final String name) throws Exception {
        return new File(CycloneDxReportRendererTest.class.getResource("/report/" + name).toURI());
    }

    private static String render(final String fixture, final String... bomFileNames) throws Exception {
        final Bom bom = CycloneDxReportMojo.readBom(fixture(fixture));
        final StringWriter writer = new StringWriter();
        new CycloneDxReportRenderer(new Xhtml5BaseSink(writer), bom, CycloneDxReportMojo.getBundle(Locale.ENGLISH),
                Arrays.asList(bomFileNames)).render();
        return writer.toString();
    }

    private static void assertContains(final String html, final String expected) {
        assertTrue(html.contains(expected), () -> "expected to find '" + expected + "' in:\n" + html);
    }

    private static void assertNotContains(final String html, final String unexpected) {
        assertFalse(html.contains(unexpected), () -> "did not expect '" + unexpected + "' in:\n" + html);
    }

    @Test
    void rendersMetadataAndLinksTheMachineReadableFiles() throws Exception {
        final String html = render("bom-1.7.json", "bom.json", "bom.xml");

        assertContains(html, "<a href=\"bom.json\">bom.json</a>");
        assertContains(html, "<a href=\"bom.xml\">bom.xml</a>");
        assertContains(html, "urn:uuid:3e383c4c-ef61-3eba-8214-3ecd46c4bbee");
        assertContains(html, "com.example:demo-app:1.0.0");
        assertContains(html, "<code>" + APP + "</code>");
        assertContains(html, "org.cyclonedx:cyclonedx-maven-plugin 2.10.0");
        assertContains(html, "maven.scopes");
        assertContains(html, "compile,provided,runtime,system");
        assertContains(html, "cdx:reproducible");
    }

    @Test
    void doesNotInventTheTimestampOfReproducibleSboms() throws Exception {
        // the fixture has no timestamp, like SBOMs generated with project.build.outputTimestamp
        assertNull(CycloneDxReportMojo.readBom(fixture("bom-1.7.json")).getMetadata().getTimestamp());

        assertNotNull(CycloneDxReportMojo.readBom(fixture("bom-1.4.xml")).getMetadata().getTimestamp());
        assertContains(render("bom-1.4.xml"), "2024-01-02T03:04:05Z");
        assertContains(render("bom-empty.json"), "2024-05-06T07:08:09Z");
    }

    @Test
    void rendersComponentsSortedWithLicensesAndHashes() throws Exception {
        final String html = render("bom-1.7.json");

        // summary table sorted by group then name: com.example:dual, com.example:odd-lib, then org.apache.commons ones
        final int start = html.indexOf("<h2>Components</h2>");
        final String table = html.substring(start, html.indexOf("</table>", start));
        final int dual = table.indexOf(">com.example:dual<");
        final int oddLib = table.indexOf(">com.example:odd-lib<");
        final int lang3 = table.indexOf(">org.apache.commons:commons-lang3<");
        final int text = table.indexOf(">org.apache.commons:commons-text<");
        assertTrue(0 < dual && dual < oddLib && oddLib < lang3 && lang3 < text, table);
        assertContains(table, "<td>vers:maven/*</td>");

        assertContains(html, "href=\"https://spdx.org/licenses/Apache-2.0.html\"");
        assertContains(html, "EPL-2.0 OR GPL-2.0-with-classpath-exception");
        assertContains(html, "SHA-256: <code>de023e1cb9fb2a2ea4ab0f3d3de4b5c0b4e2ef1fb39c8e3e4fd26d1e2a3e7f0a</code>");
        assertContains(html, "website: <a class=\"externalLink\" href=\"https://commons.apache.org/proper/commons-text\"");
        assertContains(html, "The Apache Software Foundation");
    }

    @Test
    void linksTheDependencyGraphBothWays() throws Exception {
        final String html = render("bom-1.7.json");

        // every component gets an anchor, used by the summary table and the dependency graph
        assertContains(html, "id=\"" + CycloneDxReportRenderer.anchor(COMMONS_LANG3) + "\"");
        final String lang3Link = "<a href=\"#" + CycloneDxReportRenderer.anchor(COMMONS_LANG3) + "\">"
                + "org.apache.commons:commons-lang3:3.14.0</a>";
        assertContains(html, lang3Link);

        // commons-lang3 is required by commons-text and odd-lib, both rendered as links
        final String lang3Details = html.substring(html.indexOf("id=\"" + CycloneDxReportRenderer.anchor(COMMONS_LANG3) + "\""));
        final String requiredBy = lang3Details.substring(lang3Details.indexOf("Required by"));
        assertTrue(requiredBy.indexOf("#" + CycloneDxReportRenderer.anchor(ODD_LIB)) > 0, requiredBy);
        assertTrue(requiredBy.indexOf("#" + CycloneDxReportRenderer.anchor(COMMONS_TEXT)) > 0, requiredBy);

        // direct dependencies of the application, in declaration order
        final String direct = html.substring(html.indexOf("Direct Dependencies"), html.indexOf("Components</h2>"));
        assertTrue(direct.indexOf("commons-text") < direct.indexOf("odd-lib"), direct);
        assertTrue(direct.indexOf("odd-lib") < direct.indexOf("com.example:dual"), direct);

        // the application itself is not a component: rendered as text, not as a link
        assertContains(html, "com.example:demo-app:1.0.0");
        assertNotContains(html, "href=\"#" + CycloneDxReportRenderer.anchor(APP) + "\"");
    }

    @Test
    void rendersCycloneDx17ExternalComponents() throws Exception {
        final String html = render("bom-1.7.json");

        final String oddLib = html.substring(html.indexOf("id=\"" + CycloneDxReportRenderer.anchor(ODD_LIB) + "\""));
        assertContains(oddLib, "External");
        assertContains(oddLib, "Yes");
        assertContains(oddLib, "vers:maven/*");
    }

    @Test
    void rendersUntrustedContentAsPlainText() throws Exception {
        final String html = render("bom-1.7.json");

        // no link pattern nor expression interpretation, and markup is escaped
        assertContains(html, "{braces}");
        assertContains(html, "${expressions}");
        assertContains(html, "&lt;b&gt;markup&lt;/b&gt;");
        assertNotContains(html, "<b>markup</b>");
        assertContains(html, "{v2}");

        // only http(s) URLs become links
        assertNotContains(html, "href=\"javascript:");
        assertContains(html, "javascript:alert(2)");
        assertContains(html, "(not a web link)");
    }

    @Test
    void rendersLegacyToolsAndLicenseUrls() throws Exception {
        final String html = render("bom-1.4.xml");

        assertContains(html, "OWASP Foundation CycloneDX Maven plugin 2.7.9");
        assertContains(html, "<a class=\"externalLink\" href=\"http://www.eclipse.org/legal/epl-v10.html\">"
                + "Eclipse Public License 1.0</a>");
        assertContains(html, "SHA-1: <code>8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12</code>");
    }

    @Test
    void rendersSbomWithoutComponents() throws Exception {
        final String html = render("bom-empty.json");

        assertContains(html, "This SBOM does not list any component.");
        assertNotContains(html, "Component Details");
        assertEquals(-1, html.indexOf("<li>"), html);
    }
}
