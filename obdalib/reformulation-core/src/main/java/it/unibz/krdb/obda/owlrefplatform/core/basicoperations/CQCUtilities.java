package it.unibz.krdb.obda.owlrefplatform.core.basicoperations;

import it.unibz.krdb.obda.model.Atom;
import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.DatalogProgram;
import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.URIConstant;
import it.unibz.krdb.obda.model.ValueConstant;
import it.unibz.krdb.obda.model.Variable;
import it.unibz.krdb.obda.model.impl.AnonymousVariable;
import it.unibz.krdb.obda.model.impl.FunctionalTermImpl;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.model.impl.VariableImpl;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.Description;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.OClass;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.Ontology;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.Property;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.PropertySomeRestriction;
import it.unibz.krdb.obda.owlrefplatform.core.ontology.SubDescriptionAxiom;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * A class that allows you to perform different operations related to query
 * containment on conjunctive queries.
 * 
 * @author Mariano Rodriguez Muro
 * 
 */
public class CQCUtilities {

	private CQIE					canonicalQuery		= null;

	private static AtomUnifier		unifier				= new AtomUnifier();

	private static QueryAnonymizer	anonymizer			= new QueryAnonymizer();

	private static OBDADataFactory	termFactory			= OBDADataFactoryImpl.getInstance();

	List<Atom>						canonicalbody		= null;

	Atom					canonicalhead		= null;

	Set<Predicate>					canonicalpredicates	= new HashSet<Predicate>(50);

	static Logger					log					= LoggerFactory.getLogger(CQCUtilities.class);

	private Ontology			sigma				= null;

	final private OBDADataFactory	fac					= OBDADataFactoryImpl.getInstance();

	/***
	 * Constructs a CQC utility using the given query.
	 * 
	 * @param query
	 *            A conjunctive query
	 */
	public CQCUtilities(CQIE query) {
		this(query, null);
	}

	/***
	 * Constructs a CQC utility using the given query. If Sigma is not null and
	 * not empty, then it will also be used to verify containment w.r.t.\ Sigma.
	 * 
	 * @param query
	 *            A conjunctive query
	 * @param sigma
	 *            A set of ABox dependencies
	 */
	public CQCUtilities(CQIE query, Ontology sigma) {
		this.sigma = sigma;
		if (sigma != null) {
			// log.debug("Using dependencies to chase the query");
			query = chaseQuery(query, sigma);
		}
		this.canonicalQuery = getCanonicalQuery(query);
		canonicalbody = canonicalQuery.getBody();
		canonicalhead = canonicalQuery.getHead();
		for (Atom atom : canonicalbody) {
			Atom patom = (Atom) atom;
			canonicalpredicates.add(patom.getPredicate());
		}
	}

