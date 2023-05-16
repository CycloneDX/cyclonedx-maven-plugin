void assertBomFiles(String path, boolean aggregate) {
    File bomFileXml = new File(basedir, path + ".xml")
    File bomFileJson = new File(basedir, path + ".json")

    assert bomFileXml.exists()
    assert bomFileJson.exists()

    String analysis = aggregate ? "makeAggregateBom" : "makeBom"
    assert bomFileXml.text.contains('<property name="maven.goal">' + analysis + '</property>')
    assert bomFileXml.text.contains('<property name="maven.scopes">compile,provided,runtime,system</property>')
    assert !bomFileXml.text.contains('<property name="maven.optional.unused">')
    assert bomFileJson.text.contains('"name" : "maven.goal",')
    assert bomFileJson.text.contains('"value" : "' + analysis + '"')
    assert bomFileJson.text.contains('"name" : "maven.scopes",')
    assert bomFileJson.text.contains('"value" : "compile,provided,runtime,system"')
}

assertBomFiles("target/bom", true) // aggregate
assertBomFiles("api/target/bom", false)
assertBomFiles("util/target/bom", false)
assertBomFiles("impls/target/bom", false)
assertBomFiles("impls/impl-A/target/bom", false)
assertBomFiles("impls/impl-B/target/bom", false)

var buildLog = new File(basedir, "build.log").text

assert 11 == (buildLog =~ /\[INFO\] CycloneDX: Resolving Dependencies/).size()
assert 2 == (buildLog =~ /\[INFO\] CycloneDX: Resolving Aggregated Dependencies/).size()

// 13 = 6 modules for main cyclonedx-makeAggregateBom execution
//    + 1 for root module cyclonedx-makeAggregateBom-root-only execution
//    + 6 modules for additional cyclonedx-makeBom execution
assert 13 == (buildLog =~ /\[INFO\] CycloneDX: Writing and validating BOM \(XML\)/).size()
assert 13 == (buildLog =~ /\[INFO\] CycloneDX: Writing and validating BOM \(JSON\)/).size()
// cyclonedx-makeAggregateBom-root-only execution skips 5 non-root modules
assert 5 == (buildLog =~ /\[INFO\] Skipping CycloneDX on non-execution root/).size()

// [WARNING] artifact org.cyclonedx.its:api:xml:cyclonedx:1.0-SNAPSHOT already attached, replace previous instance
assert 0 == (buildLog =~ /-SNAPSHOT already attached, replace previous instance/).size()

String cleanBom(String path) {
    File bomFile = new File(basedir, path)
    return bomFile.text.replaceFirst(/urn:uuid:........-....-....-....-............/, "urn:uuid:").replaceFirst(/\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ/, "");
}

void assertBomEqualsNonAggregate(String path) {
    String bomXml = cleanBom(path + "-makeBom.xml")
    String aggregateBomXml = cleanBom(path + ".xml")
    assert bomXml == aggregateBomXml

    String bomJson = cleanBom(path + "-makeBom.json")
    String aggregateBomJson = cleanBom(path + ".json")
    assert bomJson == aggregateBomJson
}

assertBomEqualsNonAggregate("api/target/bom")
assertBomEqualsNonAggregate("util/target/bom")
assertBomEqualsNonAggregate("impls/target/bom")
assertBomEqualsNonAggregate("impls/impl-A/target/bom")
assertBomEqualsNonAggregate("impls/impl-B/target/bom")

// dependencies for root component in makeAggregateBom is the list of modules
String bom = new File(basedir, 'target/bom.xml').text
String rootDependencies = bom.substring(bom.indexOf('<dependency ref="pkg:maven/org.cyclonedx.its/makeAggregateBom@1.0-SNAPSHOT?type=pom">'), bom.indexOf('</dependency>') + 13)
assert rootDependencies.contains('<dependency ref="pkg:maven/org.cyclonedx.its/api@1.0-SNAPSHOT?type=jar"/>')
assert rootDependencies.contains('<dependency ref="pkg:maven/org.cyclonedx.its/impls@1.0-SNAPSHOT?type=pom"/>')
assert rootDependencies.contains('<dependency ref="pkg:maven/org.cyclonedx.its/util@1.0-SNAPSHOT?type=jar"/>')
assert 4 == (rootDependencies =~ /<dependency ref="pkg:maven/).size()
