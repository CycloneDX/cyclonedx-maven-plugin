package org.cyclonedx.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
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
    @DisplayName("Using developers information only")
    void setManufacturer1() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
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
        Component projectBomComponent = new Component();
        MavenProject mavenProject = new MavenProject();
        mavenProject.setDevelopers(developers);
        mojo.setManufacturer(mavenProject, projectBomComponent);
        OrganizationalEntity manufacturer = projectBomComponent.getManufacturer();
        assertNotNull(manufacturer);
        assertEquals(4, manufacturer.getContacts().size());
        assertEquals("Developer", manufacturer.getContacts().get(0).getName());
        assertEquals("My Organization", manufacturer.getName());
    }

    @Test
    @DisplayName("Using developers information  with empty organization")
    void setManufacturer2() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
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
        Component projectBomComponent = new Component();
        MavenProject mavenProject = new MavenProject();
        mavenProject.setDevelopers(developers);
        mavenProject.setOrganization(new Organization());
        mojo.setManufacturer(mavenProject, projectBomComponent);
        OrganizationalEntity manufacturer = projectBomComponent.getManufacturer();
        assertNotNull(manufacturer);
        assertEquals(4, manufacturer.getContacts().size());
        assertEquals("Developer", manufacturer.getContacts().get(0).getName());
        assertEquals("My Organization", manufacturer.getName());
    }

    @Test
    @DisplayName("Using developers and organization information")
    void setManufacturer3() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();

        MavenProject mavenProject = new MavenProject();
        List<Developer> developers = new ArrayList<>();
        Developer developer = new Developer();
        developer.setName("Developer 2");
        developer.setEmail("Developer@foo.com");
        developer.setOrganization("My Organization");
        developer.setOrganizationUrl("http://foo.com");
        developers.add(developer);
        mavenProject.setDevelopers(developers);

        Organization organization = new Organization();
        organization.setName("My Company");
        organization.setUrl("http://example.com");
        mavenProject.setOrganization(organization);

        Component projectBomComponent = new Component();
        mojo.setManufacturer(mavenProject, projectBomComponent);
        OrganizationalEntity manufacturer = projectBomComponent.getManufacturer();
        assertNotNull(manufacturer);
        assertEquals(1, manufacturer.getContacts().size());
        assertEquals("Developer 2", manufacturer.getContacts().get(0).getName());
        assertEquals("My Company", manufacturer.getName());
    }

    @Test
    @DisplayName("Using organization information only")
    void setManufacturer4() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();

        MavenProject mavenProject = new MavenProject();
        List<Developer> developers = new ArrayList<>();
        Organization organization = new Organization();
        organization.setName("My Organization");
        organization.setUrl("http://example.org");
        mavenProject.setOrganization(organization);

        mavenProject.setDevelopers(developers);
        mavenProject.setOrganization(organization);

        Component projectBomComponent = new Component();
        mojo.setManufacturer(mavenProject, projectBomComponent);
        OrganizationalEntity manufacturer = projectBomComponent.getManufacturer();
        assertNotNull(manufacturer);
        assertNull(manufacturer.getContacts());
        assertEquals("My Organization", manufacturer.getName());
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