package it.unibz.inf.ontop.dbschema.impl.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.*;
import it.unibz.inf.ontop.dbschema.*;
import it.unibz.inf.ontop.exception.MetadataExtractionException;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Stream;

@JsonDeserialize(as = JsonBasicLens.class)
public class JsonBasicLens extends JsonBasicOrJoinLens {

    @Nonnull
    public final List<String> baseRelation;

    @JsonCreator
    public JsonBasicLens(@JsonProperty("columns") JsonBasicOrJoinLens.Columns columns, @JsonProperty("name") List<String> name,
                         @JsonProperty("baseRelation") List<String> baseRelation,
                         @JsonProperty("filterExpression") String filterExpression,
                         @JsonProperty("uniqueConstraints") UniqueConstraints uniqueConstraints,
                         @JsonProperty("otherFunctionalDependencies") OtherFunctionalDependencies otherFunctionalDependencies,
                         @JsonProperty("foreignKeys") ForeignKeys foreignKeys,
                         @JsonProperty("nonNullConstraints") NonNullConstraints nonNullConstraints,
                         @JsonProperty("iriSafeConstraints") IRISafeConstraints iriSafeConstraints) {
        super(name, uniqueConstraints, otherFunctionalDependencies, foreignKeys, nonNullConstraints, iriSafeConstraints, columns, filterExpression);
        this.baseRelation = baseRelation;
    }

    @Override
    protected ImmutableList<ParentDefinition> extractParentDefinitions(DBParameters dbParameters,
                                                                                     MetadataLookup parentCacheMetadataLookup)
            throws MetadataExtractionException {
        QuotedIDFactory quotedIDFactory = dbParameters.getQuotedIDFactory();
        NamedRelationDefinition parentDefinition = parentCacheMetadataLookup.getRelation(quotedIDFactory.createRelationID(
                baseRelation.toArray(new String[0])));

        return ImmutableList.of(new ParentDefinition(parentDefinition,""));
    }

    /**
     * TODO: support duplication of the column (not just renamings)
     */
    @Override
    public ImmutableList<ImmutableList<Attribute>> getAttributesIncludingParentOnes(Lens lens,
                                                                                    ImmutableList<Attribute> parentAttributes) {
        if (filterExpression != null && (!filterExpression.isEmpty()))
            // TODO: log a warning
            return ImmutableList.of();

        return getDerivedFromParentAttributes(lens, parentAttributes);
    }

    public boolean propagateUniqueConstraintsUp(Lens relation, ImmutableList<NamedRelationDefinition> parents,
                                                QuotedIDFactory idFactory) throws MetadataExtractionException {
        //There is no guarantee a UC will hold in the parent if the lens performed a filter.
        if(filterExpression != null && !filterExpression.isEmpty())
            return false;

        ImmutableList<UniqueConstraint> ucs = relation.getUniqueConstraints();
        ImmutableList<UniqueConstraint> propagableConstraints = ucs.stream()
                .filter(u -> hasNoDeterminantOverridden(u, idFactory))
                .filter(u -> isUcNotInParent(u, parents.get(0)))
                .collect(ImmutableCollectors.toList());
        if(propagableConstraints.isEmpty())
            return false;

        for(UniqueConstraint uc : propagableConstraints) {
            try {
                UniqueConstraint.Builder builder = UniqueConstraint.builder(parents.get(0), uc.getName());
                for (Attribute attribute : uc.getDeterminants()) {
                    builder.addDeterminant(attribute.getID());
                }
                builder.build();
            } catch (AttributeNotFoundException e) {
                throw new MetadataExtractionException(e);
            }
        }
        return true;
    }

    private boolean hasNoAttributeSetOverridden(ImmutableSet<Attribute> attributes, QuotedIDFactory idFactory) {
        return attributes.stream()
                .noneMatch(a -> columns.added.stream()
                        .anyMatch(c -> a.getID().equals(idFactory.createAttributeID(c.name))));
    }

    private boolean hasNoDeterminantOverridden(UniqueConstraint uc, QuotedIDFactory idFactory) {
        return hasNoAttributeSetOverridden(uc.getDeterminants(), idFactory);
    }

    private boolean isUcNotInParent(UniqueConstraint uc, NamedRelationDefinition parent) {
        return parent.getUniqueConstraints().stream()
                .noneMatch(u -> ucEquals(uc, u));
    }

    private boolean ucEquals(UniqueConstraint uc1, UniqueConstraint uc2) {
        ImmutableList<ImmutableSet<QuotedID>> determinants = Stream.of(uc1, uc2)
                .map(uc -> uc.getDeterminants().stream()
                        .map(Attribute::getID)
                        .collect(ImmutableCollectors.toSet())
                )
                .collect(ImmutableCollectors.toList());
        return determinants.get(0).equals(determinants.get(1));
    }

    @Override
    public boolean propagateForeignKeyConstraintsUp(Lens relation, ImmutableList<NamedRelationDefinition> parents,
                                                QuotedIDFactory idFactory) throws MetadataExtractionException {
        ImmutableList<ForeignKeyConstraint> fks = relation.getForeignKeys();
        ImmutableList<ForeignKeyConstraint> propagableConstraints = fks.stream()
                .filter(fk -> hasNoAttributeSetOverridden(fk.getComponents().stream()
                        .map(ForeignKeyConstraint.Component::getAttribute)
                        .collect(ImmutableCollectors.toSet()), idFactory))
                .filter(fk -> isFkNotInParent(fk, parents.get(0)))
                .filter(fk -> fk.getReferencedRelation() != parents.get(0))
                .collect(ImmutableCollectors.toList());
        if(propagableConstraints.isEmpty())
            return false;

        for(ForeignKeyConstraint fk : propagableConstraints) {
            try {
                ForeignKeyConstraint.Builder builder = ForeignKeyConstraint.builder(fk.getName(), parents.get(0), fk.getReferencedRelation());
                for (ForeignKeyConstraint.Component component : fk.getComponents()) {
                    builder.add(component.getAttribute().getID(), component.getReferencedAttribute().getID());
                }
                builder.build();
            } catch (AttributeNotFoundException e) {
                throw new MetadataExtractionException(e);
            }
        }
        return true;
    }

    private boolean isFkNotInParent(ForeignKeyConstraint fk, NamedRelationDefinition parent) {
        return parent.getForeignKeys().stream()
                .noneMatch(f -> fkEquals(f, fk));
    }

    private boolean fkEquals(ForeignKeyConstraint fk1, ForeignKeyConstraint fk2) {
        return Optional.of(fk1)
                .filter(f -> f.getReferencedRelation().equals(fk2.getReferencedRelation()))
                .filter(f -> f.getComponents().stream()
                        .allMatch(c1 -> fk2.getComponents().stream()
                                .anyMatch(c2 -> c1.getAttribute().getID().equals(c2.getAttribute().getID())
                                                && c1.getReferencedAttribute().equals(c2.getReferencedAttribute()))))
                .isPresent();
    }
}
