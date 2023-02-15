File bomFileXml = new File(basedir, "target/bom.xml")
File bomFileJson = new File(basedir, "target/bom.json")

assert bomFileXml.exists()
assert bomFileJson.exists()

assert bomFileXml.text.contains('<reference type="website"><url>https://github.com/CycloneDX/cyclonedx-maven-plugin</url></reference>')

File bomAggregateFileXml = new File(basedir, "target/bom-makeAggregateBom.xml")
File bomAggregateFileJson = new File(basedir, "target/bom-makeAggregateBom.json")

assert bomAggregateFileXml.exists()
assert bomAggregateFileJson.exists()

assert ! new File(basedir, "build.log").text.contains('[INFO] CycloneDX: Parameters')
