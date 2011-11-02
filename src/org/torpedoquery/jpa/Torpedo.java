/**
 *
 *   Copyright 2011 Xavier Jodoin xjodoin@gmail.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.torpedoquery.jpa;

import static org.torpedoquery.jpa.internal.TorpedoMagic.*;

import java.util.Collection;
import java.util.Map;

import org.torpedoquery.jpa.internal.ArrayCallHandler;
import org.torpedoquery.jpa.internal.ArrayCallHandler.ValueHandler;
import org.torpedoquery.jpa.internal.AscFunctionHandler;
import org.torpedoquery.jpa.internal.AvgFunctionHandler;
import org.torpedoquery.jpa.internal.CoalesceFunction;
import org.torpedoquery.jpa.internal.ComparableConstantFunctionHandler;
import org.torpedoquery.jpa.internal.ConstantFunctionHandler;
import org.torpedoquery.jpa.internal.CountFunctionHandler;
import org.torpedoquery.jpa.internal.DescFunctionHandler;
import org.torpedoquery.jpa.internal.DistinctFunctionHandler;
import org.torpedoquery.jpa.internal.DoNothingQueryConfigurator;
import org.torpedoquery.jpa.internal.GroupBy;
import org.torpedoquery.jpa.internal.GroupingConditionHandler;
import org.torpedoquery.jpa.internal.InnerJoinHandler;
import org.torpedoquery.jpa.internal.LeftJoinHandler;
import org.torpedoquery.jpa.internal.MaxFunctionHandler;
import org.torpedoquery.jpa.internal.MinFunctionHandler;
import org.torpedoquery.jpa.internal.MultiClassLoaderProvider;
import org.torpedoquery.jpa.internal.Proxy;
import org.torpedoquery.jpa.internal.ProxyFactoryFactory;
import org.torpedoquery.jpa.internal.QueryBuilder;
import org.torpedoquery.jpa.internal.RightJoinHandler;
import org.torpedoquery.jpa.internal.Selector;
import org.torpedoquery.jpa.internal.SumFunctionHandler;
import org.torpedoquery.jpa.internal.TorpedoMethodHandler;
import org.torpedoquery.jpa.internal.WhereClauseHandler;
import org.torpedoquery.jpa.internal.WhereQueryConfigurator;
import org.torpedoquery.jpa.internal.WithQueryConfigurator;

/**
 * Torpedo Query goal is to simplify how you create and maintain your HQL query.
 * (http://docs.jboss.org/hibernate/core/3.3/reference/en/html/queryhql.html)
 * 
 * (All following examples are extract from Torpedo's Tests cases)
 * 
 * First add this import static org.torpedoquery.jpa.Torpedo.*;
 * 
 * 1. Create simple select
 * 
 * final Entity entity = from(Entity.class); org.torpedoquery.jpa.Query<Entity>
 * select = select(entity);
 * 
 * 2. Create scalar queries
 * 
 * final Entity entity = from(Entity.class); org.torpedoquery.jpa.Query<String>
 * select = select(entity.getCode());
 * 
 * 3. How to execute your query
 * 
 * final Entity entity = from(Entity.class); org.torpedoquery.jpa.Query<Entity>
 * select = select(entity); List<Entity> entityList =
 * select.list(entityManager);
 * 
 * 4. Create simple condition
 * 
 * final Entity entity = from(Entity.class);
 * where(entity.getCode()).eq("mycode"); org.torpedoquery.jpa.Query<Entity>
 * select = select(entity);
 * 
 * 5. Create join on your entities
 * 
 * final Entity entity = from(Entity.class); final SubEntity subEntity =
 * innerJoin(entity.getSubEntities()); org.torpedoquery.jpa.Query<String[]>
 * select = select(entity.getCode(), subEntity.getName());
 * 
 * 6. Group your conditions
 * 
 * Entity from = from(Entity.class); OnGoingLogicalCondition condition =
 * condition(from.getCode()).eq("test").or(from.getCode()).eq("test2");
 * where(from.getName()).eq("test").and(condition); Query<Entity> select =
 * select(from);
 * 
 * 
 */
public class Torpedo {

	private static final ProxyFactoryFactory proxyFactoryFactory = new ProxyFactoryFactory(new MultiClassLoaderProvider());

