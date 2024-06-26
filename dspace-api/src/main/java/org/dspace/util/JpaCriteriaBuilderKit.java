/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.util;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;

/**
 * Data structure containing the required objects to build criteria
 * for a JPA query built using the JPA Criteria API.
 * The getters match those generated by the JVM when using a record
 * so that no API changes will be required when this class gets converted
 * into a record when DSpace gets promoted to Java 17 or later.
 * @author Jean-François Morin (Université Laval)
 */
// TODO: Convert this data structure into a record when DSpace gets promoted to Java 17 or later
public class JpaCriteriaBuilderKit<T> {

    private CriteriaBuilder criteriaBuilder;
    /** Can be a CriteriaQuery as well as a Subquery - both extend AbstractQuery. */
    private AbstractQuery<T> query;
    private Root<T> root;

    public JpaCriteriaBuilderKit(CriteriaBuilder criteriaBuilder, AbstractQuery<T> query,
            Root<T> root) {
        this.criteriaBuilder = criteriaBuilder;
        this.query = query;
        this.root = root;
    }

    public CriteriaBuilder criteriaBuilder() {
        return criteriaBuilder;
    }

    public AbstractQuery<T> query() {
        return query;
    }

    public Root<T> root() {
        return root;
    }

}
