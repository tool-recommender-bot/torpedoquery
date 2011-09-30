package org.torpedoquery.jpa.internal;

public class EqualCondition<T> extends SingleParameterCondition<T> {

	public EqualCondition(Selector selector, Parameter<T> parameter) {
		super(selector, parameter);
	}

	@Override
	protected String getComparator() {
		return "=";
	}

}