	/***
	 * This method will "chase" a query with respect to a set of ABox
	 * dependencies. This will introduce atoms that are implied by the presence
	 * of other atoms. This operation may introduce an infinite number of new
	 * atoms, since there might be A ISA exists R and exists inv(R) ISA A
	 * dependencies. To avoid infinite cyles we will chase up to a certain depth
	 * only.
	 * 
	 * To improve performance, sigma should already be saturated.
	 * 
	 * @param query
	 * @param sigma
	 * @return
	 */
	public CQIE chaseQuery(CQIE query, Ontology sigma) {
		sigma.saturate();
		Atom head = (Atom) query.getHead().clone();

		LinkedHashSet<Atom> body = new LinkedHashSet<Atom>();
		body.addAll(query.getBody());

		LinkedHashSet<Atom> newbody = new LinkedHashSet<Atom>();
		newbody.addAll(query.getBody());

		boolean loop = true;
		while (loop) {
			loop = false;
			for (Atom atom : body) {
				Atom patom = (Atom) atom;
				Predicate predicate = atom.getPredicate();

				Term oldTerm1 = null;
				Term oldTerm2 = null;

				Set<SubDescriptionAxiom> pis = sigma.getByIncluded(predicate);
				if (pis == null) {
					continue;
				}
				for (SubDescriptionAxiom pi : pis) {

					Description left = pi.getSub();
					if (left instanceof Property) {

						if (patom.getArity() != 2)
							continue;

						Property lefttRoleDescription = (Property) left;

						if (lefttRoleDescription.isInverse()) {
							oldTerm1 = patom.getTerm(1);
							oldTerm2 = patom.getTerm(0);
						} else {
							oldTerm1 = patom.getTerm(0);
							oldTerm2 = patom.getTerm(1);
						}
					} else if (left instanceof OClass) {

						if (patom.getArity() != 1)
							continue;

						oldTerm1 = patom.getTerm(0);

					} else if (left instanceof PropertySomeRestriction) {
						if (patom.getArity() != 2)
							continue;

						PropertySomeRestriction lefttAtomicRole = (PropertySomeRestriction) left;
						if (lefttAtomicRole.isInverse()) {
							oldTerm1 = patom.getTerm(1);
						} else {
							oldTerm1 = patom.getTerm(0);
						}

					} else {
						throw new RuntimeException("ERROR: Unsupported dependnecy: " + pi.toString());
					}

					Description right = pi.getSuper();

					Term newTerm1 = null;
					Term newTerm2 = null;
					Predicate newPredicate = null;
					Atom newAtom = null;

					if (right instanceof Property) {
						Property rightRoleDescription = (Property) right;
						newPredicate = rightRoleDescription.getPredicate();
						if (rightRoleDescription.isInverse()) {
							newTerm1 = oldTerm2;
							newTerm2 = oldTerm1;
						} else {
							newTerm1 = oldTerm1;
							newTerm2 = oldTerm2;
						}
						newAtom = fac.getAtom(newPredicate, newTerm1, newTerm2);
					} else if (right instanceof OClass) {
						OClass rightAtomicConcept = (OClass) right;
						newTerm1 = oldTerm1;
						newPredicate = rightAtomicConcept.getPredicate();
						newAtom = fac.getAtom(newPredicate, newTerm1);

					} else if (right instanceof PropertySomeRestriction) {
						// Here we need to introduce new variables, for the
						// moment
						// we only do it w.r.t.\ non-anonymous variables.
						// hence we are incomplete in containment detection.

						PropertySomeRestriction rightExistential = (PropertySomeRestriction) right;
						newPredicate = rightExistential.getPredicate();
						if (rightExistential.isInverse()) {
							if (newTerm2 instanceof AnonymousVariable)
								continue;
							newTerm1 = fac.getNondistinguishedVariable();
							newTerm2 = oldTerm1;
							newAtom = fac.getAtom(newPredicate, newTerm1, newTerm2);
						} else {
							if (newTerm1 instanceof AnonymousVariable)
								continue;
							newTerm1 = oldTerm1;
							newTerm2 = fac.getNondistinguishedVariable();
							newAtom = fac.getAtom(newPredicate, newTerm1, newTerm2);
						}
					} else {
						throw new RuntimeException("ERROR: Unsupported dependency: " + pi.toString());
					}

					if (!newbody.contains(newAtom)) {
						newbody.add(newAtom);
					}

				}
			}
			if (body.size() != newbody.size()) {
				loop = true;
				body = newbody;
				newbody = new LinkedHashSet<Atom>();
				newbody.addAll(body);
			}
		}

		LinkedList<Atom> bodylist = new LinkedList<Atom>();
		bodylist.addAll(body);
		CQIE newquery = fac.getCQIE(head, bodylist);
		newquery.setQueryModifiers(query.getQueryModifiers());

		return newquery;
	}

	/***
	 * Computes a query in which all variables have been replaced by
	 * ValueConstants that have the no type and have the same 'name' as the
	 * original variable.
	 * 
	 * This new query can be used for query containment checking.
	 * 
	 * @param q
	 */
	public CQIE getCanonicalQuery(CQIE q) {
		CQIE canonicalquery = q.clone();

		int constantcounter = 1;

		Map<Variable, Term> substitution = new HashMap<Variable, Term>(50);
		Atom head = canonicalquery.getHead();
		constantcounter = getCanonicalAtom(head, constantcounter, substitution);

		for (Atom atom : canonicalquery.getBody()) {
			constantcounter = getCanonicalAtom(atom, constantcounter, substitution);
		}
		return canonicalquery;
	}

