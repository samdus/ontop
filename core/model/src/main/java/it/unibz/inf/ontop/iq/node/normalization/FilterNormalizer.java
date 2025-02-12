package it.unibz.inf.ontop.iq.node.normalization;

import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.IQTreeCache;
import it.unibz.inf.ontop.iq.node.FilterNode;
import it.unibz.inf.ontop.utils.VariableGenerator;

public interface FilterNormalizer {

    IQTree normalizeForOptimization(FilterNode filterNode, IQTree child, VariableGenerator variableGenerator, IQTreeCache treeCache);
}
