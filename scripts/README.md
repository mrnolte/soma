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


**Java ontology CLI (A2 tooling foundation)**

Install JDK 25 and set `JAVA_HOME`. From the repository root, build and run
read-only compatibility tests with the checksum-pinned Maven 3.9.16 wrapper:

```sh
bash ./mvnw -B -ntp verify -f scripts/java/pom.xml
# Windows PowerShell:
.\mvnw.cmd -B -ntp verify -f scripts/java/pom.xml
```

Tests cover the existing YAML configuration, local catalog loading of all source
ontologies, and an OWLAPI roundtrip with HermiT consistency/unsatisfiability checks.
`verify` currently builds the CLI; export lifecycle integration belongs to A3.

The executable jar accepts `--help`, `--config=PATH` (default `application.yaml`),
and the required `--versionInfo=VERSION` (or JVM property `soma.version`).
Configuration paths remain relative to the working directory, as before. Run the
legacy processing pipeline from `scripts/java` only on a disposable input copy:
**it still overwrites source ontologies until A3 replaces the saver**.

Spring Boot was removed: the CLI explicitly constructs the processing steps and
reads YAML with SnakeYAML's safe constructor. The processing order is unchanged.
See [the dependency review](java/DEPENDENCIES.md) for versions and scan scope.
