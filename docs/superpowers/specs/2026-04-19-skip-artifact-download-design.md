# Skip Artifact Download Mode

**Status:** Approved for implementation planning
**Date:** 2026-04-19
**Target plugin version:** 2.10.0

## Summary

Add a `skipArtifactDownload` flag to the CycloneDX Maven plugin that lets users generate SBOMs without the `.jar` files of their dependencies being present on disk. POMs are still resolved (they are required to walk the dependency graph and to enrich components with license and URL metadata); only the per-artifact hash computation — which is the sole reason jars are ever read — is suppressed.

The flag is a behavior flag. It does not install Aether interceptors or otherwise try to prevent Maven from downloading jars that something else in the build lifecycle asked for. Users who want to realize "no jar downloads" end-to-end invoke the goal directly (`mvn org.cyclonedx:cyclonedx-maven-plugin:makeBom -Dcyclonedx.skipArtifactDownload=true`) or bind the plugin to a phase before `compile`.

## Motivation

In CI/CD pipelines that only produce an SBOM — supply-chain scans, dependency inventories, license audits — the current plugin forces a full jar download of every transitive dependency. On a project with a large dependency graph this can be hundreds of MB of network and disk, all for the sole purpose of computing SHA hashes that the downstream SBOM consumer often does not require. A fast, jar-free mode avoids that cost.

## Scope

### In scope
- A new Maven plugin parameter `skipArtifactDownload` (property `cyclonedx.skipArtifactDownload`, default `false`) on `BaseCycloneDxMojo`, inherited by `makeBom`, `makeAggregateBom`, and `makePackageBom`.
- Suppressing `BomUtils.calculateHashes(artifact.getFile(), …)` on dependency components when the flag is set.
- Forcing `detectUnusedForOptionalScope` to `false` (with a WARN-level log) when the flag is set, since the analyzer reads compiled classes from the jars.
- Tests that generate a BOM with the flag set and assert hashes are absent on dependencies but present on the plugin self-component.
- README documentation of the flag and the recommended invocation pattern.

### Out of scope
- Fetching `.sha1`/`.sha256`/`.sha512` sidecar files remotely to populate hashes without the jar. Deferred to a future `fetchRemoteHashes` feature.
- Skipping effective-POM resolution. Licenses and URLs stay in the SBOM.
- Blocking jar resolution at the Aether/`RepositorySystem` layer. The flag is cooperative, not enforcing.
- Any new goal. The flag rides on the existing goals.
- Changing the default lifecycle phase binding.

## Decisions

Captured from brainstorming:

| # | Question | Decision |
|---|---|---|
| Q1 | What does "no disk" mean? | Avoid jar downloads; POM downloads acceptable. |
| Q2 | Hashes in SBOM? | Omit. No remote hash fetch. |
| Q3 | API shape? | New config flag on existing goals. No new Mojo. |
| Q4 | Effective-POM enrichment? | Keep. Licenses / URLs / publisher / description still resolved. |
| Q5.1 | If user also sets `detectUnusedForOptionalScope=true`? | Force-disable it with a warning. |
| Q5.2 | Plugin self-hash in `metadata.tools`? | Keep. The plugin jar is always on disk (Maven had to load it). |

## Design

### Configuration surface

One new parameter on `BaseCycloneDxMojo`:

```java
/**
 * Skip reading artifact files from disk. When {@code true}, component hashes
 * are omitted for dependency components. POM resolution for licenses/URLs and
 * the plugin self-hash in {@code metadata.tools} are unaffected.
 *
 * <p>When this flag is {@code true}, {@code detectUnusedForOptionalScope} is
 * force-disabled (with a warning) because the analyzer requires compiled
 * classes from dependency jars.
 *
 * @since 2.10.0
 */
@Parameter(property = "cyclonedx.skipArtifactDownload", defaultValue = "false", required = false)
private boolean skipArtifactDownload;

protected boolean isSkipArtifactDownload() {
    return skipArtifactDownload;
}
```

### Code changes

1. **`org.cyclonedx.maven.ModelConverter` (interface).**
   Add a new overload:
   ```java
   Component convertMavenDependency(Artifact artifact, Version schemaVersion,
                                    boolean includeLicenseText,
                                    boolean skipArtifactDownload);
   ```
   Retain the existing 3-arg method for binary compatibility. Its default implementation delegates to the 4-arg variant with `skipArtifactDownload = false`.

2. **`org.cyclonedx.maven.DefaultModelConverter`.**
   Implement the 4-arg method. Inside, the existing block
   ```java
   try {
       logger.debug(BaseCycloneDxMojo.MESSAGE_CALCULATING_HASHES);
       component.setHashes(BomUtils.calculateHashes(artifact.getFile(), schemaVersion));
   } catch (IOException e) { ... }
   ```
   is wrapped in `if (!skipArtifactDownload) { ... }`. No other change — the effective-POM build path, PURL generation, license/metadata enrichment, and external-reference handling remain identical.
   The 3-arg method is kept and delegates: `return convertMavenDependency(artifact, schemaVersion, includeLicenseText, false);`.

3. **`org.cyclonedx.maven.BaseCycloneDxMojo`.**
   - Add the `@Parameter` and `isSkipArtifactDownload()` above.
   - Update `convertMavenDependency(Artifact)` to pass `skipArtifactDownload` through to the 4-arg `ModelConverter` method.
   - In `execute()`, immediately after the `shouldSkip()` block and before `logParameters()`, reconcile conflicting flags:
     ```java
     if (skipArtifactDownload && detectUnusedForOptionalScope) {
         getLog().warn("cyclonedx.skipArtifactDownload is true; disabling "
                 + "detectUnusedForOptionalScope (analyzer requires compiled classes).");
         detectUnusedForOptionalScope = false;
     }
     ```
   - Extend `logParameters()` to include the new flag.