	/***
	 * Replaces each Variable inside the atom with a constant. It will return
	 * the counter updated + n, where n is the number of constants introduced by
	 * the method.
	 * 
	 * @param atom
	 * @param constantcounter
	 *            an initial counter for the naming of the new constants. This
	 *            is needed to provide a numbering to each of the new constants
	 * @return
	 */
	private static int getCanonicalAtom(Atom atom, int constantcounter, Map<Variable, Term> currentMap) {
		List<Term> headterms = ((Atom) atom).getTerms();
		for (int i = 0; i < headterms.size(); i++) {
			Term term = headterms.get(i);
			if (term instanceof Variable) {
				Term substitution = null;
				if (term instanceof Variable) {
					substitution = currentMap.get(term);
					if (substitution == null) {
						ValueConstant newconstant = termFactory.getValueConstant("CAN" + ((Variable) term).getName() + constantcounter);
						constantcounter += 1;
						currentMap.put((Variable) term, newconstant);
						substitution = newconstant;
					}
				} else if (term instanceof ValueConstant) {
					ValueConstant newconstant = termFactory.getValueConstant("CAN" + ((ValueConstant) term).getValue() + constantcounter);
					constantcounter += 1;
					substitution = newconstant;
				} else if (term instanceof URIConstant) {
					ValueConstant newconstant = termFactory.getValueConstant("CAN" + ((URIConstant) term).getURI() + constantcounter);
					constantcounter += 1;
					substitution = newconstant;
				}
				headterms.set(i, substitution);
			} else if (term instanceof Function) {
				FunctionalTermImpl function = (FunctionalTermImpl) term;
				List<Term> functionterms = function.getTerms();
				for (int j = 0; j < functionterms.size(); j++) {
					Term fterm = functionterms.get(j);
					if (fterm instanceof Variable) {
						Term substitution = null;
						if (fterm instanceof VariableImpl) {
							substitution = currentMap.get(fterm);
							if (substitution == null) {
								ValueConstant newconstant = termFactory.getValueConstant("CAN" + ((VariableImpl) fterm).getName()
										+ constantcounter);
								constantcounter += 1;
								currentMap.put((Variable) fterm, newconstant);
								substitution = newconstant;

							}
						} else {
							ValueConstant newconstant = termFactory.getValueConstant("CAN" + ((ValueConstant) fterm).getValue()
									+ constantcounter);
							constantcounter += 1;
							substitution = newconstant;
						}
						functionterms.set(j, substitution);
					}
				}
			}
		}
		return constantcounter;
	}

	/***
	 * True if the query used to construct this CQCUtilities object is is
	 * contained in 'query'.
	 * 
	 * @param query
	 * @return
	 */
	public boolean isContainedIn(CQIE query) {
		CQIE duplicate1 = query.clone();

		if (!query.getHead().getPredicate().equals(canonicalhead.getPredicate()))
			return false;

		for (Atom queryatom : query.getBody()) {
			if (!canonicalpredicates.contains(((Atom) queryatom).getPredicate())) {
				return false;
			}
		}

		return hasAnswer(duplicate1);
	}

	/***
	 * Returns true if the query can be answered using the ground atoms in the
	 * body of our canonical query.
	 * 
	 * @param query
	 * @return
	 */
	private boolean hasAnswer(CQIE query) {

		List<Atom> body = query.getBody();
		for (int atomidx = 0; atomidx < body.size(); atomidx++) {
			Atom currentAtomTry = (Atom) body.get(atomidx);

			for (int groundatomidx = 0; groundatomidx < canonicalbody.size(); groundatomidx++) {
				Atom currentGroundAtom = (Atom) canonicalbody.get(groundatomidx);

				Map<Variable, Term> mgu = unifier.getMGU(currentAtomTry, currentGroundAtom);
				if (mgu == null)
					continue;

				CQIE satisfiedquery = unifier.applyUnifier(query.clone(), mgu);
				satisfiedquery.getBody().remove(atomidx);

				if (satisfiedquery.getBody().size() == 0)
					if (canonicalhead.equals(satisfiedquery.getHead()))
						return true;
				/*
				 * Stopping early if we have chosen an MGU that has no
				 * possibility of being successful because of the head.
				 */
				if (unifier.getMGU(canonicalhead, satisfiedquery.getHead()) == null) {
					continue;
				}
				if (hasAnswer(satisfiedquery))
					return true;

			}
		}
		return false;
	}

