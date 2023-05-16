package org.cyclonedx.maven;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
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
    static Element getElement(final Element parent, final String elementName) throws Exception {
        Element element = null;
        Node child = parent.getFirstChild();
        while (child != null) {
            if (Node.ELEMENT_NODE == child.getNodeType()) {
                if (child.getNodeName().equals(elementName)) {
                    if (element != null) {
                        throw new Exception("Second instance of element " + elementName + " discovered in " + parent.getNodeName());
                    }
                    element = (Element)child;
                }
            }
            child = child.getNextSibling();
        }
        return element;
    }

    static Element getDependencyNode(final Node dependencies, final String ref) {
        return getChildElement(dependencies, ref, "dependency", "ref");
    }

    static Element getComponentNode(final Node components, final String ref) {
        return getChildElement(components, ref, "component", "bom-ref");
    }

    private static Element getChildElement(final Node parent, final String ref, final String elementName, final String attrName) {
        final NodeList children = parent.getChildNodes();
        final int numChildNodes = children.getLength();
        for (int index = 0 ; index < numChildNodes ; index++) {
            final Node child = children.item(index);
            if ((child.getNodeType() == Node.ELEMENT_NODE) && elementName.equals(child.getNodeName())) {
                final Node refNode = child.getAttributes().getNamedItem(attrName);
                if (ref.equals(refNode.getNodeValue())) {
                    return (Element)child;
                }
            }
        }
        return null;
    }

    static Set<String> getComponentReferences(final Node parent) {
        return getReferences(null, parent, "component", "bom-ref");
    }

    static Set<String> getDependencyReferences(final Node parent) {
        return getReferences(null, parent, "dependency", "ref");
    }

    private static Set<String> getReferences(Set<String> references, final Node rootNode, final String elementName, final String attrName) {
        if (references == null) {
            references = new HashSet<>();
        }
        final NodeList children = rootNode.getChildNodes();
        final int numChildNodes = children.getLength();
        for (int index = 0 ; index < numChildNodes ; index++) {
            final Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (elementName.equals(child.getNodeName())) {
                    final Node refNode = child.getAttributes().getNamedItem(attrName);
                    if (refNode != null) {
                        references.add(refNode.getNodeValue());
                    }
                }
                getReferences(references, child, elementName, attrName);
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
}
