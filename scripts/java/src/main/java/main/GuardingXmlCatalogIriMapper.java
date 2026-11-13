package main;

import org.semanticweb.owlapi.model.*;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** A deliberately small, strict local uri catalog. No delegation, rewrite or network fallback. */
public final class GuardingXmlCatalogIriMapper implements OWLOntologyIRIMapper {
    private static final String NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
    private final Map<IRI, Path> entries;

    public GuardingXmlCatalogIriMapper(java.io.File file) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(file).getDocumentElement();
            if (!NS.equals(root.getNamespaceURI()) || !"catalog".equals(root.getLocalName()))
                throw new IOException("Expected an XML catalog: " + file);
            var mappings = new LinkedHashMap<IRI, Path>();
            read(root, file.toPath().toRealPath().getParent(), mappings);
            entries = Collections.unmodifiableMap(mappings);
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid local catalog " + file + ": " + e.getMessage(), e); }
    }

    private static void read(Element root, Path base, Map<IRI, Path> mappings) throws IOException {
        if (root.hasAttributeNS(XMLConstants.XML_NS_URI, "base")) throw new IOException("xml:base is not supported in local catalogs");
        for (var node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element element)) continue;
            if (!NS.equals(element.getNamespaceURI())) throw new IOException("Unsupported catalog namespace");
            if ("group".equals(element.getLocalName())) { read(element, base, mappings); continue; }
            if (!"uri".equals(element.getLocalName())) throw new IOException("Unsupported catalog directive: " + element.getTagName());
            if (element.hasAttributeNS(XMLConstants.XML_NS_URI, "base")) throw new IOException("xml:base is not supported");
            String name = element.getAttribute("name"), target = element.getAttribute("uri");
            if (name.isBlank() || target.isBlank() || !java.net.URI.create(name).isAbsolute())
                throw new IOException("Catalog uri requires absolute name and local uri");
            if (java.net.URI.create(target).isAbsolute()) throw new IOException("Catalog target must be relative: " + target);
            var path = base.resolve(target).normalize();
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(base))
                throw new IOException("Missing or non-local catalog target: " + path);
            if (mappings.putIfAbsent(IRI.create(name), path.toRealPath()) != null)
                throw new IOException("Ambiguous catalog mapping: " + name);
        }
    }

    public Map<IRI, Path> entries() { return entries; }

    @Override public IRI getDocumentIRI(IRI original) {
        var path = entries.get(original);
        if (path == null) throw new IllegalArgumentException("Missing local catalog mapping: " + original);
        return IRI.create(path.toUri());
    }
}
