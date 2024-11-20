# Manufacturer
Manufacturer is common in BOMs created through automated processes.

When creating a number of BOMs for several projects within one organization
or company, it is convenient to attach this information at one place.

This will also conform to upcoming EU regulation that all SBOM files shall

>At minimum, the product with digital elements shall be accompanied by:
>1. the name, registered trade name or registered trademark of the manufacturer, and the
    > postal address, the email address or other digital contact as well as, where
    > available, the website at which the manufacturer can be contacted;
>2. the single point of contact where information about vulnerabilities of the product
    > with digital elements can be reported and received, and where the manufacturer’s
    > policy on coordinated vulnerability disclosure can be found
>3. name and type and any additional information enabling the unique identification
    > of the product with digital elements
>4. the intended purpose of the product with digital elements, including the security
    > environment provided by the manufacturer, as well as the product’s essential
    > functionalities and information about the security properties

## Configuration
The configuration is optional. If none is specified, the manufacturer information is not visible.
See https://cyclonedx.org/docs/latest/json/#metadata_manufacturer for more information.

### name
The name of the organization,

### address
The physical address (location) of the organization.
##### country
The country name or the two-letter ISO 3166-1 country code.
##### region
The region or state in the country.
##### locality
The locality or city within the country.
##### postOfficeBoxNumber
The post office box number.
##### postalCode
The postal code.
##### streetAddress
The street address.

### url
The URL of the organization. Multiple URLs are allowed.

# contact
A contact at the organization. Multiple contacts are allowed.

##### name
The name of a contact
##### email
The email address of the contact.
##### phone
The phone number of the contact.

## Example of configuration

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.cyclonedx</groupId>
                <artifactId>cyclonedx-maven-plugin</artifactId>
                <version>${cyclonedx-maven-plugin.version}</version>
                <configuration>
                    <manufacturer>
                        <name>Example Company</name>
                        <url>https://www.example.com/contact</url>
                        <contact>
                            <contact>
                                <name>Steve Springett</name>
                                <email>Steve.Springett@owasp.org</email>
                            </contact>
                            <contact>
                                <name>Another contact</name>
                                <phone>1-800-555-1111</phone>
                            </contact>
                        </contact>
                    </manufacturer>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

This configuration will add the following to the BOM file (JSON format):
```json
    "manufacturer" : {
      "name" : "Example Company",
      "url" : [
        "https://www.example.com/contact"
      ],
      "contact" : [
        {
          "name" : "Steve Springett",
          "email" : "Steve.Springett@owasp.org"
        },
        {
          "name" : "Another contact",
          "phone" : "1-800-555-1111"
        }
      ]
    }
```

## Links
- [EU regulation proposal about SBOM generation.](https://www.europarl.europa.eu/doceo/document/TA-9-2024-0130_EN.pdf)

