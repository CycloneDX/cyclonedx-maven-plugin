package org.cyclonedx.maven;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class TestUtils {
    static Element getDependencyNode(final Map<String, Collection<String>> purlToIdentities, final Element dependencies, final String purl) throws Exception {
        int numElements = 0;
        Collection<Element> elements = getDependencyNodes(purlToIdentities, dependencies, purl);
        if (elements != null) {
            numElements = elements.size();
            if (numElements == 1) {
                return elements.iterator().next();
            }
        }
        throw new Exception("Expected a single dependency for purl " + purl + ", found " + numElements + " dependencies");
    }

    static Element getComponentNode(final Map<String, Collection<String>> purlToIdentities, final Element components, final String purl) throws Exception {
        int numElements = 0;
        Collection<Element> elements = getComponentNodes(purlToIdentities, components, purl);
        if (elements != null) {
            numElements = elements.size();
            if (numElements == 1) {
                return elements.iterator().next();
            }
        }
        throw new Exception("Expected a single component for purl " + purl + ", found " + numElements + " components");
    }

    static Collection<Element> getDependencyNodes(final Map<String, Collection<String>> purlToIdentities, final Element dependencies, final String purl) {
        return getChildElements(purlToIdentities, dependencies, purl, "dependency", "ref");
    }

    static Collection<Element> getComponentNodes(final Map<String, Collection<String>> purlToIdentities, final Element components, final String purl) {
        return getChildElements(purlToIdentities, components, purl, "component", "bom-ref");
    }

    static Element getDependencyNodeByIdentity(final Element dependencies, final String identity) throws Exception {
        int numElements = 0;
        Collection<Element> elements = getChildElementsByIdentity(dependencies, Arrays.asList(identity), "dependency", "ref");
        if (elements != null) {
            numElements = elements.size();
            if (numElements == 1) {
                return elements.iterator().next();
            }
        }
        throw new Exception("Expected a single dependency for identity " + identity + ", found " + numElements + " dependencies");
    }

    static Element getComponentNodeByIdentity(final Element components, final String identity) throws Exception {
        int numElements = 0;
        Collection<Element> elements =  getChildElementsByIdentity(components, Arrays.asList(identity), "component", "bom-ref");
        if (elements != null) {
            numElements = elements.size();
            if (numElements == 1) {
                return elements.iterator().next();
            }
        }
        throw new Exception("Expected a single compoennt for identity " + identity + ", found " + numElements + " components");
    }

    private static Collection<Element> getChildElements(final Map<String, Collection<String>> purlToIdentities, final Element parent, final String purl, final String elementName, final String attrName) {
        final Collection<String> identities = purlToIdentities.get(purl);
        return getChildElementsByIdentity(parent, identities, elementName, attrName);
    }

    private static Collection<Element> getChildElementsByIdentity(final Element parent, final Collection<String> identities, final String elementName, final String attrName) {
        if (identities == null) {
            return null;
        }
        final Collection<Element> childElements = new HashSet<>();

        final NodeList children = parent.getChildNodes();
        final int numChildNodes = children.getLength();
        for (int index = 0 ; index < numChildNodes ; index++) {
            final Node child = children.item(index);
            if ((child.getNodeType() == Node.ELEMENT_NODE) && elementName.equals(child.getNodeName())) {
                final Node refNode = child.getAttributes().getNamedItem(attrName);
                if (identities.contains(refNode.getNodeValue())) {
                    childElements.add((Element)child);
                }
            }
        }
        return childElements;
    }

    static Set<String> getComponentReferences(final Element parent) {
        return getReferences(null, parent, "component", "bom-ref");
    }

    static Set<String> getDependencyReferences(final Element parent) {
        return getReferences(null, parent, "dependency", "ref");
    }

    private static Set<String> getReferences(Set<String> references, final Element root, final String elementName, final String attrName) {
        if (references == null) {
            references = new HashSet<>();
        }

        final NodeList components = root.getElementsByTagName(elementName);
        final int numComponents = components.getLength();
        for (int index = 0 ; index < numComponents ; index++) {
            final Element component = (Element) components.item(index);
            final String value = component.getAttribute(attrName);
            if (value != null) {
                references.add(value);
            }
        }
        return references;
    }

    static Document readXML(File file) throws IOException, SAXException, ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setValidating(false);

        final DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    static Map<String, Collection<String>> getPUrlToIdentities(final Element root) {
        final Map<String, Collection<String>> purlToIdentities = new HashMap<>();
        final NodeList components = root.getElementsByTagName("component");
        for (int index = 0 ; index < components.getLength() ; index++) {
            final Element component = (Element)components.item(index);
            final String bomRef = component.getAttribute("bom-ref");
            final String purl = component.getElementsByTagName("purl").item(0).getTextContent();
            final Collection<String> identities = purlToIdentities.get(purl);
            if (identities != null) {
                identities.add(bomRef);
            } else {
                final Collection<String> newIdentities = new HashSet<>();
                newIdentities.add(bomRef);
                purlToIdentities.put(purl, newIdentities);
            }
        }
        return purlToIdentities;
    }

    static boolean containsDependency(final Map<String, Collection<String>> purlToIdentities, final Set<String> dependencies, final String purl) throws Exception {
        final Collection<String> identities = purlToIdentities.get(purl);
        int numIdentities = 0;
        if (identities != null) {
            numIdentities = identities.size();
            if (numIdentities == 1) {
                return dependencies.contains(identities.iterator().next());
            }
        }
        throw new Exception("Expected a single identity for purl " + purl + ", found " + numIdentities + " identities");
    }
}
