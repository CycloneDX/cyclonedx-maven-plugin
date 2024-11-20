package org.cyclonedx.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.OrganizationalContact;
import org.cyclonedx.model.OrganizationalEntity;
import org.cyclonedx.model.organization.PostalAddress;
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
            Map<String, Component> components, Map<String, Dependency> dependencies) throws MojoExecutionException {
            return "";
        }
    }

    @Test
    @DisplayName("Verify that the default configuration does not have manufacturer information ")
    void hasNoManufacturerInformation() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        assertFalse(mojo.hasManufacturerInformation());
    }

    @Test
    @DisplayName("Verify that the function hasManufacturerInformation works as expected")
    void hasManufacturerInformation() throws NoSuchFieldException, IllegalAccessException {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        OrganizationalEntity manufacturer = new OrganizationalEntity();
        manufacturer.setName("Manufacturer");
        setParentParameter(mojo, "manufacturer", manufacturer);
        assertTrue(mojo.hasManufacturerInformation());

        manufacturer = new OrganizationalEntity();
        setParentParameter(mojo, "manufacturer", manufacturer);
        PostalAddress address = new PostalAddress();
        address.setCountry("UK");
        manufacturer.setAddress(address);
        assertTrue(mojo.hasManufacturerInformation());

        manufacturer = new OrganizationalEntity();
        setParentParameter(mojo, "manufacturer", manufacturer);
        OrganizationalContact contact = new OrganizationalContact();
        contact.setName("Contact");
        List<OrganizationalContact> contacts = new ArrayList<>();
        contacts.add(contact);
        manufacturer.setContacts(contacts);
        assertTrue(mojo.hasManufacturerInformation());

        manufacturer = new OrganizationalEntity();
        setParentParameter(mojo, "manufacturer", manufacturer);
        List<String> urls = new ArrayList<>();
        urls.add("https://www.owasp.org");
        manufacturer.setUrls(urls);
        assertTrue(mojo.hasManufacturerInformation());

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

    @Test
    @DisplayName("Verify that a list of strings works as expected")
    void isNotNullOrEmptyString() {
        List<String> list = null;
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        assertFalse(mojo.isNotNullOrEmptyString(list));
        list = new ArrayList<>();
        assertFalse(mojo.isNotNullOrEmptyString(list));
        String value = null;
        list.add(value);
        assertFalse(mojo.isNotNullOrEmptyString(list));
        list.add("");
        assertFalse(mojo.isNotNullOrEmptyString(list));
        list.add("null");
        assertTrue(mojo.isNotNullOrEmptyString(list));
    }

    @Test
    @DisplayName("Verify that a list of contacts works as expected")
    void isNotNullOrEmptyContacts() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        List<OrganizationalContact> list = null;
        assertFalse(mojo.isNotNullOrEmptyContacts(list));
        list = new ArrayList<>();
        assertFalse(mojo.isNotNullOrEmptyContacts(list));
        OrganizationalContact contact = new OrganizationalContact();
        contact.setName("Contact");
        list.add(contact);
        assertTrue(mojo.isNotNullOrEmptyContacts(list));
    }

    @Test
    @DisplayName("Verify that check of address works as expected")
    void testIsNotNullOrEmpty() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        PostalAddress address = new PostalAddress();
        assertFalse(mojo.isNotNullOrEmpty(address));
        address.setRegion("AL");
        assertTrue(mojo.isNotNullOrEmpty(address));

        address = new PostalAddress();
        address.setPostOfficeBoxNumber("12345");
        assertTrue(mojo.isNotNullOrEmpty(address));

        address = new PostalAddress();
        address.setLocality("my locality");
        assertTrue(mojo.isNotNullOrEmpty(address));

        address = new PostalAddress();
        address.setPostalCode("12345");
        assertTrue(mojo.isNotNullOrEmpty(address));

        address = new PostalAddress();
        address.setCountry("US");
        assertTrue(mojo.isNotNullOrEmpty(address));

        address = new PostalAddress();
        address.setStreetAddress("Main street");
        assertTrue(mojo.isNotNullOrEmpty(address));
    }

    @Test
    @DisplayName("Verify that test of contact works as expected")
    void testIsNotNullOrEmpty1() {
        BaseCycloneDxMojoImpl mojo = new BaseCycloneDxMojoImpl();
        OrganizationalContact contact = null;
        assertFalse(mojo.isNotNullOrEmpty(contact));
        contact = new OrganizationalContact();
        assertFalse(mojo.isNotNullOrEmpty(contact));
        contact.setPhone("1-555-888-1234");
        assertTrue(mojo.isNotNullOrEmpty(contact));

        contact = new OrganizationalContact();
        contact.setEmail("info@example.com");
        assertTrue(mojo.isNotNullOrEmpty(contact));

        contact = new OrganizationalContact();
        contact.setName("Contact");
        assertTrue(mojo.isNotNullOrEmpty(contact));
    }

    /**
     * Inject a parameter value to a superclass (even private parameters).
     * <p>example:</p>
     * <br>{@code class B extends A;}
     * <br>{@code class A  {  private Type a; } }
     * <br>
     * <br>{@code setParentParameter(new B(), "a", new Type()); }
     *
     * @param cc The class instance
     * @param fieldName The field name
     * @param value The value
     * @throws NoSuchFieldException If the field does not exist
     * @throws IllegalAccessException If the value is not able to be modified
     */
    public static void setParentParameter(Object cc, String fieldName, Object value)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = cc.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(cc, value);
    }

}