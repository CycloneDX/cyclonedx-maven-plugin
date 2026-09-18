import groovy.json.JsonSlurper

File aggregateBom = new File(basedir, "target/bom.json")
assert aggregateBom.exists() : "aggregate BOM was not generated"

def bom = new JsonSlurper().parse(aggregateBom)
def war = bom.components.find { component ->
    component.purl == "pkg:maven/org.cyclonedx.its/war@1.0-SNAPSHOT?type=war"
}

assert war != null : "war reactor component is missing from aggregate BOM"
assert war.type == "application" : "war reactor component was not transformed with its own lifecycle plan"
