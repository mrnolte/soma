package main.converter;


import org.semanticweb.owlapi.model.IRI;

public class IRIConverter {

	public IRI convert(final String from) {
		return IRI.create(from);
	}
}