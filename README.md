[![Build Status](https://github.com/CycloneDX/cyclonedx-maven-plugin/workflows/Maven%20CI/badge.svg)](https://github.com/CycloneDX/cyclonedx-maven-plugin/actions?workflow=Maven+CI)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.cyclonedx/cyclonedx-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.cyclonedx/cyclonedx-maven-plugin)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][License]
[![Website](https://img.shields.io/badge/https://-cyclonedx.org-blue.svg)](https://cyclonedx.org/)
[![Group Discussion](https://img.shields.io/badge/discussion-groups.io-blue.svg)](https://groups.io/g/CycloneDX)
[![Twitter](https://img.shields.io/twitter/url/http/shields.io.svg?style=social&label=Follow)](https://twitter.com/CycloneDX_Spec)


CycloneDX Maven Plugin
=========

The CycloneDX Maven plugin creates an aggregate of all dependencies and transitive dependencies of a project 
and creates a valid CycloneDX bill-of-material document from the results. CycloneDX is a lightweight BOM 
specification that is easily created, human readable, and simple to parse. 

Maven Usage
-------------------

```xml
<!-- uses default configuration -->
<plugins>
    <plugin>
        <groupId>org.cyclonedx</groupId>
        <artifactId>cyclonedx-maven-plugin</artifactId>
        <version>1.5.1</version>
    </plugin>
</plugins>
```


Default Values
-------------------
```xml
<plugins>
    <plugin>
        <groupId>org.cyclonedx</groupId>
        <artifactId>cyclonedx-maven-plugin</artifactId>
        <version>1.5.1</version>
        <executions>
            <execution>
                <phase>verify</phase>
                <goals>
                    <goal>makeAggregateBom</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <schemaVersion>1.1</schemaVersion>
            <includeBomSerialNumber>true</includeBomSerialNumber>
            <includeCompileScope>true</includeCompileScope>
            <includeProvidedScope>true</includeProvidedScope>
            <includeRuntimeScope>true</includeRuntimeScope>
            <includeSystemScope>true</includeSystemScope>
            <includeTestScope>false</includeTestScope>
            <includeDependencyGraph>true</includeDependencyGraph>
        </configuration>
    </plugin>
</plugins>
```

Notes
-------------------
As of v1.4.0, the default CycloneDX BOM format is v1.1 with included serial number. 

Goals
-------------------
The CycloneDX Maven plugin contains the following two goals:
* makeBom
* makeAggregateBom

By default the BOM will be attached as additional artifact and thereby installed resp.
uploaded/deployed as `${project.artifactId}-${project.version}-cyclonedx.xml`. This
may be switched of by setting `cyclonedx.skipAttach` to true.

makeBom and makeAggregateBom can optionally be skipped completely by setting `cyclonedx.skip` to true.

Copyright & License
-------------------

CycloneDX Maven Plugin is Copyright (c) Steve Springett. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for the full license.

[License]: https://github.com/CycloneDX/cyclonedx-maven-plugin/blob/master/LICENSE
