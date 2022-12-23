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

var buildLog = new File(basedir, "build.log").text

// 12 = 6 modules * 2 BOMs per module (JSON + XML) for main cyclonedx-makeAggregateBom execution
// + 2 = root module * 2 BOMs for cyclonedx-makeAggregateBom-root-only execution
// + 12 = 6 modules * 2 BOMs per module (JSON + XML) for additional cyclonedx-makeBom execution
assert 26 == (buildLog =~ /\[INFO\] CycloneDX: Writing BOM/).size()
assert 26 == (buildLog =~ /\[INFO\] CycloneDX: Validating BOM/).size()
assert 5 == (buildLog =~ /\[INFO\] Skipping CycloneDX on non-execution root/).size()

// [WARNING] artifact org.cyclonedx.its:api:xml:cyclonedx:1.0-SNAPSHOT already attached, replace previous instance
assert 0 == (buildLog =~ /-SNAPSHOT already attached, replace previous instance/).size()
