package it.unibz.inf.ontop.model.term.functionsymbol.impl;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.vocabulary.SPARQL;

public class Sha384SPARQLFunctionSymbolImpl extends AbstractHashSPARQLFunctionSymbol {

    protected Sha384SPARQLFunctionSymbolImpl(RDFDatatype xsdStringType) {
        super(SPARQL.SHA384, xsdStringType);
    }

    @Override
    protected ImmutableTerm computeLexicalTerm(ImmutableList<ImmutableTerm> subLexicalTerms,
                                               ImmutableList<ImmutableTerm> typeTerms, TermFactory termFactory, ImmutableTerm returnedTypeTerm) {
        return termFactory.getDBSha384(subLexicalTerms.get(0));
    }
}
