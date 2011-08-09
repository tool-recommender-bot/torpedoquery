package com.netappsid.jpaquery.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import com.netappsid.jpaquery.PostFunction;
import com.netappsid.jpaquery.Query;

public class QueryBuilder<T> implements Query<T> {
	private final Class<?> toQuery;
	private final List<Selector> toSelect = new ArrayList<Selector>();
	private final List<Join> joins = new ArrayList<Join>();
	private ConditionBuilder<?, ? extends Number> whereClause;

	private String freezeQuery;

	private String alias;
	private OrderBy orderBy;
	private GroupBy groupBy;

	public QueryBuilder(Class<?> toQuery) {
		this.toQuery = toQuery;
	}

	public String getQuery(AtomicInteger incrementor) {
		return freezeQuery(incrementor);
	}

	private String freezeQuery(AtomicInteger incrementor) {

		if (freezeQuery == null) {
			String from = " from " + toQuery.getSimpleName() + " " + getAlias(incrementor);
			StringBuilder builder = new StringBuilder();

			appendSelect(builder, incrementor);

			builder.append(from);

			builder.append(getJoins(incrementor));

			builder.append(appendWhereClause(new StringBuilder(), incrementor));

			builder.append(appendOrderBy(new StringBuilder(), incrementor));

			builder.append(appendGroupBy(new StringBuilder(), incrementor));

			freezeQuery = builder.toString().trim();

		}
		return freezeQuery;
	}

	@Override
	public String getQuery() {
		return getQuery(new AtomicInteger());
	}

	public String appendOrderBy(StringBuilder builder, AtomicInteger incrementor) {

		if (orderBy != null) {
			orderBy.createQueryFragment(builder, this, incrementor);
		}

		for (Join join : joins) {
			join.appendOrderBy(builder, incrementor);
		}

		return builder.toString();
	}

	public String appendGroupBy(StringBuilder builder, AtomicInteger incrementor) {

		if (groupBy != null) {
			groupBy.createQueryFragment(builder, incrementor);
		}

		for (Join join : joins) {
			join.appendGroupBy(builder, incrementor);
		}

		return builder.toString();
	}

	public StringBuilder appendWhereClause(StringBuilder builder, AtomicInteger incrementor) {

		Condition whereClauseCondition = getWhereClause();

		if (whereClauseCondition != null) {
			if (builder.length() == 0) {
				builder.append(" where ").append(whereClauseCondition.createQueryFragment(incrementor)).append(" ");
			} else {
				builder.append("and ").append(whereClauseCondition.createQueryFragment(incrementor)).append(" ");
			}
		}

		for (Join join : joins) {
			join.appendWhereClause(builder, incrementor);
		}

		return builder;
	}

	public void appendSelect(StringBuilder builder, AtomicInteger incrementor) {
		for (Selector selector : toSelect) {
			if (builder.length() == 0) {
				builder.append("select ").append(selector.createQueryFragment(incrementor));
			} else {
				builder.append(", ").append(selector.createQueryFragment(incrementor));
			}
		}
	}

	public String getAlias(AtomicInteger incrementor) {
		if (alias == null) {
			final char[] charArray = toQuery.getSimpleName().toCharArray();

			charArray[0] = Character.toLowerCase(charArray[0]);
			alias = new String(charArray) + "_" + incrementor.getAndIncrement();
		}
		return alias;
	}

	public void addSelector(Selector selector) {
		toSelect.add(selector);
	}

	public void addJoin(Join innerJoin) {
		joins.add(innerJoin);
	}

	public boolean hasSubJoin() {
		return !joins.isEmpty();
	}

	public String getJoins(AtomicInteger incrementor) {

		StringBuilder builder = new StringBuilder();

		for (Join join : joins) {
			builder.append(join.getJoin(getAlias(incrementor), incrementor));
		}

		return builder.toString();
	}

	public void setWhereClause(ConditionBuilder<?, ? extends Number> whereClause) {

		if (this.whereClause != null) {
			throw new IllegalArgumentException("You cannot have more than one WhereClause by query");
		}

		this.whereClause = whereClause;
	}

	public Condition getWhereClause() {
		if (whereClause != null) {
			return whereClause.getLogicalCondition() != null ? whereClause.getLogicalCondition() : whereClause;
		}
		return null;
	}

	@Override
	public Map<String, Object> getParameters() {

		freezeQuery(new AtomicInteger());

		Map<String, Object> params = new HashMap<String, Object>();
		List<ValueParameter> parameters = getValueParameters();
		for (ValueParameter parameter : parameters) {
			params.put(parameter.getName(), parameter.getValue());
		}
		return params;
	}

	public List<ValueParameter> getValueParameters() {
		List<ValueParameter> valueParameters = new ArrayList<ValueParameter>();

		Condition whereClauseCondition = getWhereClause();

		if (whereClauseCondition != null) {
			List<Parameter> parameters = whereClauseCondition.getParameters();
			for (Parameter parameter : parameters) {
				if (parameter instanceof ValueParameter) {
					valueParameters.add((ValueParameter) parameter);
				}
			}
		}

		for (Join join : joins) {
			List<ValueParameter> params = join.getParams();
			valueParameters.addAll(params);
		}

		return valueParameters;
	}

	public void addOrder(Selector selector) {
		if (orderBy == null) {
			orderBy = new OrderBy();
		}

		orderBy.addOrder(selector);

	}

	public void setGroupBy(GroupBy groupBy) {
		this.groupBy = groupBy;
	}

	@Override
	public T get(EntityManager entityManager) {
		try {
			return (T) createJPAQuery(entityManager).getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	@Override
	public List<T> list(EntityManager entityManager) {
		return createJPAQuery(entityManager).getResultList();
	}

	@Override
	public <E> List<E> map(EntityManager entityManager, PostFunction<E, T> function) {
		List<T> toConvert = list(entityManager);
		List<E> result = new ArrayList<E>();

		for (T value : toConvert) {
			result.add(function.execute(value));
		}
		return result;
	}

	private javax.persistence.Query createJPAQuery(EntityManager entityManager) {
		final javax.persistence.Query query = entityManager.createQuery(getQuery(new AtomicInteger()));
		final Map<String, Object> parameters = getParameters();

		for (Entry<String, Object> parameter : parameters.entrySet()) {
			query.setParameter(parameter.getKey(), parameter.getValue());
		}

		return query;
	}

}
