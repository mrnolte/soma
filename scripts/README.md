This folder includes several utility scripts for working with the SOMA ontology.

**Controlled vocabulary generation**

Run ecv.sh to get an ELAN controlled vocabulary, constructed from concepts in the ontology. The script already configures which ontologies, and which parts of the taxonomy, are relevant for the generation. The output file will be called EASE.ecv, and will appear in the same folder as the script.

Dependencies:

* bash
* python (2.7 or higher)
* (python package) untangle
* (python package) uuid
* (python package) itertools
* (python package) hashlib

**Testing consistency**

Run the hermit.py script to verify an OWL file with the HermiT reasoner. Verification here means checking whether some concepts are subsumed by owl:Nothing, ie. they are unsatisfiable.

Syntax to run the script is:

./hermit_test.py [options] inputfile

where inputfile is a path to an OWL file. The following options are available:
* -r, --reasoner: path to HermiT.jar, the executable for the HermiT reasoner. If not provided, it is assumed the jar is in the same folder as where the script is called from
* -o, --output: path to a file where to store the results of the HermiT reasoner. If not provided, results are not stored

Classification and subsumption reasoning will be performed on the input owl file. In case of non-satisfiable concepts, messages will be produced on stdout (ie. the console) by the script. A successful run of the script, that is, if no errors happen and no non-satisfiable concepts are found, produces no console output.


**Java ontology CLI (A3 processing)**

Install JDK 25 and set `JAVA_HOME`. From the repository root:

```sh
bash ./mvnw -B -ntp verify -f scripts/java/pom.xml
java -jar scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar --versionInfo=current
# Windows: use .\mvnw.cmd for Maven.
```

The CLI also accepts `--config=PATH` (default `scripts/java/application.yaml`)
and the JVM property `soma.version`. Paths inside YAML are resolved relative to
that file. `ontology.output-directory` is required and must be separate from the
input directory. Collapse `out-path` values are filenames inside that output.
The default exports all 16 source modules to `build/ci/dist/owl`, replacing
SOMA.owl and SOMA-HOME.owl with the configured aggregations, plus a complete
local XML import catalog. Inputs under `owl/` are read only.

Processing order is explicit in `CIRunner`: load sources, copy into an independent
manager, optionally repair entity namespaces, check namespaces, optionally rewrite
GCIs, add isDefinedBy and version annotations, aggregate, then export. Only the
exporter writes files. The four transformations are individually configurable in
YAML. Version is required only when version annotations are enabled. Defaults
preserve the previous enabled steps; IRI repair stays off.

Catalog imports must resolve to existing local files; missing or duplicate
mappings fail even when OWLAPI has already cached the imported ontology. Explicit
catalog aliases remain supported: Upstream HOME imports SOMA-ALL.owl while the
actual ontology IRI is SOMA-All.owl. Working imports are canonicalized through
the catalog, without changing source files. A separate generated catalog ensures
HOME loads the exported SOMA aggregation rather than the small source SOMA module.
No network fallback, catalog delegation or rewrite directives are supported.

Intentional changes: aggregation uses the entire transitive import closure and
excludes the entire closure of retained imports. Root ontology annotations are
preserved. GCI rewrites retain annotations and avoid singleton intersections.
Manchester export is rejected because it can lose GCIs; use functional, rdf_xml
or owl_xml. Configuration mistakes and ambiguous ontology references fail early.

Tests cover invalid configuration, missing/ambiguous imports, unchanged source
bytes on success and failure, exact synthetic axiom differences, complete SOMA
aggregation and reload of all exports using only their local catalog. HermiT is
currently exercised on a synthetic ontology; full semantic checks belong to B.
See [the dependency review](java/DEPENDENCIES.md) for the A2 toolchain and scan.