	/**
	 * 
	 * @param your
	 *            entity class you want to create your query
	 * @return a mock object, it serve to create your create your query
	 * 
	 */
	public static <T> T from(Class<T> toQuery) {
		try {

			QueryBuilder queryBuilder = new QueryBuilder(toQuery);
			TorpedoMethodHandler fjpaMethodHandler = new TorpedoMethodHandler(queryBuilder, proxyFactoryFactory);

			T from = proxyFactoryFactory.createProxy(fjpaMethodHandler, Proxy.class, toQuery);

			fjpaMethodHandler.addQueryBuilder(from, queryBuilder);

			setQuery((Proxy) from);

			return from;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * In HQL you can specify field is only in subclass
	 * 
	 * Entity from = from(Entity.class); ExtendEntity extend = extend(from,
	 * ExtendEntity.class); where(extend.getSpecificField()).eq("test");
	 * 
	 * @param toExtend
	 * @param subclass
	 * @return
	 */
	public static <T, E extends T> E extend(T toExtend, Class<E> subclass) {
		try {

			TorpedoMethodHandler fjpaMethodHandler = getTorpedoMethodHandler();
			E proxy = proxyFactoryFactory.createProxy(fjpaMethodHandler, Proxy.class, subclass);

			QueryBuilder queryBuilder = fjpaMethodHandler.getQueryBuilder(toExtend);
			fjpaMethodHandler.addQueryBuilder(proxy, queryBuilder);

			return proxy;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> Query<T> select(Function<T> value) {
		return (Query<T>) Torpedo.select(new Object[] { value });
	}

	public static <T> Query<T> select(T value) {
		return (Query<T>) Torpedo.select(new Object[] { value });
	}

	public static <T> Query<T[]> select(Function<T>... values) {
		return select((T[]) values);
	}

	public static <T> Query<T[]> select(T... values) {
		TorpedoMethodHandler methodHandler = getTorpedoMethodHandler();

		for (int i = 0; i < values.length; i++) {

			Object param = values[i];

			if (param instanceof Function) {
				Function function = (Function) values[i];
				Proxy proxy = (Proxy) function.getProxy();
				methodHandler = proxy.getTorpedoMethodHandler();
			} else if (param instanceof Proxy) {
				Proxy proxy = (Proxy) param;
				methodHandler = proxy.getTorpedoMethodHandler();
			}

		}

		final QueryBuilder<T[]> root = methodHandler.getRoot();

		methodHandler.handle(new ArrayCallHandler(new ValueHandler() {

			@Override
			public void handle(Proxy query, QueryBuilder queryBuilder, Selector selector) {
				root.addSelector(selector);
			}
		}, values));

		return root;

	}

	public static <T> T innerJoin(T toJoin) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E innerJoin(T toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T innerJoin(Collection<T> toJoin) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E innerJoin(Collection<T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T innerJoin(Map<?, T> toJoin) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E innerJoin(Map<?, T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new InnerJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T leftJoin(T toJoin) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E leftJoin(T toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T leftJoin(Collection<T> toJoin) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E leftJoin(Collection<T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T leftJoin(Map<?, T> toJoin) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E leftJoin(Map<?, T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new LeftJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T rightJoin(T toJoin) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E rightJoin(T toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T rightJoin(Collection<T> toJoin) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E rightJoin(Collection<T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> T rightJoin(Map<?, T> toJoin) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<T>(getTorpedoMethodHandler(), proxyFactoryFactory));
	}

	public static <T, E extends T> E rightJoin(Map<?, T> toJoin, Class<E> realType) {
		return getTorpedoMethodHandler().handle(new RightJoinHandler<E>(getTorpedoMethodHandler(), proxyFactoryFactory, realType));
	}

	public static <T> OnGoingLogicalCondition where(OnGoingLogicalCondition condition) {
		return getTorpedoMethodHandler().handle(new GroupingConditionHandler<T>(new WhereQueryConfigurator<T>(), condition));
	}

	public static <T> ValueOnGoingCondition<T> where(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, ValueOnGoingCondition<T>>());
	}

	public static <V, T extends Comparable<V>> OnGoingComparableCondition<V> where(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<V, OnGoingComparableCondition<V>>());
	}

	public static OnGoingStringCondition<String> where(String object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<String, OnGoingStringCondition<String>>());
	}

	public static <T> OnGoingCollectionCondition<T> where(Collection<T> object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, OnGoingCollectionCondition<T>>(new WhereQueryConfigurator<T>()));
	}

	public static <T> OnGoingComparableCondition<T> where(ComparableFunction<T> object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, OnGoingComparableCondition<T>>(object, null, new WhereQueryConfigurator<T>()));
	}

	public static <T> ValueOnGoingCondition<T> with(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, ValueOnGoingCondition<T>>(new WithQueryConfigurator<T>()));
	}

	public static <V, T extends Comparable<V>> OnGoingComparableCondition<V> with(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<V, OnGoingComparableCondition<V>>(new WithQueryConfigurator<V>()));
	}

	public static OnGoingStringCondition<String> with(String object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<String, OnGoingStringCondition<String>>(new WithQueryConfigurator<String>()));
	}

	public static <T> OnGoingCollectionCondition<T> with(Collection<T> object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, OnGoingCollectionCondition<T>>(new WithQueryConfigurator<T>()));
	}

	public static <T> OnGoingLogicalCondition with(OnGoingLogicalCondition condition) {
		return getTorpedoMethodHandler().handle(new GroupingConditionHandler<T>(new WithQueryConfigurator<T>(), condition));
	}

	public static <T> ValueOnGoingCondition<T> condition(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, ValueOnGoingCondition<T>>(new DoNothingQueryConfigurator<T>()));
	}

