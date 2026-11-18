# DUL 4.2 migration

SOMA uses DUL 4.2 with its original entity namespace. The existing self-hosted
ontology IRI, `http://www.ease-crc.org/ont/DUL.owl`, is retained so published SOMA
imports resolve to the packaged compatibility copy. The local catalog resolves
imports without a network connection; the original ODP ontology IRI is also a
catalog alias for this file.

## Upstream provenance and compatibility patch

Source: http://www.ontologydesignpatterns.org/ont/dul/DUL.owl, retrieved
2026-11-18; server Last-Modified: 2025-09-18 08:11:01 GMT.
The downloaded Turtle file has SHA-256:

```text
e5f6429596d2c7014730bad85aaaa9c39530235f27f596cd0e7ce85e6bbcba66
```

`owl/DUL.owl` is saved in Functional Syntax with OWLAPI 5.5.1. It retains all
upstream annotations, including Italian labels, author/date metadata, the 4.2
version annotation. Only the ontology IRI retains SOMA's self-hosted identity.
One logical axiom is rewritten:

```text
Place SubClassOf isLocationOf min 1 owl:Thing
  becomes
Place SubClassOf isLocationOf some owl:Thing
```

These expressions have identical semantics. The existential syntax is required
for OWL 2 DL in SOMA because the transitive `containsObject` subproperty makes
`isLocationOf` non-simple. An importing ontology cannot remove the original
cardinality axiom, so this compatibility patch must remain in the dependency
until upstream changes it. No DUL property chains are removed.

## SOMA annotations are separate

`SOMA-DUL-ANNOTATIONS.owl` imports the self-hosted DUL copy and contains no logical axioms.
SOMA's direct DUL imports now use this module, including USD's import, so labels
are available when loading the modules independently.

The module carries sixteen useful annotations from our old DUL copy: eight
additional English labels, the English `associatedWith` label, six
`isDefinedBy` annotations and `isPropertPartOf`'s `seeAlso` link. It also supplies
a label and a comment for the new `hasInScope` property.

The old capitalized label variants on `Classification` and `Parthood` are
superseded by upstream's labels. The old URL used as an `isPropertPartOf` comment
is superseded by upstream's explanatory comment. Original annotations are not
overridden or removed by the supplement. The upstream `isPropertPartOf` IRI is
retained to avoid breaking compatibility.

The export annotation passes skip DUL itself, so they do not put SOMA build
versions or generated annotations back into the dependency. The collapse
configuration also uses the `SOMA-ALL` ontology IRI introduced by the IRI fixes.

## Specific affordance relations

`hasAffordance` relates a `Disposition` to the `Affordance` description that
specifies it. Its inverse is `isAffordanceOf`. They specialize DUL's
`isDescribedBy` and `describes`, respectively, and remain simple properties.

The disposition/capability cardinalities, the affordance-specific range
restriction, twelve affordance inference chains, and HOME's sitting affordance
links now use these specific properties. DUL's broad description chains can
still describe bearers and triggers without treating those objects as
dispositions. SOMA's bearer/trigger/event-type inferences remain available.

Software uses the existing `isDesignFor` / `isDesignedBy` pair for its specific
design restrictions, including the software configuration cardinality.

### Existing data

Applications should assert `hasAffordance` for intended disposition-to-affordance
links, or its inverse for the opposite direction. Existing intended links using
generic DUL descriptions must be migrated explicitly. Do not promote every
inferred `describes` / `isDescribedBy` link: those relations deliberately have
broader meanings in DUL 4.2. Generic links remain inferred from the new specific
links, preserving the broader relation for queries with subproperty inference.
Existing asserted generic links may be retained alongside the new specific links.

Exactly one now counts the designated affordance relation. Additional generic
descriptions, including additional generic affordance descriptions, are allowed.
This is an intentional distinction, not a claim of equivalence to counting all
generic DUL links.

## Regression checks

The fixtures in `scripts/tests/fixtures/dul42` cover:

| Fixture | Expected result |
| --- | --- |
| `physical-bearer.owl` | Consistent; retains DUL's `describes` inference and SOMA's `affordsBearer` inference; the physical bearer is not a disposition. |
| `two-specific-affordances.owl` | Inconsistent: two explicitly different fillers violate the exact-one specific affordance constraint. |
| `additional-generic-description.owl` | Consistent: a second generic description does not count as a second specific affordance. |

The standalone check requires OWLAPI 5.x and HermiT 1.4.5.519, available in the
shaded JAR produced by the CI modernization series. From the repository root:

```powershell
java -Xmx2048m -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar scripts/tests/Dul42CompatibilityCheck.java . fixtures build/dul42-fixtures.json
java -Xmx2048m -cp scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar scripts/tests/Dul42CompatibilityCheck.java . SOMA-HOME.owl build/dul42-reasoning.json
```

The check tests OWL 2 DL over the merged import closure, avoiding a modular
profile-check blind spot. It checks consistency, every named class, and object
and data property non-emptiness for each requested root. The separate Allen
module retains its known OWL 2 DL property-characteristic violations and is not
made DL-compliant by this change.

### Existing findings outside this migration

The broader audit also checked the independent ELAN and USD roots. These retain
pre-existing issues, and the checks report failures rather than ignoring them:

- ELAN has five unsatisfiable meal-preparation classes: `PrepareForBreakfast`,
  `PrepareForDinner`, `PrepareForLunch`, `PrepareForMeal`, and `SettingTheTable`.
  `PrepareForMeal` requires `formalityLevel some xsd:boolean`, while that
  property's range is `xsd:string`. The same five failures are present on the
  parent branch. This migration does not alter that data model.
- USD contains custom datatypes without definitions, including `u_token`.
  HermiT rejects these undefined datatypes already on the parent branch; the
  first reported datatype can vary.
  USD's merged OWL 2 DL profile check passes; full HermiT reasoning is not
  certified for this root.

These independent checks can be reproduced with the same Java command, selecting
`SOMA-ELAN.owl` or `USD.owl` as the requested root. The separate Allen module's
known property-characteristic profile violations also remain unchanged.
