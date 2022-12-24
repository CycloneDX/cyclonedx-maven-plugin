File bomFileXml = new File(basedir, "target/bom.xml")
File bomFileJson = new File(basedir, "target/bom.json")

assert bomFileXml.exists()
assert bomFileJson.exists()

assert bomFileXml.text.contains('<reference type="website"><url>https://github.com/CycloneDX/cyclonedx-maven-plugin</url></reference>')
