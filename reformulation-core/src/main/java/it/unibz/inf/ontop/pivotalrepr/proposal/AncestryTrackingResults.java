package it.unibz.inf.ontop.pivotalrepr.proposal;

import it.unibz.inf.ontop.pivotalrepr.QueryNode;

import java.util.Optional;

/**
 * TODO: explain
 *
 */
public interface AncestryTrackingResults<N extends QueryNode> extends NodeCentricOptimizationResults<N> {

    /**
     * TODO: explain
     */
    <M extends QueryNode> NodeCentricOptimizationResults<M> generateResultsForAncestor(M originalAncestorNode);

    Optional<AncestryTracker> getOptionalTracker();


}
