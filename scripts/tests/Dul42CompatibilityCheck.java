// Standalone semantic regression checks. Requires OWLAPI 5.x and HermiT 1.4.5.519.
// Run with the shaded ontology-tooling JAR from the CI modernization series.
import java.nio.file.*;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.formats.*;

class Dul42CompatibilityCheck {
 static Path catalog;
 static OWLOntology load(Path file) throws Exception {
  var manager=OWLManager.createOWLOntologyManager();
  var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
  factory.setNamespaceAware(true);
  factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  var doc=factory.newDocumentBuilder().parse(catalog.toFile());
  var entries=doc.getElementsByTagNameNS("urn:oasis:names:tc:entity:xmlns:xml:catalog","uri");
  Map<IRI,IRI> mappings=new HashMap<>();
  for(int n=0;n<entries.getLength();n++) {
   var e=(org.w3c.dom.Element)entries.item(n);
   mappings.put(IRI.create(e.getAttribute("name")),IRI.create(catalog.getParent().resolve(e.getAttribute("uri")).toFile()));
  }
  manager.getIRIMappers().add(i->{if(!mappings.containsKey(i))throw new IllegalArgumentException("Unmapped import: "+i);return mappings.get(i);});
  return manager.loadOntologyFromOntologyDocument(file.toFile());
 }

 static String S="http://www.ease-crc.org/ont/SOMA.owl#",D="http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#";
 static OWLOntology flat(OWLOntology source)throws Exception {
  var m=OWLManager.createOWLOntologyManager();var result=m.createOntology();
  for(var member:source.getImportsClosure())result.addAxioms(member.getAxioms());return result;
 }
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("Usage: Dul42CompatibilityCheck REPOSITORY fixtures|ROOT.owl[,ROOT.owl] REPORT.json");
  Path root=Path.of(args[0]),output=Path.of(args[2]);catalog=root.resolve("owl/catalog-v001.xml");
  if(output.toAbsolutePath().getParent()!=null)Files.createDirectories(output.toAbsolutePath().getParent());
  var results=new LinkedHashMap<String,Object>();
  if(args[1].equals("fixtures")){
   for(String name:List.of("physical-bearer","two-specific-affordances","additional-generic-description")){
    var ontology=flat(load(root.resolve("scripts/tests/fixtures/dul42/"+name+".owl")));
    var profile=new org.semanticweb.owlapi.profiles.OWL2DLProfile().checkOntology(ontology);
    require(profile.isInProfile(),"Fixture profile: "+profile.getViolations());
    var config=new org.semanticweb.HermiT.Configuration();config.individualTaskTimeout=30000;
    var reasoner=new org.semanticweb.HermiT.ReasonerFactory().createReasoner(ontology,config);
    try{
     boolean consistent=reasoner.isConsistent();
     require(consistent!=name.equals("two-specific-affordances"),"Unexpected consistency: "+name);
     var result=new LinkedHashMap<String,Object>();result.put("consistent",consistent);
     if(consistent){
      var df=ontology.getOWLOntologyManager().getOWLDataFactory();String test="urn:soma:tests:dul42:";
      var disposition=df.getOWLNamedIndividual(IRI.create(test+"disposition"));var affordance=df.getOWLNamedIndividual(IRI.create(test+"affordance"));
      var role=df.getOWLNamedIndividual(IRI.create(test+"bearerRole"));var bearer=df.getOWLNamedIndividual(IRI.create(test+"bearer"));
      require(reasoner.isEntailed(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(D+"describes")),affordance,bearer)),"DUL describes chain lost");
      require(reasoner.isEntailed(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(S+"affordsBearer")),disposition,role)),"SOMA bearer inference lost");
      require(reasoner.isEntailed(df.getOWLClassAssertionAxiom(df.getOWLObjectComplementOf(df.getOWLClass(IRI.create(S+"Disposition"))),bearer)),"Bearer incorrectly typed as disposition");
      result.put("dulChainPreserved",true);result.put("somaChainPreserved",true);result.put("physicalBearerNotDisposition",true);
      if(name.equals("physical-bearer")){
       // A migrated legacy link must still imply the original generic relation.
       require(reasoner.isEntailed(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(D+"isDescribedBy")),disposition,affordance)),"Generic entailment lost");
      }
     }
     results.put(name,result);System.out.println("PASS fixture "+name+": "+result);
    }finally{reasoner.dispose();}
   }
  }else{
   for(String filename:args[1].split(",")){
    var ontology=flat(load(root.resolve("owl/"+filename)));
    var profile=new org.semanticweb.owlapi.profiles.OWL2DLProfile().checkOntology(ontology);
    require(profile.isInProfile(),filename+" merged profile: "+profile.getViolations());
    var properties=new org.semanticweb.owlapi.util.OWLObjectPropertyManager(ontology);
    var df=ontology.getOWLOntologyManager().getOWLDataFactory();
    if(ontology.containsObjectPropertyInSignature(IRI.create(S+"hasAffordance"))){
     require(!properties.isNonSimple(df.getOWLObjectProperty(IRI.create(S+"hasAffordance"))),"hasAffordance became non-simple");
     require(!properties.isNonSimple(df.getOWLObjectProperty(IRI.create(S+"isAffordanceOf"))),"inverse became non-simple");
    }
    var config=new org.semanticweb.HermiT.Configuration();config.individualTaskTimeout=30000;
    org.semanticweb.owlapi.reasoner.OWLReasoner reasoner;
    try{reasoner=new org.semanticweb.HermiT.ReasonerFactory().createReasoner(ontology,config);}
    catch(org.semanticweb.HermiT.datatypes.UnsupportedDatatypeException e){
     results.put(filename,Map.of("inProfile",true,"reasonerStatus","unsupportedDatatype","error",e.getMessage()));
     new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),results);
     throw e;
    }
    try{
     require(reasoner.isConsistent(),filename+" inconsistent");
     var unsat=new ArrayList<String>();var emptyObject=new ArrayList<String>();var emptyData=new ArrayList<String>();int count=0;
     for(var c:ontology.classesInSignature().filter(c->!c.isOWLNothing()).sorted().toList()){
      if(!reasoner.isSatisfiable(c))unsat.add(c.getIRI().toString());
      if(++count%100==0)System.out.println(filename+": checked "+count+" named classes");
     }
     for(var p:ontology.objectPropertiesInSignature().filter(p->!p.isOWLBottomObjectProperty()).sorted().toList())if(!reasoner.isSatisfiable(df.getOWLObjectSomeValuesFrom(p,df.getOWLThing())))emptyObject.add(p.getIRI().toString());
     for(var p:ontology.dataPropertiesInSignature().filter(p->!p.isOWLBottomDataProperty()).sorted().toList())if(!reasoner.isSatisfiable(df.getOWLDataSomeValuesFrom(p,df.getTopDatatype())))emptyData.add(p.getIRI().toString());
     var result=Map.of("consistent",true,"inProfile",true,"classesChecked",count,"unsatisfiableClasses",unsat,"emptyObjectProperties",emptyObject,"emptyDataProperties",emptyData);
     results.put(filename,result);new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),results);
     System.out.println(filename+": "+result);
     require(unsat.isEmpty()&&emptyObject.isEmpty()&&emptyData.isEmpty(),"Reasoning findings in "+filename);
    }finally{reasoner.dispose();}
   }
  }
  new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),results);
 }
}
