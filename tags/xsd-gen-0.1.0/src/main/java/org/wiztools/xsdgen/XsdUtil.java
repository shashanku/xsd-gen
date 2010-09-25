package org.wiztools.xsdgen;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import org.wiztools.commons.StringUtil;

/**
 *
 * @author subWiz
 */
final class XsdUtil {
    private XsdUtil() {}

    private static final String XSD_NS_URI = "http://www.w3.org/2001/XMLSchema";

    private static void processAttributes(final Element inElement, final Element outElement) {
        for(int i=0; i<inElement.getAttributeCount(); i++) {
            final Attribute attr = inElement.getAttribute(i);

            final String name = attr.getLocalName();

            final String nsPrefix = attr.getNamespacePrefix();
            final String nsURI = attr.getNamespaceURI();
            final String nsName = (nsPrefix==null? "": nsPrefix + ":") + name;
            
            final String value = attr.getValue();

            Element attrElement = new Element("xsd:attribute", XSD_NS_URI);
            attrElement.addAttribute(new Attribute("name", name));
            attrElement.addAttribute(new Attribute("type", TypeInferenceUtil.getTypeOfContent(value)));
            attrElement.addAttribute(new Attribute("use", "required"));

            outElement.appendChild(attrElement);
        }
    }

    private static void recurseGen(Element parent, Element parentOutElement) {
        // Adding complexType element:
        Element complexType = new Element("xsd:complexType", XSD_NS_URI);
        Element sequence = new Element("xsd:sequence", XSD_NS_URI);
        complexType.appendChild(sequence);
        processAttributes(parent, complexType);
        parentOutElement.appendChild(complexType);

        Elements childs = parent.getChildElements();
        final Set<String> elementNamesProcessed = new HashSet<String>();
        for(int i=0; i<childs.size(); i++) {
            Element e = childs.get(i);
            final String localName = e.getLocalName();
            final String nsURI = e.getNamespaceURI();
            final String nsPrefix = e.getNamespacePrefix();
            final String nsName = (nsPrefix!=null?nsPrefix + ":": "") + localName;

            if(e.getChildElements().size() > 0) { // Is complex type with children!
                Element element = new Element("xsd:element", XSD_NS_URI);
                element.addAttribute(new Attribute("name", localName));
                recurseGen(e, element);

                sequence.appendChild(element);
            }
            else {
                final String cnt = e.getValue();
                final String eValue = cnt==null? null: cnt.trim();

                final String type = TypeInferenceUtil.getTypeOfContent(eValue);

                if(!elementNamesProcessed.contains(nsName)) { // process an element first time only
                    Element element = new Element("xsd:element", XSD_NS_URI);
                    element.addAttribute(new Attribute("name", localName));
                    
                    if(parent.getChildElements(localName, nsURI).size() > 1){
                        element.addAttribute(new Attribute("maxOccurs", "unbounded"));
                    }
                    else {
                        element.addAttribute(new Attribute("minOccurs", "0"));
                        element.addAttribute(new Attribute("maxOccurs", "1"));
                    }
                    // Attributes
                    final int attrCount = e.getAttributeCount();
                    if(attrCount > 0) { // has attributes: complex type without sequence!
                        Element complexTypeCurrent = new Element("xsd:complexType", XSD_NS_URI);
                        Element simpleContent = new Element("xsd:simpleContent", XSD_NS_URI);
                        Element extension = new Element("xsd:extension", XSD_NS_URI);
                        extension.addAttribute(new Attribute("base", type));
                        processAttributes(e, extension);
                        simpleContent.appendChild(extension);
                        complexTypeCurrent.appendChild(simpleContent);

                        element.appendChild(complexTypeCurrent);
                    }
                    else { // if no attributes, just put the type:
                        element.addAttribute(new Attribute("type", type));
                    }
                    sequence.appendChild(element);
                }
            }
            elementNamesProcessed.add(nsName);
        }
    }

    static void parse(File file) throws ParsingException, IOException {
        Builder parser = new Builder();
        Document doc = parser.build(file);
        final Element rootElement = doc.getRootElement();

        // Output Document
        Element outRoot = new Element("xsd:schema", XSD_NS_URI);
        Document outDoc = new Document(outRoot);

        // Setting targetNamespace:
        {
            final String nsPrefix = rootElement.getNamespacePrefix();
            if(!StringUtil.isStrEmpty(nsPrefix)) {
                outRoot.addAttribute(new Attribute("targetNamespace", rootElement.getNamespaceURI()));
                outRoot.addAttribute(new Attribute("elementFormDefault", "qualified"));
            }
        }

        // Adding all other namespace attributes:
        {
            for(int i=0; i<rootElement.getNamespaceDeclarationCount(); i++) {
                final String nsPrefix = rootElement.getNamespacePrefix(i);
                final String nsURI = rootElement.getNamespaceURI(nsPrefix);
                outRoot.addNamespaceDeclaration(nsPrefix, nsURI);
            }
        }

        // Adding the root element:
        Element rootElementXsd = new Element("xsd:element", XSD_NS_URI);
        {
            rootElementXsd.addAttribute(new Attribute("name", rootElement.getLocalName()));
            outRoot.appendChild(rootElementXsd);
        }

        recurseGen(rootElement, rootElementXsd);

        // Root element attributes
        processAttributes(rootElement, rootElementXsd);

        // Display output:
        Serializer serializer = new Serializer(System.out, "UTF-8");
        serializer.setIndent(2);
        serializer.write(outDoc);
    }
}