4. **`org.cyclonedx.maven.CycloneDxMojo`.**
   No code change required — `detectUnusedForOptionalScope` is already consulted inside `doProjectDependencyAnalysis()` and the upstream reconciliation handles the conflict.

5. **`DefaultProjectDependenciesConverter`, `DelegatingRepositorySystem`.**
   No change. Graph walking already only requires POMs.

### Behavior matrix

| Output field | Default | `skipArtifactDownload=true` |
|---|---|---|
| `component.hashes` (dependencies) | SHA-1/256/512 over jar | **omitted** |
| `component.licenses` | from effective POM | unchanged |
| `component.externalReferences` | from effective POM | unchanged |
| `component.publisher` / `description` | from effective POM | unchanged |
| `component.purl` / `group` / `name` / `version` / `type` | unchanged | unchanged |
| `metadata.tools.component.hashes` (plugin self) | SHA-1/256/512 | unchanged (plugin jar always on disk) |
| `dependencies` graph | unchanged | unchanged |
| `detectUnusedForOptionalScope` honored | yes | **no — force-disabled with warning** |

### Error handling

- **Both flags on simultaneously:** warn and override `detectUnusedForOptionalScope` to `false`. Do not throw. Matches the plugin's existing friendly-defaults posture (e.g., invalid `projectType` warns and falls back to `library`).
- **`artifact.getFile()` unexpectedly null with flag off:** pre-existing behavior, unchanged. `BomUtils.calculateHashes` logs an error; the component gets no hashes. The new flag turns that accidental path into the documented path.
- **Parameter validation:** no additional validation beyond the above reconciliation. The flag is boolean.

## Testing

Follow the existing Takari-based JUnit pattern under `src/test/java/org/cyclonedx/maven/`.

1. **New fixture project:** `src/test/resources/skip-artifact-download/` — single-module Maven project with a handful of real dependencies (modeled after the simpler shape of `src/test/resources/issue-117/` rather than the sprawling `bom-dependencies` tree). Uses `${current.version}` for the plugin version.

2. **New test class:** `SkipArtifactDownloadTest` (JUnit 4, `@RunWith(MavenJUnitTestRunner.class)`, `@MavenVersions({"3.6.3"})`, extends `BaseMavenVerifier`).

   - `testHashesOmittedWhenFlagSet`: builds the fixture with
     `-Dcyclonedx.skipArtifactDownload=true`, parses `target/bom.xml`, asserts:
     - Every dependency `<component>` has no `<hashes>` child.
     - `<metadata><tools>` (or the 1.5+ `<metadata><tools><components>`) still contains the plugin self-component with hashes.
     - At least one dependency has a `<purl>`, a `<licenses>`, and the expected `<group>`/`<name>`/`<version>`.
     - `<dependencies>` graph is non-empty and wires the main component to its direct deps.

   - `testDetectUnusedForOptionalScopeOverriddenWhenFlagSet`: builds with both
     `-Dcyclonedx.skipArtifactDownload=true` and
     `-DdetectUnusedForOptionalScope=true`. Asserts the Maven log contains the
     warning string and the BOM is still valid/well-formed.

   - `testHashesPresentWhenFlagUnset`: baseline — same fixture, no flag, asserts at least one dependency has `<hashes>` populated. Guards against accidental regressions in the default path.

3. **Aggregate coverage:** extend an existing aggregate test (e.g., the one under `src/test/resources/bundle/` or `issue-284/`) with one additional invocation path that sets the flag and asserts hashes are absent across reactor modules. Keep it small.

4. **Invoker IT:** not added in this iteration. The unit suite exercises the same code path; adding an invoker IT would be duplication for the flag's current scope.

## Documentation

1. **`README.md`** — insert a new section between "Excluding Projects" and "Goals":

   > ### Skipping jar downloads
   >
   > Set `-Dcyclonedx.skipArtifactDownload=true` (or `<skipArtifactDownload>true</skipArtifactDownload>` in `<configuration>`) to skip reading dependency jar files. Component hashes are omitted for dependencies; all other metadata (licenses, URLs, purl, dependency graph) is preserved. Typical use:
   >
   > ```bash
   > mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -Dcyclonedx.skipArtifactDownload=true
   > ```
   >
   > Invoke the goal directly (as above) or bind the plugin to a phase earlier than `compile` if you want Maven's own lifecycle not to pull jars either. Incompatible with `detectUnusedForOptionalScope=true`, which will be force-disabled.

2. **Javadoc on the `@Parameter`** serves as the `site` reference (maven-plugin-report-plugin regenerates the goal docs from Javadoc).

3. No new `src/site/markdown/` page unless a reviewer specifically asks for one.

## Release notes / compatibility

- **Target version:** `2.10.0` (new feature → minor bump). Update `pom.xml` `<version>` from `2.9.2-SNAPSHOT` to `2.10.0-SNAPSHOT` as part of implementation.
- **Java / Maven compatibility:** unchanged. Still JDK 8, Maven 3.1+.
- **`ModelConverter` SPI:** the new 4-arg method is additive. The old 3-arg method stays functional for any third-party consumer that extends the interface (none known in-tree).
- **Default behavior with flag unset:** bit-for-bit identical to 2.9.x output.

## Open questions / future work

- `fetchRemoteHashes=true` variant that pulls `.sha512` / `.sha256` / `.sha1` sidecar files from remote repos without the jar. Separate feature.
- A stricter `skipPomResolution=true` flag that also skips effective-POM building (loses license metadata). Separate feature.
- Phase-binding guidance could become a dedicated `src/site/markdown/fast-sbom.md` page if users ask for it.
