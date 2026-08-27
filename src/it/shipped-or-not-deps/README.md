SBOM Representation of Shipped vs Not-Shipped Dependencies
====

This IT is a WIP that for now show concrete issue before we can work on finding solutions then implement them.

## What is the Problem

- the [SBOM for `lib`](target/its/shipped-or-not-deps/not-shipped/lib/target/) has 4 dependencies
- the [SBOM for `war`](target/its/shipped-or-not-deps/shipped/war/target/) has 5 (because it depends on `lib`)

nothing in the 2 SBOMs shows that:
- `war` **embeds** the dependencies (in `WEB-INF/lib/*.jar`), which in addition force a precise dependencies version if someone uses the `.war` file
- but `lib` does not embed any dependency, just has soft references that will have to be resolved by a consumer, eventually changing the resolved version vs what was defined by the lib

shade is like war, but just in more complex forms because there are many options

Spring Boot is like war

key differences between `shade`, `war` and Spring Boot is that the dependencies are not embedded exactly in the same location in the output artifact

## Why does it Matter?

When a library ships an SBOM that declares many dependencies, and a dependency has a vulnerability,
users tend to report and ask "for a fixed library": upgrading the dependency by the consumer is natural,
no absolute need for the provider to ship a new release of his library (unless compatibility with newer
versions of the dependency is not easy).

When a war or equivalent ships a dependency, consumer of the war cannot easily upgrade: it would require patching the
war. Here, reporting the dependency vulnerability makes sense.

If nothing is clear in the SBOM, end users cannot know what they should do: report or not?

## Ideas

- version-less component for library dependencies, to show that version from lib is just a hint, but consumer may change when doing conflict resolution,
- CycloneDX 1.7 [`versionRange` and `isExternal`](https://cyclonedx.org/docs/1.7/json/#metadata_tools_oneOf_i0_components_items_versionRange), 
- when a dependency is shipped, describe precisely where it is shipped, perhaps as `evidence` field,
- based on previous shipped case, expand to a way to describe "not shipped"
- ...
