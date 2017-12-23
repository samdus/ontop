package it.unibz.inf.ontop.spec.mapping.transformer;

import it.unibz.inf.ontop.dbschema.DBMetadata;
import it.unibz.inf.ontop.spec.mapping.Mapping;
import it.unibz.inf.ontop.spec.ontology.OntologyABox;
import it.unibz.inf.ontop.spec.ontology.ClassifiedTBox;
import it.unibz.inf.ontop.spec.OBDASpecInput;
import it.unibz.inf.ontop.spec.OBDASpecification;

/**
 * TODO: find a better name
 */
public interface MappingTransformer {

    OBDASpecification transform(OBDASpecInput specInput, Mapping mapping, DBMetadata dbMetadata,
                                OntologyABox abox, ClassifiedTBox tBox);

    OBDASpecification transform(OBDASpecInput specInput, Mapping mapping, DBMetadata dbMetadata,
                                ClassifiedTBox tBox);
}
