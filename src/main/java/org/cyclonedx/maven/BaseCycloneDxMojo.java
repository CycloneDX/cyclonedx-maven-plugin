/*
 * This file is part of CycloneDX Maven Plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.cyclonedx.maven;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.cyclonedx.maven.model.Attribute;
import org.cyclonedx.maven.model.Component;
import org.cyclonedx.maven.model.Hash;
import org.cyclonedx.maven.model.License;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class BaseCycloneDxMojo extends AbstractMojo {

    private static final String NS_BOM = "http://cyclonedx.org/schema/bom/1.0";

    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true, required = true)
    protected List<MavenProject> reactorProjects;


    /**
     * Various messages sent to console.
     */
    protected static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    protected static final String MESSAGE_SKIPPING_POM = "CycloneDX: Skipping pom package";
    protected static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    protected static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    protected static final String MESSAGE_WRITING_BOM = "CycloneDX: Writing BOM";
    protected static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM";
    protected static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard as defined by the XSD";


    /**
     * Returns a reference to the current project. This method is used instead
     * of auto-binding the project via component annotation in concrete
     * implementations of this. If the child has a
     * <code>@Component MavenProject project;</code> defined then the abstract
     * class (i.e. this class) will not have access to the current project (just
     * the way Maven works with the binding).
     *
     * @return returns a reference to the current project
     */
    protected MavenProject getProject() {
        return project;
    }

    /**
     * Returns the list of Maven Projects in this build.
     *
     * @return the list of Maven Projects in this build
     */
    protected List<MavenProject> getReactorProjects() {
        return reactorProjects;
    }

    protected Component convert(Artifact artifact) {
        Component component = new Component();
        component.setGroup(artifact.getGroupId());
        component.setName(artifact.getArtifactId());
        component.setVersion(artifact.getVersion());
        component.setType("library");
        component.setHashes(calculateHashes(artifact.getFile()));

        try {
            PackageURL purl = new PackageURL("maven", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), null, null);
            component.setPurl(purl.canonicalize());
        } catch (MalformedPackageURLException e) {
            // throw it away
        }

        MavenProject project = extractPom(artifact);
        if (project != null) {
            if (project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
            component.setDescription(project.getDescription());
            if (project.getLicenses() != null) {
                List<License> licenses = new ArrayList<>();
                for (org.apache.maven.model.License artifactLicense : project.getLicenses()) {
                    License license = new License();
                    // todo: possible resolution to SPDX license ID. Without resolution, we are forced to use only the license
                    // name since Maven doesn't enforce (or officially recommend) use of SPDX license IDs (stupid Maven)....
                    if (artifactLicense.getName() != null) {
                        license.setName(artifactLicense.getName());
                        licenses.add(license);
                    } else if (artifactLicense.getUrl() != null) {
                        license.setName(artifactLicense.getUrl());
                        licenses.add(license);
                    }
                }
                if (licenses.size() > 0) {
                    component.setLicenses(licenses);
                }
            }
        }
        return component;
    }

    private MavenProject extractPom(Artifact artifact) {
        if (artifact.getFile() != null) {
            try {
                JarFile jarFile = new JarFile(artifact.getFile());
                JarEntry entry = jarFile.getJarEntry("META-INF/maven/"+ artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml");
                if (entry != null) {
                    MavenXpp3Reader mavenreader = new MavenXpp3Reader();
                    try (InputStream input = jarFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(input)) {
                        Model model = mavenreader.read(reader);
                        return new MavenProject(model);
                    }
                }
            } catch (XmlPullParserException | IOException e) {
                // throw it away
            }
        }
        return null;
    }


    protected Document createBom(Set<Component> components) throws ParserConfigurationException {
        getLog().info(MESSAGE_CREATING_BOM);
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);

        // Create root <bom> node
        Element bomNode = createRootElement(doc, "bom", null,
                new Attribute("xmlns", NS_BOM),
                new Attribute("version", "1"));

        Element componentsNode = createElement(doc, bomNode, "components");

        if (components != null) {
            for (Component component : components) {
                Element componentNode = createElement(doc, componentsNode, "component", null, new Attribute("type", component.getType()));

                createElement(doc, componentNode, "publisher", stripBreaks(component.getPublisher()));
                createElement(doc, componentNode, "group", stripBreaks(component.getGroup()));
                createElement(doc, componentNode, "name", stripBreaks(component.getName()));
                createElement(doc, componentNode, "version", stripBreaks(component.getVersion()));
                createElement(doc, componentNode, "description", stripBreaks(component.getDescription()));

                if (component.getHashes() != null) {
                    // Create the hashes node
                    Element hashesNode = createElement(doc, componentNode, "hashes");
                    for (Hash hash : component.getHashes()) {
                        createElement(doc, hashesNode, "hash", hash.getValue(), new Attribute("alg", hash.getAlgorithm()));
                    }
                }

                if (component.getLicenses() != null) {
                    // Create the licenses node
                    Element licensesNode = doc.createElementNS(NS_BOM, "licenses");
                    componentNode.appendChild(licensesNode);
                    for (License license : component.getLicenses()) {
                        // Create individual license node
                        Element licenseNode = doc.createElementNS(NS_BOM, "license");
                        if (license.getId() != null) {
                            Element licenseIdNode = doc.createElementNS(NS_BOM, "id");
                            licenseIdNode.appendChild(doc.createTextNode(license.getId()));
                            licenseNode.appendChild(licenseIdNode);
                        } else if (license.getName() != null) {
                            Element licenseNameNode = doc.createElementNS(NS_BOM, "name");
                            licenseNameNode.appendChild(doc.createTextNode(license.getName()));
                            licenseNode.appendChild(licenseNameNode);
                        }
                        licensesNode.appendChild(licenseNode);
                    }
                }

                createElement(doc, componentNode, "cpe", stripBreaks(component.getCpe()));
                createElement(doc, componentNode, "purl", stripBreaks(component.getPurl()));
                createElement(doc, componentNode, "modified", String.valueOf(component.isModified()));
            }
        }
        return doc;
    }

    protected Element createElement(Document doc, Node parent, String name) {
        Element node = doc.createElementNS(NS_BOM, name);
        parent.appendChild(node);
        return node;
    }

    protected Element createElement(Document doc, Node parent, String name, Object value) {
        return createElement(doc, parent, name, value, new Attribute[0]);
    }

    protected Element createElement(Document doc, Node parent, String name, Object value, Attribute... attributes) {
        Element node = null;
        if (value != null || attributes.length > 0) {
            node = doc.createElementNS(NS_BOM, name);
            for (Attribute attribute: attributes) {
                node.setAttribute(attribute.getKey(), attribute.getValue());
            }
            if (value != null) {
                node.appendChild(doc.createTextNode(value.toString()));
            }
            parent.appendChild(node);
        }
        return node;
    }

    protected Element createRootElement(Document doc, String name, Object value, Attribute... attributes) {
        Element node = null;
        if (value != null || attributes.length > 0) {
            node = doc.createElementNS(NS_BOM, name);
            node.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            node.setAttribute("xsi:schemaLocation", NS_BOM + " " + NS_BOM);
            for (Attribute attribute: attributes) {
                node.setAttribute(attribute.getKey(), attribute.getValue());
            }
            if (value != null) {
                node.appendChild(doc.createTextNode(value.toString()));
            }
            doc.appendChild(node);
        }
        return node;
    }

    protected String toString(Document doc) {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(domSource, result);
        } catch (TransformerException e) {
            getLog().error(e.getMessage());
        }
        String bomString = writer.toString();
        getLog().debug(bomString);
        return bomString;
    }

    protected boolean validateBom(File file) {
        getLog().info(MESSAGE_VALIDATING_BOM);
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        // Use local copies of schemas rather than resolving from the net. It's faster, and less prone to errors.
        Source[] schemaFiles = {
                new StreamSource(getClass().getClassLoader().getResourceAsStream("spdx.xsd")),
                new StreamSource(getClass().getClassLoader().getResourceAsStream("bom-1.0.xsd"))
        };

        Source xmlFile = new StreamSource(file);
        try {
            Schema schema = schemaFactory.newSchema(schemaFiles);
            Validator validator = schema.newValidator();
            final List<SAXParseException> exceptions = new LinkedList<>();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }
                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }
            });
            validator.validate(xmlFile);
            if (exceptions.size() > 0) {
                getLog().error("One or more errors encountered while parsing bom");
                for (SAXParseException exception: exceptions) {
                    getLog().error(exception.getMessage());
                }
                return false;
            }
            return true;
        } catch (IOException | SAXException e) {
            getLog().error("An error occurred validating the BOM: " + e.getMessage());
        }
        return false;
    }

    protected void logParameters() {
        if (getLog().isInfoEnabled()) {
            getLog().info("CycloneDX: Parameters");
            getLog().info("------------------------------------------------------------------------");
            /*
            getLog().info("artifact      : " + artifact);
            getLog().info("bomVersion    : " + bomVersion);
            getLog().info("componentType : " + componentType);
            getLog().info("publisher     : " + publisher);
            getLog().info("group         : " + group);
            getLog().info("name          : " + name);
            getLog().info("version       : " + version);
            getLog().info("description   : " + description);
            getLog().info("licenses      : " + license);
            getLog().info("cpe           : " + cpe);
            getLog().info("purl          : " + purl);
            getLog().info("modified      : " + modified);
            */
            getLog().info("------------------------------------------------------------------------");
        }
    }

    private List<Hash> calculateHashes(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return null;
        }
        getLog().debug(MESSAGE_CALCULATING_HASHES);
        getLog().debug(file.getAbsolutePath());
        List<Hash> hashes = new ArrayList<>();

        try {
            FileInputStream fis = new FileInputStream(file);
            hashes.add(new Hash("MD5", DigestUtils.md5Hex(fis)));
            fis.close();

            fis = new FileInputStream(file);
            hashes.add(new Hash("SHA-1", DigestUtils.sha1Hex(fis)));
            fis.close();

            fis = new FileInputStream(file);
            hashes.add(new Hash("SHA-256", DigestUtils.sha256Hex(fis)));
            fis.close();

            fis = new FileInputStream(file);
            hashes.add(new Hash("SHA-512", DigestUtils.sha512Hex(fis)));
            fis.close();
        } catch (IOException e) {
            getLog().error(e.getMessage());
        }
        return hashes;
    }

    private String stripBreaks(String in) {
        if (in == null) {
            return null;
        }
        return in.trim().replace("\r\n", " ").replace("\n", " ").replace("\t", " ").replace("\r", " ").replaceAll(" +", " ");
    }

}
