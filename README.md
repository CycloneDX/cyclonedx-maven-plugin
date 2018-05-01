[![Build Status](https://travis-ci.org/CycloneDX/cyclonedx-maven-plugin.svg?branch=master)](https://travis-ci.org/CycloneDX/cyclonedx-maven-plugin)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][Apache 2.0]


CycloneDX Maven Plugin
=========

The CycloneDX Maven plugin creates an aggregate of all dependencies and transitive dependencies of a project 
and creates a valid CycloneDX bill-of-material document from the results. CycloneDX is a lightweight BoM 
specification that is easily created, human readable, and simple to parse. The resulting bom.xml can be used
with tools such as [OWASP Dependency-Track](https://dependencytrack.org/) for the continuous analysis of components.

Maven Usage
-------------------

```xml
<!-- uses default configuration -->
<plugins>
    <plugin>
        <groupId>org.cyclonedx</groupId>
        <artifactId>cyclonedx-maven-plugin</artifactId>
        <version>1.0.0</version>
    </plugin>
</plugins>
```


```xml
<!-- uses custom configuration -->
<plugins>
    <plugin>
        <groupId>org.cyclonedx</groupId>
        <artifactId>cyclonedx-maven-plugin</artifactId>
        <version>1.0.0</version>
        <configuration>
            <includeCompileScope>true</includeCompileScope>
            <includeProvidedScope>true</includeProvidedScope>
            <includeRuntimeScope>true</includeRuntimeScope>
            <includeSystemScope>true</includeSystemScope>
            <includeTestScope>true</includeTestScope>
        </configuration>
    </plugin>
</plugins>
```

Copyright & License
-------------------

CycloneDX Maven Plugin is Copyright (c) Steve Springett. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] [Apache 2.0] file for the full license.

[Apache 2.0]: https://github.com/stevespringett/nist-data-mirror/blob/master/LICENSE
