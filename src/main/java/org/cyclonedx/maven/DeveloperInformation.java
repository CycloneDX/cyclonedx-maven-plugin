/*
 * Copyright (c) Giesecke+Devrient Mobile Security GmbH 2018-2024
 */
package org.cyclonedx.maven;

import java.util.ArrayList;
import java.util.List;
import org.cyclonedx.model.OrganizationalContact;

/**
 * Help class for parse a list of developers
 */
class DeveloperInformation {

    private final List<OrganizationalContact> contacts = new ArrayList<>();
    private String organization;
    private final List<String> urls = new ArrayList<>();

    /**
     * Add contact information
     *
     * @param contact The contact
     */
    void addOrganizationalContact(OrganizationalContact contact) {
        contacts.add(contact);
    }

    /**
     * If Maven section "<organization>" is missing, see if we can find any organization information from
     * a developers section
     * @param organization The organization name
     */
    void setOrganization(String organization) {
        if (this.organization == null && organization != null) {
            this.organization = organization;
        }
    }

    /**
     * Add a defined url
     * @param url The url
     */
    void addUrl(String url) {
        if (url != null) {
            urls.add(url);
        }
    }

    /**
     * @return List of contacts
     */
    public List<OrganizationalContact> getContacts() {
        return contacts;
    }

    /**
     * @return First organization name if found
     */
    public String getOrganization() {
        return organization;
    }

    /**
     * @return List of configured urls
     */
    public List<String> getUrls() {
        return urls;
    }
}
