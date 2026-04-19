Using the Plugin Locally on a Maven Project
============================================

This guide walks through running the CycloneDX Maven plugin on a Maven project on
your workstation to generate a CycloneDX Software Bill of Materials (SBOM).

## Prerequisites

- A JDK 8 or later runtime on `PATH` / `JAVA_HOME`.
- Maven 3.1 or later (the project declares `<prerequisites><maven>3.1</maven></prerequisites>`).
- A Maven project you want to analyze. It can be a single module or a multi-module reactor.
- Network access to Maven Central (or a configured mirror) so dependency POMs can be resolved.

## Quick start — one-shot invocation

The fastest path: run a goal directly, with no plugin configuration in your `pom.xml`.
The plugin is already on Maven Central.

```bash
# single-module project — BOM for this module only
mvn org.cyclonedx:cyclonedx-maven-plugin:makeBom

# multi-module reactor — aggregate BOM at the root, plus per-module BOMs
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
```

Outputs land in `target/bom.xml` and `target/bom.json` next to the relevant `pom.xml`.
For aggregate builds, the aggregate BOMs live at the reactor root and per-module BOMs
live in each module's own `target/`.

You can pin the plugin version if you need reproducibility:

```bash
mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
```

## Binding to the build lifecycle

To generate the SBOM every time the project is built, add the plugin to your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.cyclonedx</groupId>
      <artifactId>cyclonedx-maven-plugin</artifactId>
      <version>2.9.1</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals>
            <goal>makeAggregateBom</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Now `mvn package` (or any phase at or after `package`) produces the BOM. The BOM is also
attached as a secondary artifact with classifier `cyclonedx` during `install`/`deploy`, so
it is published alongside your main artifact.

## Choosing the right goal

| Goal | What it produces | When to use |
| ---- | ---------------- | ----------- |
| `makeBom` | One BOM per module | Single-module projects, or if you want only per-module BOMs in a reactor. |
| `makeAggregateBom` | One aggregate BOM at the reactor root + one BOM per module | Multi-module projects where consumers want a single SBOM describing the whole build. |
| `makePackageBom` | One BOM per module, but only for modules with `war`/`ear` packaging | You only care about deployable artifacts in a reactor that also contains libraries. |

## Output formats and file names

Default output is `bom.xml` **and** `bom.json`. To restrict the format, set `outputFormat`:

```bash
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DoutputFormat=json
```

Valid values: `all` (default), `xml`, `json`.

