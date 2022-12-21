File bomFileXml = new File(basedir, "target/bom.xml")
File bomFileJson = new File(basedir, "target/bom.json")

assert bomFileXml.exists()
assert bomFileJson.exists()
