Example Projects
================

## Libraries

Typical default case is a library, with `jar` [packaging](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html#Packaging) (default packaging when not specified in `pom.xml`),
`target/*.jar` output file built by `maven-jar-plugin`:
- Maven dependencies of the library are pure symbolic references defined in `pom.xml` and version is just **preferred**
  (unless defined as `[x.y.z]` in `pom.xml`),
- consumer of the library will get its dependencies as **transitive** dependencies of the library added and ,
- version of transitives are calculated by the built tool, based on consuming context: many aspects influence effective
  version, including `dependencyManagement` configuration and eventual conflicts between different transitives

See Maven's [Introduction to Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
for more details.

Also don't be confused:
- a Maven `<dependency>` is a [CycloneDX `component`](https://cyclonedx.org/docs/1.6/json/#components):
  a [CycloneDX `dependency`](https://cyclonedx.org/docs/1.6/json/#dependencies) is a Maven's dependency tree node,  
- Maven's [dependency scope](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)
  (`compile`, `provided`, `runtime`, `test`) is not a [CycloneDX scope](https://cyclonedx.org/docs/1.6/json/#components_items_scope)
  (`required`, `optional`, `excluded`),
- Maven's [BOM POM](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#bill-of-materials-bom-poms)
(used to import dependencyManagement `<dependencyManagement><scope>import</scope>`) is not a (CycloneDX) SBOM,

## Java EE

Java EE provide packaging archives formats where dependency components are copied:

- `war` web archives, created by [`maven-war-plugin`](https://maven.apache.org/plugins/maven-war-plugin/) often through
  `war` packaging, with `*.jar` dependencies embedded in `WEB-INF/lib`,
- `ejb` (with client), `rar`, `par`, `acr`...
- `ear` enterprise archives, created by [`maven-ear-plugin`](https://maven.apache.org/plugins/maven-ear-plugin/), with
  flexible [EAR modules](https://maven.apache.org/plugins/maven-ear-plugin/modules.html) defining how libs are packaged.

### Frontend

https://github.com/eirslett/frontend-maven-plugin

## Maven Assembly

Flexible archive creation done by [`maven-assembly-plugin`](https://maven.apache.org/plugins/maven-assembly-plugin/),
with optional [`dependencySet` configuration](https://maven.apache.org/plugins/maven-assembly-plugin/assembly.html#class_dependencySet).

### Provisio

[Provisio](https://github.com/jvanzyl/provisio) is another plugin trying to provide equivalent features.

## Maven Shade

[Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/) provides the capability to package the
artifact in an uber-jar, including its dependencies and to shade - i.e. rename - the packages of some of the dependencies.

It provides a very flexible way to [select content](https://maven.apache.org/plugins/maven-shade-plugin/examples/includes-excludes.html)
from dependencies.

## Executable Jars

- [Spring Boot `repackage`](https://docs.spring.io/spring-boot/maven-plugin/packaging.html)
- [Spring Boot OCI Images](https://docs.spring.io/spring-boot/maven-plugin/build-image.html)
- [Uni-Jar](https://github.com/nsoft/uno-jar)
- [App Assembler](https://www.mojohaus.org/appassembler/appassembler-maven-plugin/index.html)
