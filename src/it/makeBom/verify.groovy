File bomFileXml = new File(basedir, "target/bom.xml")
File bomFileJson = new File(basedir, "target/bom.json")

assert bomFileXml.exists()
assert bomFileJson.exists()

assert bomFileXml.text.contains('<reference type="website">\n' +
        '          <url>https://github.com/CycloneDX/cyclonedx-maven-plugin</url>\n' +
        '        </reference>')

assert !bomFileXml.text.contains('<property name="maven.optional.unused">')

// check default schema version
assert bomFileJson.text.contains('"specVersion" : "1.7"')

// Reproducible Builds
assert !bomFileJson.text.contains('"timestamp"')
assert bomFileJson.text.contains('"name" : "cdx:reproducible",')
assert bomFileJson.text.contains('"value" : "enabled"')

// dependency type=zip: check that artifact is described with license info (issue #431)
assert bomFileJson.text.contains('"group" : "com.ibm.websphere.appserver.features"')
assert bomFileJson.text.contains('"name" : "IBM International License Agreement for Non-Warranted Programs",')

File bomAggregateFileXml = new File(basedir, "target/bom-makeAggregateBom.xml")
File bomAggregateFileJson = new File(basedir, "target/bom-makeAggregateBom.json")

assert bomAggregateFileXml.exists()
assert bomAggregateFileJson.exists()

String buildLog = new File(basedir, "build.log").text
assert ! buildLog.contains('[INFO] CycloneDX: Parameters')

assert buildLog.contains('[INFO]            attaching as makeBom-1.0-SNAPSHOT.cdx.xml')
assert buildLog.contains('[INFO]            attaching as makeBom-1.0-SNAPSHOT.cdx.json')

// regression for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/564
assert ! buildLog.contains('Unknown keyword')
