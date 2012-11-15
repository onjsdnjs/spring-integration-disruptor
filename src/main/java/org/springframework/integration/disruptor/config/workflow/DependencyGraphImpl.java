package org.springframework.integration.disruptor.config.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.integration.disruptor.config.HandlerGroup;
import org.springframework.util.Assert;

final class DependencyGraphImpl<T> implements DependencyGraph<T> {

	private int size;
	private final List<List<Integer>> adjacencyList;
	private final Map<String, Integer> symbolTable;
	private final Map<Integer, String> inverseSymbolTable;
	private final Map<Integer, T> dataMap;

	public DependencyGraphImpl() {
		this.size = 0;
		this.adjacencyList = new ArrayList<List<Integer>>();
		this.symbolTable = new HashMap<String, Integer>();
		this.inverseSymbolTable = new HashMap<Integer, String>();
		this.dataMap = new HashMap<Integer, T>();
	}

	private DependencyGraphImpl(final int size, final List<List<Integer>> adjacencyList, final Map<String, Integer> symbolTable,
			final Map<Integer, String> inverseSymbolTable, final Map<Integer, T> dataMap) {
		this.size = size;
		this.adjacencyList = adjacencyList;
		this.symbolTable = symbolTable;
		this.inverseSymbolTable = inverseSymbolTable;
		this.dataMap = dataMap;
	}

	public List<String> getDependencies(final String depender) {
		final Integer dependerKey = this.symbolTable.get(depender);
		if (dependerKey == null) {
			return Collections.emptyList();
		}
		final List<Integer> dependees = this.adjacencyList.get(dependerKey);
		return this.toSymbolicNames(dependees);
	}

	public List<String> getOrphanDependencies() {
		final List<Integer> orphanKeys = new ArrayList<Integer>();
		for (int key = 0; key < this.adjacencyList.size(); key++) {
			final List<Integer> adjacentKeys = this.adjacencyList.get(key);
			if (adjacentKeys.isEmpty()) {
				orphanKeys.add(key);
			}
		}
		return this.toSymbolicNames(orphanKeys);
	}

	public List<String> toSymbolicNames(final Iterable<Integer> keys) {
		final List<String> symbolicNames = new ArrayList<String>();
		for (final Integer dependee : keys) {
			symbolicNames.add(this.inverseSymbolTable.get(dependee));
		}
		return symbolicNames;
	}

	public DependencySetter addDependency(final String depender) {

		return new DependencySetter() {

			public void dependsOn(final String dependee) {
				final int dependerKey = DependencyGraphImpl.this.initAndGetKey(depender);
				final int dependeeKey = DependencyGraphImpl.this.initAndGetKey(dependee);
				DependencyGraphImpl.this.createDependency(dependerKey, dependeeKey);
			}

			public void dependsOnNone() {
				DependencyGraphImpl.this.initAndGetKey(depender);
			}

		};
	}

	private int initAndGetKey(final String depender) {
		final int dependerKey = DependencyGraphImpl.this.getKey(depender);
		DependencyGraphImpl.this.initAdjacencyListSlot(dependerKey);
		return dependerKey;
	}

	private void createDependency(final int dependerKey, final int dependeeKey) {
		this.adjacencyList.get(dependerKey).add(dependeeKey);
	}

	private void initAdjacencyListSlot(final int key) {
		if ((this.adjacencyList.size() <= key) || (this.adjacencyList.get(key) == null)) {
			this.adjacencyList.add(key, new ArrayList<Integer>());
		}
	}

	private int getKey(final String name) {
		if (this.symbolTable.containsKey(name)) {
			return this.symbolTable.get(name);
		} else {
			this.symbolTable.put(name, this.size);
			this.inverseSymbolTable.put(this.size, name);
			this.size++;
			return this.symbolTable.get(name);
		}
	}

	public interface DependencySetter {

		public void dependsOn(String dependee);

		public void dependsOnNone();

	}

	public int getSize() {
		return this.size;
	}

	public DependencyGraph<T> inverse() {
		final List<List<Integer>> inverseAdjacencyList = new ArrayList<List<Integer>>();
		for (int key = 0; key < this.adjacencyList.size(); key++) {
			inverseAdjacencyList.add(key, new ArrayList<Integer>());
		}
		for (int key = 0; key < this.adjacencyList.size(); key++) {
			for (final Integer adjacentKey : this.adjacencyList.get(key)) {
				inverseAdjacencyList.get(adjacentKey).add(key);
			}
		}
		return new DependencyGraphImpl<T>(this.size, inverseAdjacencyList, this.symbolTable, this.inverseSymbolTable, this.dataMap);
	}

	public void putData(final String name, final T data) {
		Assert.isTrue(this.symbolTable.containsKey(name), "No such node '" + name + "'");
		final Integer key = this.symbolTable.get(name);
		this.dataMap.put(key, data);
	}

	public T getData(final String name) {
		final Integer key = this.symbolTable.get(name);
		return key != null ? this.dataMap.get(key) : null;
	}

	public List<String> getSymbolicNames() {
		return new ArrayList<String>(this.symbolTable.keySet());
	}

	public List<Integer> adjacentKeys(final Integer key) {
		return this.adjacencyList.get(key);
	}

	public static <T> DependencyGraph<T> forHandlerGroups(final Iterable<HandlerGroup> handlerGroups) {
		final DependencyGraph<T> graph = new DependencyGraphImpl<T>();
		for (final HandlerGroup handlerGroup : handlerGroups) {
			for (final String dependency : handlerGroup.getDependencies()) {
				graph.addDependency(handlerGroup.getName()).dependsOn(dependency);
			}
		}
		return graph;
	}

}