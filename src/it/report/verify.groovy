File report(String module) {
    return new File(basedir, module + 'target/site/cyclonedx-sbom.html')
}

// lib: SBOM written by makeBom at default location, in both formats
File libReport = report('lib/')
assert libReport.isFile()
String lib = libReport.text
assert lib.contains('pkg:maven/org.apache.commons/commons-text@1.12.0?type=jar')
assert lib.contains('pkg:maven/org.apache.commons/commons-lang3@') // transitive dependency
assert lib.contains('href="https://spdx.org/licenses/Apache-2.0.html"')
assert lib.contains('SHA-256: <code>')
assert lib.contains('<a href="bom.json">bom.json</a>')
assert lib.contains('<a href="bom.xml">bom.xml</a>')
assert new File(basedir, 'lib/target/site/bom.json').isFile()
assert new File(basedir, 'lib/target/site/bom.xml').isFile()
// the report is listed with the other project reports
assert new File(basedir, 'lib/target/site/project-reports.html').text.contains('cyclonedx-sbom.html')

// app: SBOM written at the location configured by spring-boot-starter-parent, found without configuration
File appReport = report('app/')
assert appReport.isFile()
String app = appReport.text
assert app.contains('pkg:maven/org.cyclonedx.its/report-lib@1.0-SNAPSHOT?type=jar') // reactor dependency
assert app.contains('<a href="application.cdx.json">application.cdx.json</a>')
assert new File(basedir, 'app/target/site/application.cdx.json').isFile()
assert !new File(basedir, 'app/target/site/application.cdx.xml').exists()

// root project: no SBOM, so the report is skipped
assert !report('').exists()
assert new File(basedir, 'build.log').text.contains('Skipping CycloneDX report: no SBOM file found')
