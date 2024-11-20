package org.cyclonedx.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.model.Developer;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.OrganizationalContact;
import org.cyclonedx.model.OrganizationalEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseCycloneDxMojoTest {

    /**
     * Minimal test class that can create an instance of BaseCycloneDxMojo
     * so we can do some unit testing.
     */
    private static class BaseCycloneDxMojoImpl extends BaseCycloneDxMojo {

        @Override
        protected String extractComponentsAndDependencies(Set<String> topLevelComponents,
            Map<String, Component> components, Map<String, Dependency> dependencies) {
            return "";
        }
    }

    @Test
    @DisplayName("")
    void createListOfAuthors() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        OrganizationalEntity manufacturer = new OrganizationalEntity();
        List<Developer> developers = new ArrayList<>();
        Developer developer = new Developer();
        developer.setName("Developer");
        developers.add(developer);
        developer = new Developer();
        developer.setEmail("Developer@foo.com");
        developers.add(developer);
        developer = new Developer();
        developer.setOrganization("My Organization");
        developers.add(developer);
        developer = new Developer();
        developer.setOrganizationUrl("http://foo.com");
        developers.add(developer);
        List<OrganizationalContact> listOfAuthors = mojo.createListOfAuthors(manufacturer, developers);
        assertNotNull(listOfAuthors);
        assertEquals(4, listOfAuthors.size());
        assertEquals("Developer", listOfAuthors.get(0).getName());
    }

    @Test
    @DisplayName("Verify addContacts")
    void addContacts() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        OrganizationalEntity manufacturer = new OrganizationalEntity();
        List<Developer > developers = new ArrayList<>();
        mojo.addContacts(manufacturer, developers);
        assertNotNull(manufacturer.getContacts());
        assertTrue(manufacturer.getContacts().isEmpty());
    }

    @Test
    @DisplayName("Verify addUrl")
    void addUrl() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        OrganizationalEntity manufacturer = new OrganizationalEntity();
        assertNull(manufacturer.getUrls());
        mojo.addUrl(manufacturer, "http://foo.com");
        assertNotNull(manufacturer.getUrls());
        assertEquals(1, manufacturer.getUrls().size());
        mojo.addUrl(manufacturer, "http://example.com");
        assertEquals(2, manufacturer.getUrls().size());
    }


    @Test
    @DisplayName("Verify that check of String content works")
    void isNotNullOrEmpty() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        String value = null;
        assertFalse(mojo.isNotNullOrEmpty(value));
        value = "";
        assertFalse(mojo.isNotNullOrEmpty(value));
        value = "null";
        assertTrue(mojo.isNotNullOrEmpty(value));
    }

}