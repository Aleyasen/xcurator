/*
 *    Copyright (c) 2013, University of Toronto.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */
package edu.toronto.cs.xcurator.generator;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.toronto.cs.xcurator.model.Attribute;
import edu.toronto.cs.xcurator.model.AttributeInstance;
import edu.toronto.cs.xcurator.model.OntologyLink;
import edu.toronto.cs.xcurator.model.Relation;
import edu.toronto.cs.xcurator.model.RelationInstance;
import edu.toronto.cs.xcurator.model.Schema;
import edu.toronto.cs.xcurator.model.SchemaInstance;
import edu.toronto.cs.xml2rdf.mapping.generator.SchemaException;
import edu.toronto.cs.xml2rdf.xml.XMLUtils;
import org.w3c.dom.Text;

public class BasicSchemaExtraction implements MappingStep {

    private final int maxElements;

    /**
     * @param maxElements The maximum number of elements to process from the
     * source document.
     */
    public BasicSchemaExtraction(int maxElements) {
        this.maxElements = maxElements;
    }

    public BasicSchemaExtraction() {
        // Eric: -1 signify that there is no upper bound
        // on the number of elements to be processed
        this(-1);
    }

    @Override
    public void process(Element root, Map<String, Schema> schemas) {
        // The organization of the XML files should have "clinical_studies" as the
        // very root document element (which is passed in as root), with many
        // "clinical_study" child nodes, which is the children variable below.
        NodeList children = root.getChildNodes();

        // Extract and merge the child element nodes and their associated schemas.
        // Iterate through all child nodes or up to the maximum number specified,
        // and process (merge) ONLY child nodes that are elements.
        for (int i = 0; i < children.getLength()
                && (maxElements == -1 || i < maxElements); i++) {
            // Only process element nodes, skipping comment nodes and etc 
            if (children.item(i) instanceof Element) {
                // Get the child element node.
                Element child = (Element) children.item(i);
                String name = child.getNodeName();
                // Create a schema for this child element node if one with the same node
                // name does not exist. Consequently, there will be only one schema for
                // each unique node name. The path of the schema is the ABSOLUTE path to
                // the child element node, starting with "/" and the root element node
                // name, such as "/clinical_studies/clinical_study".
                Schema schema = schemas.get(name);

                if (schema == null) {
                    // Eric: What if child nodes have the same name but at different
                    // layers of the XML file and thus different path? Only the first path
                    // is used?
                    // Eric: Parent is set to null, why?
                    schema = new Schema(null, child, "/" + root.getNodeName() + "/"
                            + name);
                    schemas.put(name, schema);
                }

                // Merge the child element node with its schema, 
                // that is, the schema of the same name.
                try {
                    mergeWithSchema(child, schema, schemas);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private SchemaInstance mergeWithSchema(Element element, Schema schema,
            Map<String, Schema> schemas)
            throws SchemaException, XPathExpressionException {
        // Create the schema instance and add to the schema (in one step)
        SchemaInstance instance = createSchemaInstance(schema, element);

        // Set the schema name, if null, to the name of the element;
        // or check if the two names are the same, as they should be
        // Eric: I believe this is unnecessary and should be removed
        String schemaName = schema.getName();
        if (schemaName == null) {
            schema.setName(element.getNodeName());
        } else {
            if (!schema.getName().equals(element.getNodeName())) {
                throw new SchemaException("Schema element names do not match.");
            }
        }

        // Never merge leaf element nodes.
        if (XMLUtils.isLeaf(element)) {
            return instance;
        }

        // Now that we are here, we know the element must NOT be a leaf
        // Get all the (immediate next level) child nodes of the given element node
        NodeList children = element.getChildNodes();

        // Iterate through all child nodes, but process
        // ONLY those that are elements
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {

                // Process child element node that is NOT a leaf element node.
                if (!XMLUtils.isLeaf(children.item(i))) {

                    // Get the non-leaf child element node, which means it has
                    // leaf (and possibly non-leaf) child element nodes under it
                    Element child = (Element) children.item(i);

                    // The boolean value to indicate if a previous instance of this
                    // non-leaf child element with the same name has already been
                    // processed/merged.
                    boolean found = false;

                    // Find out if this non-leaf child element already exists
                    // in parent element's relations, meaning that a previous
                    // instance of the non-leaf child element with the same name
                    // has already been processed and put into the relations of
                    // the parent element.
                    //
                    // If so, merge this instance of the non-leaf child element
                    // with the already consolidated associated schema, during
                    // which new relations or attributes might be added to this
                    // schema
                    for (Relation childRelation : schema.getRelations()) {
                        if (childRelation.getName().equals(child.getNodeName())) {
                            SchemaInstance childInstance
                                    = mergeWithSchema(child, childRelation.getChild(), schemas);
                            createRelationInstance(childRelation, instance, childInstance);
                            found = true;
                            break;
                        }
                    }

                    // This is the first encounter of the non-leaf child element
                    // with this node name
                    if (!found) {

                        // Get the name of the non-leaf child element node
                        String name = child.getNodeName();
                        // Create the path, which is the ABSOLUTE path to this
                        // non-leaf child element node, starting with "/"
                        String path = schema.getPath() + "/" + name;

                        // Create a schema for this non-leaf child element node,
                        // if none exists yet
                        Schema childSchema = schemas.get(name);
                        if (childSchema == null) {
                            // Eric: parent parameter is again set to null.
                            // Why not set the parent to the current schema?
                            childSchema = new Schema(null, child, path);
                            schemas.put(name, childSchema);
                        } else {
                            // Such schema exists, which means this schema is already
                            // the child of a DIFFERENT schema, we should update its
                            // path. This can happen because we allow the same schema
                            // (defined by its name) have MUTIPLE parent schema!
                            childSchema.setPath(childSchema.getPath() + "|" + path);
                        }

                        // Merge this non-leaf child element node first before
                        // further processing this node
                        SchemaInstance childInstance = mergeWithSchema(child, childSchema,
                                schemas);

                        // Create the lookupKeys for the creation of relation later
                        // This is essentially a list of all leaf elements that
                        // exist under the current child node
                        Set<Attribute> lookupKeys = new HashSet<Attribute>();

                        // Get the list of RELATIVE path to all leaf element nodes
                        // of the current non-leaf child element node, with path
                        // starting with the name of the current non-leaf child
                        // element node (and not "/"), and ending with the name
                        // of the leaf element nodes
                        List<String> leaves = XMLUtils.getAllLeaves(child);

                        // Iterate through all paths to the leaf element nodes
                        for (String leafPath : leaves) {

                            // Get the name of the current LEAF element node
                            int lastNodeIndex = leafPath.lastIndexOf('/');
                            String lastNodeName = leafPath.substring(lastNodeIndex + 1);

                            // Create leafName by simply replacing all "/" with "."
                            String leafName = leafPath.replace('/', '.');

                            // Append ".name" to the end of leafName if the current
                            // leaf element node has been promoted and has an
                            // OntologyLink schema associated with it
                            //
                            // Eric: Is it correct to say that the ONLY case where
                            // lastNodeSchema is NOT null is when the child node has
                            // been promoted, which means lastNodeSchema is ALWAYS
                            // an OntologyLink schema?
                            Schema lastNodeSchema = schemas.get(lastNodeName);
                            if (lastNodeSchema instanceof OntologyLink) {
                                // Eric: Why ".name"? What's the meaning behind this?
                                leafName += ".name";
                            }

                            // Create leafPath through removing the name of the parent non-leaf
                            // element node at the beginning, along with the "/", and then append
                            // "/text()" at the end of the leafPath.
                            //
                            // This is essentially the RELATIVE path to the TEXT VALUE of the
                            // current leaf element node under the parent non-leaf element node,
                            // and this path will be understood correctly by XPath
                            leafPath = leafPath.replaceAll("^" + child.getNodeName() + "/?", "");
                            // Eric: Why would leafPath ever be empty anyways? It must at least
                            // contain the name of the LAEF node.
                            leafPath = leafPath.length() > 0 ? leafPath + "/text()" : "text()";

                            // Create an entry to the lookupKeys, which keeps track of the parent
                            // non-leaf element node's schema, the name and the RELATIVE path to
                            // all the TEXT VALUES of the leaf element nodes under it, and whether
                            // these element nodes are keys or not
                            lookupKeys.add(new Attribute(schema, leafName, leafPath, false));
                        }

                        // Eric: Why is path (the third parameter) set to name?
                        // Set the parent-child (schema-childSchema) relation, with lookupKeys essentially
                        // a list of LEAF nodes of the child (childSchema) and their parent is set to
                        // schema
                        // One can think of the path to the childSchema as schema.getPath() + "/" + name
                        // (name is the name of the childSchema)
                        Relation relation = new Relation(schema, name, name, childSchema, lookupKeys);
                        createRelationInstance(relation, instance, childInstance);
                    }
                } // Process child element node that IS INDEED a leaf element node
                else {

                    // Get the leaf child element and its name
                    Element child = (Element) children.item(i);
                    String name = child.getNodeName();

                    // Get the RELATIVE path to the leaf child element
                    String path = name + "/text()";
          // The following is the ABSOLUTE PATH
                    // String path = schema.getPath() + "/" + name (+ "/text()");

                    // Find out if a previous instance of the leaf child element
                    // with the same name has already been added to the attributes
                    // or relations. Since the leaf child element has no children,
                    // the previous instance will be exactly the same as the current
                    // instance (structure-wise), the current instance does not need
                    // to be processed anymore.
                    boolean found = false;

                    // Get the attribute of the same name, which may already exist
                    // or may have to be created first
                    Attribute attribute = null;

                    for (Attribute childAttribute : schema.getAttributes()) {
                        if (childAttribute.getName().equals(child.getNodeName())) {
                            attribute = childAttribute;
                            found = true;
                            break;
                        }
                    }

          // Because ontology linking is now a independent mapping step that
                    // occur after the schema extraction, at the time of schema extraction,
                    // NO attribute should be promoted to be ontologyLink schemas yet.
                    // Therefore, the following code is redundant.
                    // for (Relation childRelation : schema.getRelations()) {
                    //  if (childRelation.getChild() instanceof OntologyLink
                    //      && childRelation.getName().equals(child.getNodeName())) {
                    //    found = true;
                    //    break;
                    //  }
                    // }
                    // If no previous instance has found, which means this is the first
                    // encounter of the leaf child node with this name
                    if (!found) {
                        // The attribute is created with path being the RELATIVE path
                        // to the TEXT VALUE of the leaf child node
                        attribute = new Attribute(schema, name, path, false);
                        schema.addAttribute(attribute);
                    }

                    // Create the attribute instance and add to the attribute
                    createAttributeInstance(attribute, instance, child);
                }
            } else if (children.item(i) instanceof Text) {
                // Because the element must not be a leaf, so we are safe to assume
                // the text node child has other element node siblings
                String textContent = children.item(i).getTextContent().trim();
                if (textContent.compareTo("") != 0) {
                    schema.addAttribute(new Attribute(schema, "value", "text()", false));
                }
            }
        }

        return instance;
    }

    private SchemaInstance createSchemaInstance(Schema schema, Element element) {
        SchemaInstance instance = null;
        try {
            instance = new SchemaInstance(element);
            schema.addInstace(instance);
        } catch (IOException e) {
        }
        return instance;
    }

    private AttributeInstance createAttributeInstance(Attribute attribute,
            SchemaInstance schemaInstance, Element attributeElement) {
        AttributeInstance instance = null;
        try {
            instance = new AttributeInstance(schemaInstance, attributeElement);
            attribute.addInstance(instance);
        } catch (IOException e) {
        }
        return instance;
    }

    private RelationInstance createRelationInstance(Relation relation,
            SchemaInstance from, SchemaInstance to) {
        RelationInstance instance = new RelationInstance(from, to);
        relation.addInstance(instance);
        return instance;
    }
}
