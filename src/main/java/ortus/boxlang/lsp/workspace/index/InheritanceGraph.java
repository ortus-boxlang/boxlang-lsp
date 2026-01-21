package ortus.boxlang.lsp.workspace.index;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks class inheritance hierarchies for the project index.
 * Provides methods to query parent/child relationships and interface implementations.
 * Thread-safe for concurrent access during workspace parsing.
 */
public class InheritanceGraph {

	private final Map<String, String>		parentClass				= new ConcurrentHashMap<>();
	private final Map<String, Set<String>>	childClasses			= new ConcurrentHashMap<>();
	private final Map<String, Set<String>>	interfaceImplementors	= new ConcurrentHashMap<>();
	private final Map<String, Set<String>>	classInterfaces			= new ConcurrentHashMap<>();

	/**
	 * Add a class relationship to the graph.
	 *
	 * @param classFQN   The fully qualified name of the class
	 * @param parentFQN  The fully qualified name of the parent class (nullable)
	 * @param interfaces List of interface FQNs that the class implements
	 */
	public void addClassRelationship( String classFQN, String parentFQN, List<String> interfaces ) {
		if ( classFQN == null || classFQN.isEmpty() ) {
			return;
		}

		// Track parent-child relationship
		if ( parentFQN != null && !parentFQN.isEmpty() ) {
			parentClass.put( classFQN, parentFQN );
			childClasses.computeIfAbsent( parentFQN, k -> ConcurrentHashMap.newKeySet() ).add( classFQN );
		}

		// Track interface implementations
		if ( interfaces != null && !interfaces.isEmpty() ) {
			classInterfaces.put( classFQN, new HashSet<>( interfaces ) );
			for ( String iface : interfaces ) {
				interfaceImplementors.computeIfAbsent( iface, k -> ConcurrentHashMap.newKeySet() ).add( classFQN );
			}
		}
	}

	/**
	 * Remove a class from the graph, cleaning up all relationships.
	 *
	 * @param classFQN The fully qualified name of the class to remove
	 */
	public void removeClass( String classFQN ) {
		if ( classFQN == null || classFQN.isEmpty() ) {
			return;
		}

		// Remove from parent's children
		String parent = parentClass.remove( classFQN );
		if ( parent != null ) {
			Set<String> siblings = childClasses.get( parent );
			if ( siblings != null ) {
				siblings.remove( classFQN );
			}
		}

		// Remove as a parent
		Set<String> children = childClasses.remove( classFQN );
		if ( children != null ) {
			for ( String child : children ) {
				parentClass.remove( child );
			}
		}

		// Remove interface implementations
		Set<String> interfaces = classInterfaces.remove( classFQN );
		if ( interfaces != null ) {
			for ( String iface : interfaces ) {
				Set<String> implementors = interfaceImplementors.get( iface );
				if ( implementors != null ) {
					implementors.remove( classFQN );
				}
			}
		}
	}

	/**
	 * Get the immediate parent class.
	 *
	 * @param classFQN The class to query
	 *
	 * @return The parent class FQN, or null if none
	 */
	public String getParent( String classFQN ) {
		return parentClass.get( classFQN );
	}

	/**
	 * Get all ancestors of a class (parent, grandparent, etc.).
	 * Returns them in order from immediate parent to root.
	 *
	 * @param classFQN The class to query
	 *
	 * @return List of ancestor FQNs in order from nearest to farthest
	 */
	public List<String> getAncestors( String classFQN ) {
		List<String>	ancestors	= new ArrayList<>();
		Set<String>		visited		= new HashSet<>();
		String			current		= classFQN;

		while ( current != null ) {
			String parent = parentClass.get( current );
			if ( parent != null && !visited.contains( parent ) ) {
				ancestors.add( parent );
				visited.add( parent );
				current = parent;
			} else {
				break;
			}
		}

		return ancestors;
	}

	/**
	 * Get all descendants of a class (children, grandchildren, etc.).
	 *
	 * @param classFQN The class to query
	 *
	 * @return List of all descendant FQNs
	 */
	public List<String> getDescendants( String classFQN ) {
		List<String>	descendants	= new ArrayList<>();
		Set<String>		visited		= new HashSet<>();
		collectDescendants( classFQN, descendants, visited );
		return descendants;
	}

	private void collectDescendants( String classFQN, List<String> descendants, Set<String> visited ) {
		Set<String> children = childClasses.get( classFQN );
		if ( children == null || children.isEmpty() ) {
			return;
		}

		for ( String child : children ) {
			if ( !visited.contains( child ) ) {
				visited.add( child );
				descendants.add( child );
				collectDescendants( child, descendants, visited );
			}
		}
	}

	/**
	 * Get immediate children of a class.
	 *
	 * @param classFQN The class to query
	 *
	 * @return Set of immediate child FQNs
	 */
	public Set<String> getChildren( String classFQN ) {
		Set<String> children = childClasses.get( classFQN );
		return children != null ? new HashSet<>( children ) : new HashSet<>();
	}

	/**
	 * Get all classes that implement a given interface.
	 *
	 * @param interfaceFQN The interface to query
	 *
	 * @return List of implementing class FQNs
	 */
	public List<String> getImplementors( String interfaceFQN ) {
		Set<String> implementors = interfaceImplementors.get( interfaceFQN );
		return implementors != null ? new ArrayList<>( implementors ) : new ArrayList<>();
	}

	/**
	 * Get all interfaces implemented by a class.
	 *
	 * @param classFQN The class to query
	 *
	 * @return Set of interface FQNs
	 */
	public Set<String> getInterfaces( String classFQN ) {
		Set<String> interfaces = classInterfaces.get( classFQN );
		return interfaces != null ? new HashSet<>( interfaces ) : new HashSet<>();
	}

	/**
	 * Check if a class extends another class (directly or indirectly).
	 *
	 * @param classFQN  The potential subclass
	 * @param parentFQN The potential parent class
	 *
	 * @return true if classFQN extends parentFQN
	 */
	public boolean isSubclassOf( String classFQN, String parentFQN ) {
		return getAncestors( classFQN ).contains( parentFQN );
	}

	/**
	 * Check if a class implements an interface.
	 *
	 * @param classFQN     The class to check
	 * @param interfaceFQN The interface to check
	 *
	 * @return true if the class implements the interface
	 */
	public boolean implementsInterface( String classFQN, String interfaceFQN ) {
		Set<String> interfaces = classInterfaces.get( classFQN );
		return interfaces != null && interfaces.contains( interfaceFQN );
	}

	/**
	 * Clear all data from the graph.
	 */
	public void clear() {
		parentClass.clear();
		childClasses.clear();
		interfaceImplementors.clear();
		classInterfaces.clear();
	}
}
