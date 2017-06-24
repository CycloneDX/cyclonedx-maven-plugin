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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
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
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


@Mojo(
        name = "writeBom",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE
)
public class CycloneDxMojo extends AbstractMojo {
    
    private static final String NS_BOM = "http://cyclonedx.org/schema/bom";

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "artifact", defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", required = true)
    private String artifact;

    @Parameter(property = "bomVersion", defaultValue = "1", required = true)
    private Integer bomVersion;

    @Parameter(property = "componentType", defaultValue = "application", required = true)
    private String componentType;

    @Parameter(property = "publisher", defaultValue = "${project.organization.name}")
    private String publisher;

    @Parameter(property = "group", defaultValue = "${project.groupId}")
    private String group;

    @Parameter(property = "name", defaultValue = "${project.artifactId}", required = true)
    private String name;

    @Parameter(property = "version", defaultValue = "${project.version}", required = true)
    private String version;

    @Parameter(property = "description", defaultValue = "${project.description}", required = true)
    private String description;

    @Parameter(property = "licenses", required = true)
    private String[] licenses;

    @Parameter(property = "cpe")
    private String cpe;

    @Parameter(property = "modified", defaultValue = "false", required = true)
    private Boolean modified;


    public void execute() throws MojoExecutionException {
        logParameters();
        List<Hash> hashes = calculateHashes();
        try {
            Document doc = createBom(hashes);
            String bomString = toString(doc);
            boolean isValid = validateBom(doc);
            if (!isValid) {
                throw new MojoExecutionException("The BOM is not valid");
            }

            File bomFile = new File(project.getBasedir(), "target/bom.xml");
            FileUtils.write(bomFile, bomString, Charset.forName("UTF-8"), false);

        } catch (ParserConfigurationException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName(), e);
        }
    }

    private Document createBom(List<Hash> hashes) throws ParserConfigurationException {
        getLog().info("CycloneDX: Building BOM");
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        // Create root <bom> node
        Element bomNode = createElement(doc, doc, "bom", null,
                new Attribute("xmlns", NS_BOM),
                new Attribute("version", bomVersion.toString()));
        Element componentsNode = createElement(doc, bomNode, "components");
        Element componentNode = createElement(doc, componentsNode, "component", null, new Attribute("type", componentType));

        createElement(doc, componentNode, "publisher", publisher);
        createElement(doc, componentNode, "group", group);
        createElement(doc, componentNode, "name", name);
        createElement(doc, componentNode, "version", version);
        createElement(doc, componentNode, "description", description);

        // Create the hashes node
        Element hashesNode = createElement(doc, componentNode, "hashes");
        for (Hash hash: hashes) {
            createElement(doc, hashesNode, "hash", hash.getValue(), new Attribute("alg", hash.getAlgorithm()));
        }

        // Create the licenses node
        Element licensesNode = doc.createElementNS(NS_BOM, "licenses");
        componentNode.appendChild(licensesNode);
        for (String license: licenses) {
            // Create individual license node
            Element licenseNode = doc.createElementNS(NS_BOM, "license");
            Element licenseIdNode = doc.createElementNS(NS_BOM, "id");
            licenseIdNode.appendChild(doc.createTextNode(license));
            licenseNode.appendChild(licenseIdNode);
            licensesNode.appendChild(licenseNode);
        }

        createElement(doc, componentNode, "cpe", cpe);
        createElement(doc, componentNode, "modified", modified.toString());

        return doc;
    }

    private Element createElement(Document doc, Node parent, String name) {
        Element node = doc.createElementNS(NS_BOM, name);
        parent.appendChild(node);
        return node;
    }

    private Element createElement(Document doc, Node parent, String name, Object value) {
        return createElement(doc, parent, name, value, new Attribute[0]);
    }

    private Element createElement(Document doc, Node parent, String name, Object value, Attribute... attributes) {
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

    private String toString(Document doc) {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(domSource, result);
        } catch (TransformerException e) {
            getLog().error(e.getMessage());
        }
        String bomString = writer.toString();
        getLog().info(bomString);
        return bomString;
    }

    private boolean validateBom(Document doc) {
        getLog().info("CycloneDX: Validating BOM");
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream resourceStream = this.getClass().getResourceAsStream("/bom.xsd");
            Schema schema = factory.newSchema(new StreamSource(resourceStream));
            Validator validator = schema.newValidator();
            validator.validate(new DOMSource(doc));
        } catch (IOException | SAXException e) {
            getLog().error("An error occurred validating BOM: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void logParameters() {
        if (getLog().isDebugEnabled()) {
            getLog().debug("CycloneDX: Parameters");
            getLog().debug("------------------------------------------------------------------------");
            getLog().debug("artifact      : " + artifact);
            getLog().debug("bomVersion    : " + bomVersion);
            getLog().debug("componentType : " + componentType);
            getLog().debug("publisher     : " + publisher);
            getLog().debug("group         : " + group);
            getLog().debug("name          : " + name);
            getLog().debug("version       : " + version);
            getLog().debug("description   : " + description);
            getLog().debug("licenses      : " + licenses);
            getLog().debug("cpe           : " + cpe);
            getLog().debug("modified      : " + modified);
            getLog().debug("------------------------------------------------------------------------");
        }
    }

    private List<Hash> calculateHashes() {
        getLog().info("CycloneDX: Calculating Hashes");
        List<Hash> hashes = new ArrayList<>();
        try {
            FileInputStream fis = new FileInputStream(new File(artifact));
            hashes.add(new Hash("MD5", DigestUtils.md5Hex(fis)));
            fis.close();

            fis = new FileInputStream(new File(artifact));
            hashes.add(new Hash("SHA-1", DigestUtils.sha1Hex(fis)));
            fis.close();

            fis = new FileInputStream(new File(artifact));
            hashes.add(new Hash("SHA-256", DigestUtils.sha256Hex(fis)));
            fis.close();

            fis = new FileInputStream(new File(artifact));
            hashes.add(new Hash("SHA-384", DigestUtils.sha384Hex(fis)));
            fis.close();

            fis = new FileInputStream(new File(artifact));
            hashes.add(new Hash("SHA-512", DigestUtils.sha512Hex(fis)));
            fis.close();
        } catch (IOException e) {
            getLog().error(e.getMessage());
        }
        return hashes;
    }

    public static void main(String[] args) throws Exception {
        CycloneDxMojo mojo = new CycloneDxMojo();
        mojo.bomVersion = 1;
        mojo.componentType = "application";
        mojo.publisher = "ACME International";
        mojo.group = "org.acme";
        mojo.name = "awesome-util";
        mojo.version = "1.0.0";
        mojo.description = "The most awesome utility";
        mojo.cpe = "cpe:/a:example:xmlutil:1.0.0";
        mojo.modified = false;

        mojo.licenses = new String[] { "Apache-2.0" };

        mojo.execute();
    }

}
