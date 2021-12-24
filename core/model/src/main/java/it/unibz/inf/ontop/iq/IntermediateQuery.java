package it.unibz.inf.ontop.iq;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.Variable;

import java.util.Optional;

/**
 *
 */
public interface IntermediateQuery {

    QueryNode getRootNode();

    ImmutableList<QueryNode> getNodesInTopDownOrder();

    ImmutableList<QueryNode> getChildren(QueryNode node);

    Optional<QueryNode> getChild(QueryNode currentNode, BinaryOrderedOperatorNode.ArgumentPosition position);

    Optional<QueryNode> getFirstChild(QueryNode node);

    /**
     * TODO: explain
     */
    Optional<BinaryOrderedOperatorNode.ArgumentPosition> getOptionalPosition(QueryNode parentNode, QueryNode child);

    DistinctVariableOnlyDataAtom getProjectionAtom();

    /**
     * Set of variables that are returned by the sub-tree.
     */
    ImmutableSet<Variable> getVariables(QueryNode subTreeRootNode);
}
