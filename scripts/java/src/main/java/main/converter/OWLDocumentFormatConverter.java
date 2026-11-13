package main.converter;

import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

public class OWLDocumentFormatConverter {

	public OWLDocumentFormat convert(final String source) {
		return switch (source.toUpperCase(java.util.Locale.ROOT)) {
			case "RDF_XML" -> new RDFXMLDocumentFormat();
			case "OWL_XML" -> new OWLXMLDocumentFormat();
			case "FUNCTIONAL" -> new FunctionalSyntaxDocumentFormat();
			case "MANCHESTER" -> throw new IllegalArgumentException("Invalid ontology.format: Manchester export can lose GCIs; use functional, rdf_xml or owl_xml");
			default -> throw new IllegalArgumentException("Invalid ontology.format: " + source);
		};
	}
}