	private boolean hasAnswer2(CQIE query2) {

		LinkedList<CQIE> querystack = new LinkedList<CQIE>();
		querystack.addLast(query2);
		LinkedList<Integer> lastAttemptIndexStack = new LinkedList<Integer>();
		lastAttemptIndexStack.addLast(0);

		while (!querystack.isEmpty()) {

			CQIE currentquery = querystack.pop();
			List<Atom> body = currentquery.getBody();
			int atomidx = lastAttemptIndexStack.pop();
			Atom currentAtomTry = body.get(atomidx);

			for (int groundatomidx = 0; groundatomidx < canonicalbody.size(); groundatomidx++) {
				Atom currentGroundAtom = (Atom) canonicalbody.get(groundatomidx);

				Map<Variable, Term> mgu = unifier.getMGU(currentAtomTry, currentGroundAtom);
				if (mgu != null) {
					CQIE satisfiedquery = unifier.applyUnifier(currentquery.clone(), mgu);
					satisfiedquery.getBody().remove(atomidx);

					if (satisfiedquery.getBody().size() == 0) {
						if (canonicalhead.equals(satisfiedquery.getHead())) {
							return true;
						}
					}

				}
			}

		}

		return false;
	}

	/**
	 * Removes all atoms that are equalt (syntactically) and then all the atoms
	 * that are redundant due to CQC.
	 * 
	 * @param queries
	 * @throws Exception
	 */
	public static HashSet<CQIE> removeDuplicateAtoms(Collection<CQIE> queries) {
		HashSet<CQIE> newqueries = new HashSet<CQIE>(queries.size() * 2);
		for (CQIE cq : queries) {
			List<Atom> body = cq.getBody();
			for (int i = 0; i < body.size(); i++) {
				Atom currentAtom = (Atom) body.get(i);
				for (int j = i + 1; j < body.size(); j++) {
					Atom comparisonAtom = (Atom) body.get(j);
					if (currentAtom.getPredicate().equals(comparisonAtom.getPredicate())) {
						if (currentAtom.equals(comparisonAtom)) {
							body.remove(j);
						}
					}
				}
			}
			newqueries.add(anonymizer.anonymize(removeRundantAtoms(cq)));
		}
		return newqueries;
	}

	/***
	 * Removes all atoms that are redundant w.r.t to query containment.This is
	 * done by going through all unifyiable atoms, attempting to unify them. If
	 * they unify with a MGU that is empty, then one of the atoms is redundant.
	 * 
	 * 
	 * @param q
	 * @throws Exception
	 */
	public static CQIE removeRundantAtoms(CQIE q) {
		CQIE result = q;
		for (int i = 0; i < result.getBody().size(); i++) {
			Atom currentAtom = result.getBody().get(i);
			for (int j = i + 1; j < result.getBody().size(); j++) {
				Atom nextAtom = result.getBody().get(j);
				Map<Variable, Term> map = unifier.getMGU(currentAtom, nextAtom);
				if (map != null && map.isEmpty()) {
					result = unifier.unify(result, i, j);
				}
			}

		}
		return result;
	}

	/***
	 * Removes queries that are contained syntactically, using the method
	 * isContainedInSyntactic(CQIE q1, CQIE 2). To make the process more
	 * efficient, we first sort the list of queries as to have longer queries
	 * first and shorter queries last.
	 * 
	 * Removal of queries is done in two main double scans. The first scan goes
	 * top-down/down-top, the second scan goes down-top/top-down
	 * 
	 * @param queries
	 */
	public static void removeContainedQueriesSyntacticSorter(List<CQIE> queries, boolean twopasses) {
		int initialsize = queries.size();
		log.debug("Removing trivially redundant queries. Initial set size: {}:", initialsize);
		long startime = System.currentTimeMillis();

		Comparator<CQIE> lenghtComparator = new Comparator<CQIE>() {

			@Override
			public int compare(CQIE o1, CQIE o2) {
				return o2.getBody().size() - o1.getBody().size();
			}
		};

		Collections.sort(queries, lenghtComparator);

		for (int i = 0; i < queries.size(); i++) {
			for (int j = queries.size() - 1; j > i; j--) {
				if (isContainedInSyntactic(queries.get(i), queries.get(j))) {
					queries.remove(i);
					i = -1;
					break;
				}
			}
		}

		if (twopasses) {
			for (int i = queries.size() - 1; i > 0; i--) {
				for (int j = 0; j < i; j++) {
					if (isContainedInSyntactic(queries.get(i), queries.get(j))) {
						queries.remove(i);
						i = +1;
						break;
					}
				}
			}
		}

		int newsize = queries.size();
		int queriesremoved = initialsize - newsize;
		long endtime = System.currentTimeMillis();
		long time = (endtime - startime) / 1000;
		log.debug("Done. Time elapse: {}s", time);
		log.debug("Resulting size: {}   Queries removed: {}", newsize, queriesremoved);

	}