	public static <V, T extends Comparable<V>> OnGoingComparableCondition<V> condition(T object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<V, OnGoingComparableCondition<V>>(new DoNothingQueryConfigurator<V>()));
	}

	public static OnGoingStringCondition<String> condition(String object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<String, OnGoingStringCondition<String>>(new DoNothingQueryConfigurator<String>()));
	}

	public static <T> OnGoingCollectionCondition<T> condition(Collection<T> object) {
		return getTorpedoMethodHandler().handle(new WhereClauseHandler<T, OnGoingCollectionCondition<T>>(new DoNothingQueryConfigurator<T>()));
	}

	public static <T> OnGoingLogicalCondition condition(OnGoingLogicalCondition condition) {
		return getTorpedoMethodHandler().handle(new GroupingConditionHandler<T>(new DoNothingQueryConfigurator<T>(), condition));
	}

	public static OnGoingGroupByCondition groupBy(Object... values) {

		TorpedoMethodHandler fjpaMethodHandler = getTorpedoMethodHandler();
		final QueryBuilder root = fjpaMethodHandler.getRoot();
		final GroupBy groupBy = new GroupBy();

		fjpaMethodHandler.handle(new ArrayCallHandler(new ValueHandler() {
			@Override
			public void handle(Proxy proxy, QueryBuilder queryBuilder, Selector selector) {
				groupBy.addGroup(selector);
			}
		}, values));

		root.setGroupBy(groupBy);
		return groupBy;
	}

	// JPA Functions
	public static Function<Long> count(Object object) {
		if (object instanceof Proxy) {
			setQuery((Proxy) object);
		}
		return getTorpedoMethodHandler().handle(new CountFunctionHandler(object instanceof Proxy ? (Proxy) object : null));
	}

	public static <V, T extends Comparable<V>> ComparableFunction<V> sum(T number) {
		return getTorpedoMethodHandler().handle(new SumFunctionHandler<V>());
	}

	public static <V, T extends Comparable<V>> ComparableFunction<V> min(T number) {
		return getTorpedoMethodHandler().handle(new MinFunctionHandler<V>());
	}

	public static <V, T extends Comparable<V>> ComparableFunction<V> max(T number) {
		return getTorpedoMethodHandler().handle(new MaxFunctionHandler<V>());
	}

	public static <V, T extends Comparable<V>> ComparableFunction<V> avg(T number) {
		return getTorpedoMethodHandler().handle(new AvgFunctionHandler<V>());
	}

	public static <T, E extends Function<T>> E coalesce(E... values) {
		CoalesceFunction<E> coalesceFunction = getCoalesceFunction(values);
		return (E) coalesceFunction;
	}

	public static <T> Function<T> coalesce(T... values) {
		final CoalesceFunction<T> coalesceFunction = getCoalesceFunction(values);
		return coalesceFunction;
	}

	private static <T> CoalesceFunction<T> getCoalesceFunction(T... values) {
		final CoalesceFunction coalesceFunction = new CoalesceFunction();
		getTorpedoMethodHandler().handle(new ArrayCallHandler(new ValueHandler() {
			@Override
			public void handle(Proxy proxy, QueryBuilder queryBuilder, Selector selector) {
				coalesceFunction.setQuery(proxy);
				coalesceFunction.addSelector(selector);
			}
		}, values));
		return coalesceFunction;
	}

	public static <T> Function<T> distinct(T object) {
		if (object instanceof Proxy) {
			setQuery((Proxy) object);
		}
		return getTorpedoMethodHandler().handle(new DistinctFunctionHandler<T>(object));
	}

	public static <T> Function<T> constant(T constant) {
		return getTorpedoMethodHandler().handle(new ConstantFunctionHandler<T>(constant));
	}

	public static <V, T extends Comparable<V>> ComparableFunction<T> constant(T constant) {
		return getTorpedoMethodHandler().handle(new ComparableConstantFunctionHandler<T>(constant));
	}

	public static <T> ComparableFunction<Integer> index(T object) {
		if (object instanceof Proxy) {
			setQuery((Proxy) object);
		}
		return getTorpedoMethodHandler().handle(new IndexFunctionHandler(object));
	}

	public static void orderBy(Object... values) {
		getTorpedoMethodHandler().handle(new ArrayCallHandler(new ValueHandler() {
			@Override
			public void handle(Proxy proxy, QueryBuilder queryBuilder, Selector selector) {
				queryBuilder.addOrder(selector);
			}
		}, values));

	}

	// orderBy function

	public static <T> Function<T> asc(T object) {
		return getTorpedoMethodHandler().handle(new AscFunctionHandler<T>());
	}

	public static <T> Function<T> desc(T object) {
		return getTorpedoMethodHandler().handle(new DescFunctionHandler<T>());
	}

}
