# Release Notes

## 2.2.0

### Breaking Changes

* [http://www.ease-crc.org/ont/SOMA.owl/NonmanifestedSituation](http://www.ease-crc.org/ont/SOMA.owl/NonmanifestedSituation) was renamed to [http://www.ease-crc.org/ont/SOMA.owl#NonmanifestedSituation](http://www.ease-crc.org/ont/SOMA.owl#NonmanifestedSituation)
  
  <details>
      <summary>Details</summary>
  The original IRI used the incorrect separator <code>/</code> instead of <code>#</code> between the namespace and the fragment.
  </details>

### New Features

* We newly included the [USD.owl](https://ease-crc.org/ont/USD.owl) ontology.
  
  <details>
      <summary>Details</summary>
      The ontology maps the USD format to a knoweldge graph representation. <a href="https://ar5iv.labs.arxiv.org/html/2310.16737">Here, you can find the the original publication on <code>USD.owl</code></a>. Note that the version in this repository, as all the ontologies in here do, uses our self-hosted version of <a href="http://www.ease-crc.org/ont/DUL.owl">DUL.owl</a>.
  </details>

* We added new Concepts and Properties for Motion Processes and Motion Plans

  <details>
      <summary>Details</summary>
      We introduce two new classes and relating properties to handle motion processes and motion plans within the ontology.
  </details>

### Bugfixes

* Fixed Issue #305 (the documentation not being build).
  
  <details>
      <summary>Details</summary>
      <ul>
          <li>
              Our switch from RDF/XML format to functional syntax broke the workflow responsible for building the IRI, because it uses owlready2, which does not support functional syntax. The workflow was fixed so that it uses the published version instead, which is still in XML/RDF.
          </li>
          <li>
              In the mean time, we added some comments that contained special characters, that had to be espaced for the generation of the latex document, e.g., <code>_</code>. This was fixed as well.
          </li>
      </ul>
  </details>

* We fixed two annotations, that were incorrectly marked as labels instead of comments.
  
  <details>
      <summary>Details</summary>
  The two incorrect annotations were found in <a href="http://www.ease-crc.org/ont/SOMA-AGENT.owl">SOMA-AGENT</a>:
      <ul>
          <li>
              SOMA:PreferenceOrder
          </li>
          <li>
              SOMA:Predilection
          </li>
      </ul>
  </details>

## 2.1.0

### Breaking changes

* Remove SOMA namespace inconsistencies
  
  <details>
      <summary>Details</summary>
      Some entities used namespaces other than DUL or SOMA, e.g. SOMA-OBJ or SOMA-ACT, which caused problems for some systems that always expect the SOMA namespace. All SOMA entities are now in the SOMA namespace instead of a sub-namespace.
  </details>

### New Features

* Introduced concepts for introspection
  
  <details>
      <summary>Details</summary>
      We added various concepts and roles in different SOMA-Files to support, e.g., tracing the metacognitive experiences of CRAM in NEEMs.
      <ul>
          <li>
              Mental task taxonomy
          </li>
          <li>
              Causality relationships
          </li>
          <li>
              Model of Software
          </li>
          <li>
              Small taxonomy of Communication tasks
          </li>
          <li>
              Model of Capabilities
          </li>
      </ul>
  </details>

* Introduced concepts for kitchen appliances and furniture in `SOMA-HOME`

* Introduced `Affordances`, `Depositions`, `Bearer` and `Trigger` in `SOMA-HOME`

* Moved different kitchen related action from `knowrob` to `SOMA-ACT`

* Introduced a more general version of `causes`: `affects` (and its inverse `isAffectedBy`)

## 2.0.0

### Breaking changes

* Removal of `IOLite`
  
  <details>
      <summary>Details</summary>
      <ul>
          <li>
              Reason: incompatible with <code>DUL-v32</code>
          </li>
          <li>
              Note: Some important concepts, e.g. <code>IOLite#LinguisticObject</code>, have been renamed to <code>SOMA#LinguisticObject</code>. If you are missing any crucial concepts/roles, let us know
          </li>
          <li>
              Ongoing effort to create a model of CRAM (see next point) will replace some of the IOLite taxonomy at some point
          </li>
      </ul>
  </details>

* Complete and still ongoing remodeling of `SOMA-IO`
  
  <details>
      <summary>Details</summary>
      <ul>
          <li>
              Reason: Was in a primitive state; we need a better model of IO stuff to model CRAM
          </li>
          <li>
              Note: Removal/resorting of various concepts and roles. To the best of our knowledge, these should not have been in use anyway - please let us know if you are missing anything
          </li>
          <li>
              Contains classes and properties to model general software architecture (including algorithms, formal languages, interfaces and compatibility, types of software & running software instances)
          </li>
      </ul>
  </details>

* New ontology in the import structure: `SOMA NEEM`
  
  <details>
      <summary>Details</summary>
      Some SOMA entities, e.g., <code>KynoDinamicData</code>, have been decided to be too specific for the basic SOMA ontology. These have been moved to <code>SOMA NEEM</code>, which is of course part of the collapsed SOMA version
  </details>

### New Features

* Introduced causal relations between `Action`s (see the property `causes`)

* Introduced `Action`s modeling communication between `Agent`s

* Introduced concepts for kitchen, ovens and trash containers in `SOMA HOME`

* Introduced metadata for episodes, e.g., to represent a temporal context or a table setting

* Added missing labels to various concepts and roles

* To all SOMA ontology files, we added comments that explain their respective domain

* Protégé-setup for offline development
  
  <details>
      <summary>Details</summary>
      When opening SOMA locally from the cloned repository, Protégé will follow the IRI redirects in the catalog file from the same folder. There, we redirected the imports from the online version to the local version. This allows offline editing, while not changing anything when opening the ontologies via the IRI elsewhere
  </details>

* Switch to functional syntax (internally)
  
  <details>
      <summary>Details</summary>
      We switched to the owl functional syntax format for the files <em>in git only</em> to support better readability when reviewing changes. The ontologies will still be published in the rdf xml format (as in all previous versions).  
  </details>

* We now host and use our own, modified copy of `DUL`
  
  <details>
      <summary>Details</summary>
      <ul>
          <li>
              Reason: <code>DUL</code> is unreliable (e.g., down for a whole week and no one can open <code>SOMA</code>)
          </li>
          <li>
              As a side effect, we can make changes to <a href="http://www.ease-crc.org/ont/DUL.owl">DUL.owl</a>, if necessary (yes, we will be very careful)
              <ul>
                  <li>
                      Removed italian lables
                  </li>
                  <li>
                      Added missing annotations of <code>rdfs:isDefinedIn</code>  (only for <a href="http://www.ease-crc.org/ont/DUL.owl">DUL</a> concepts / roles)
                  </li>
                  <li>
                      Removed unnecessary annotations of author and date
                  </li>
                  <li>
                      Added missing english labels
                  </li>
              </ul>
          </li>
      </ul>
  </details>

## 1.2.0

* Base release for starting the changelog! Yay, now you get information why stuff might break!
  
  <details>
      <summary>Details</summary>
      The SOMA ontologies can now be accessed via a version IRI, e.g., <a href="http://www.ease-crc.org/ont/1.2.0/SOMA-STATE.owl">http://www.ease-crc.org/ont/1.2.0/SOMA-STATE.owl</a>. These are guaranteed to stay the way they are. The basic ontology IRI, e.g., <a href="http://www.ease-crc.org/ont/SOMA-STATE.owl">http://www.ease-crc.org/ont/SOMA-STATE.owl</a>, now refers to the latest version that is available (not neccessary associated to a stable release), and is referred to "Upcoming" at the top!
  </details>