Change the file stem with `-DoutputName=sbom` (you'd get `sbom.xml` / `sbom.json`) or
redirect the output directory with `-DoutputDirectory=/tmp/sboms`.

## Scope filtering

By default the plugin includes `compile`, `provided`, `runtime`, and `system` dependencies
and excludes `test`. Override any of these:

```bash
# include test dependencies as well
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DincludeTestScope=true

# production-only SBOM (drop provided and runtime)
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom \
  -DincludeProvidedScope=false -DincludeRuntimeScope=false
```

## Excluding reactor modules

With `makeAggregateBom` you can filter modules out of the aggregate:

```bash
# drop modules whose artifactId contains the word "test"
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DexcludeTestProject

# drop modules by exact artifactId
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DexcludeArtifactId=internal-tools,samples

# drop modules by groupId
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DexcludeGroupId=com.example.internal
```

## Fast SBOM mode — no jar downloads

Set `cyclonedx.skipArtifactDownload=true` to generate an SBOM without reading dependency
`.jar` files from disk. Hashes are omitted for dependency components; everything else
(`purl`, licenses, URLs, publisher, description, dependency graph) is preserved because
dependency POMs are still resolved.

```bash
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom \
  -Dcyclonedx.skipArtifactDownload=true
```

This is the fastest way to generate an SBOM in CI when you don't need cryptographic hashes.
Note: invoking the goal directly (as above) skips the `compile` phase so Maven's own
lifecycle doesn't pull jars either. If the plugin is bound to `package`, `compile` will
still have run before it and Maven will have downloaded compile-scope jars as a side
effect — the flag only changes what *this plugin* reads from disk.

Incompatible with `-DdetectUnusedForOptionalScope=true` (analyzer requires compiled
classes); when both are set, `detectUnusedForOptionalScope` is force-disabled with a
warning. Available since plugin version `2.9.2`.

## Skipping the SBOM generation for individual modules

- `-Dcyclonedx.skip=true` skips the plugin entirely.
- `-Dcyclonedx.skipAttach=true` produces the BOM but does not attach it as a
  Maven artifact (so it won't be installed/deployed).
- For `makeBom` specifically, modules whose `<deploy>` is skipped are skipped automatically.
  Override with `-Dcyclonedx.skipNotDeployed=false` if you need BOMs for non-deployed modules.

## Including license text

By default licenses are listed by name/URL only. Include the full text:

```bash
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DincludeLicenseText=true
```

## Reproducible SBOMs

The plugin honors `project.build.outputTimestamp`. Set it in your `pom.xml` (as a fixed
ISO 8601 string or seconds-since-epoch) and the BOM will be emitted with a stable
`metadata.timestamp` and a `cdx:reproducible=enabled` property. Combined with Maven's
standard reproducible-build settings this yields byte-identical BOMs across machines.

## Verifying the generated BOM

After a run you should see, in the Maven log:

```
CycloneDX: Writing and validating BOM (XML): .../target/bom.xml
CycloneDX: Writing and validating BOM (JSON): .../target/bom.json
```

The "validating" line means the plugin re-parsed the output against the CycloneDX XSD/JSON
schema. A validation failure is fatal and fails the build.

You can also validate manually with the [cyclonedx-cli](https://github.com/CycloneDX/cyclonedx-cli):

```bash
cyclonedx validate --input-file target/bom.xml
```

## Using a locally-built version of the plugin

If you need a SNAPSHOT version (for testing a patch, or running from a fork):

```bash
# in the cyclonedx-maven-plugin checkout
mvn -DskipTests install

# in your target project
mvn org.cyclonedx:cyclonedx-maven-plugin:2.10.0-SNAPSHOT:makeAggregateBom
```

`mvn install` stages the plugin jar in your local Maven repository (typically
`~/.m2/repository/`). Any later build in the same user account that resolves
`org.cyclonedx:cyclonedx-maven-plugin` will find that SNAPSHOT.

## Troubleshooting

- **"The BOM does not conform to the CycloneDX BOM standard as defined by the XSD"**
  The plugin generated a BOM that failed its own post-generation validation. File an
  issue with the contents of the generated `bom.xml` — this indicates a bug in the plugin.
- **"Invalid schemaVersion configured 'X.Y', using 1.6"**
  You configured a `<schemaVersion>` the plugin's current version does not recognize.
  Check the table in the README for plugin version → CycloneDX schema compatibility.
- **Missing licenses/URLs for some dependencies**
  The plugin extracts these from each dependency's effective POM. If a dependency has a
  sparse POM (no `<licenses>`, no `<url>`) the SBOM will reflect that. This is not a
  plugin bug.
- **Build downloads all dependency jars even with `skipArtifactDownload=true`**
  The flag controls only what the plugin reads. Maven's own lifecycle may still be pulling
  jars for `compile` / `test`. Invoke the goal directly (`mvn org.cyclonedx:...:makeBom`)
  or bind the execution to a phase before `compile` (e.g. `validate`).
- **`Unable to create Maven project for <artifact> from repository.`**
  POM resolution for a transitive dependency failed. The dependency will still appear in
  the BOM (from the graph) but without license/URL metadata. Check your repository mirror
  configuration and network access.

## Next steps

- [External References](external-references.html) — the full mapping from Maven POM
  fields to CycloneDX external reference types.
- [Goal reference](plugin-info.html) — generated from the plugin's Javadoc, lists every
  configuration parameter for every goal.