	/***
	 * Check if query cq1 is contained in cq2, sintactically. That is, if the
	 * head of cq1 and cq2 are equal according to toString().equals and each
	 * atom in cq2 is also in the body of cq1 (also by means of
	 * toString().equals().
	 * 
	 * @param cq1
	 * @param cq2
	 * @return
	 */
	public static boolean isContainedInSyntactic(CQIE cq1, CQIE cq2) {
		if (!cq2.getHead().equals(cq1.getHead())) {
			return false;
		}

		for (Atom atom : cq2.getBody()) {
			if (!cq1.getBody().contains(atom))
				return false;
		}
		return true;
	}

	public static void removeContainedQueriesSorted(List<CQIE> queries, boolean twopasses) {
		removeContainedQueriesSorted(queries, twopasses, null);

	}

	public static void removeContainedQueriesSorted(DatalogProgram program, boolean twopasses) {
		removeContainedQueriesSorted(program.getRules(), twopasses, null);
	}

	public static void removeContainedQueriesSorted(DatalogProgram program, boolean twopasses, Ontology sigma) {
		removeContainedQueriesSorted(program.getRules(), twopasses, sigma);
	}

	/***
	 * Removes queries that are contained syntactically, using the method
	 * isContainedInSyntactic(CQIE q1, CQIE 2). To make the process more
	 * efficient, we first sort the list of queries as to have longer queries
	 * first and shorter queries last.
	 * 
	 * Removal of queries is done in two main double scans. The first scan goes
	 * top-down/down-top, the second scan goes down-top/top-down
	 * 
	 * @param queries
	 */
	public static void removeContainedQueriesSorted(List<CQIE> queries, boolean twopasses, Ontology sigma) {
		removeContainedQueries(queries, twopasses, sigma, true);
	}

	/***
	 * Removes queries that are contained syntactically, using the method
	 * isContainedInSyntactic(CQIE q1, CQIE 2). To make the process more
	 * efficient, we first sort the list of queries as to have longer queries
	 * first and shorter queries last.
	 * 
	 * Removal of queries is done in two main double scans. The first scan goes
	 * top-down/down-top, the second scan goes down-top/top-down
	 * 
	 * @param queries
	 */
	public static void removeContainedQueries(List<CQIE> queries, boolean twopasses, Ontology sigma, boolean sort) {

		int initialsize = queries.size();
		log.debug("Optimzing w.r.t. CQC. Initial size: {}:", initialsize);

		double startime = System.currentTimeMillis();

		Comparator<CQIE> lenghtComparator = new Comparator<CQIE>() {

			@Override
			public int compare(CQIE o1, CQIE o2) {
				return o2.getBody().size() - o1.getBody().size();
			}
		};

		if (sort)
		{
//			log.debug("Sorting...");
			Collections.sort(queries, lenghtComparator);
		}

		for (int i = 0; i < queries.size(); i++) {
			CQCUtilities cqc = new CQCUtilities(queries.get(i), sigma);
			for (int j = queries.size() - 1; j > i; j--) {
				if (cqc.isContainedIn(queries.get(j))) {
					queries.remove(i);
					i -= 1;
					break;
				}
			}
		}

		if (twopasses) {
			for (int i = (queries.size() - 1); i >= 0; i--) {
				CQCUtilities cqc = new CQCUtilities(queries.get(i), sigma);
				for (int j = 0; j < i; j++) {
					if (cqc.isContainedIn(queries.get(j))) {
						queries.remove(i);
						break;
					}
				}
			}
		}

		int newsize = queries.size();
//		int queriesremoved = initialsize - newsize;

		double endtime = System.currentTimeMillis();
		double time = (endtime - startime) / 1000;
		
		log.debug("Resulting size: {}  Time elapsed: {}", newsize, time) ;		
	}

}
