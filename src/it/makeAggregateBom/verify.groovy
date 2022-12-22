void assertBomFiles(String path) {
    File bomFileXml = new File(basedir, path + ".xml")
    File bomFileJson = new File(basedir, path + ".json")

    assert bomFileXml.exists()
    assert bomFileJson.exists()
}

assertBomFiles("target/bom") // aggregate
assertBomFiles("api/target/bom")
assertBomFiles("util/target/bom")
assertBomFiles("impls/target/bom")
assertBomFiles("impls/impl-A/target/bom")
assertBomFiles("impls/impl-B/target/bom")
