package com.netappsid.jpaquery.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupingCondition implements Condition {

	private final Condition condition;

	public GroupingCondition(Condition condition) {
		this.condition = condition;
	}

	@Override
	public String createQueryFragment(QueryBuilder queryBuilder, AtomicInteger incrementor) {
		return "( " + condition.createQueryFragment(queryBuilder, incrementor) + " )";
	}

	@Override
	public List<Parameter> getParameters() {
		return condition.getParameters();
	}

}