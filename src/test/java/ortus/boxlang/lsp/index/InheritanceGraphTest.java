package ortus.boxlang.lsp.index;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.index.InheritanceGraph;

class InheritanceGraphTest {

	private InheritanceGraph graph;

	@BeforeEach
	void setUp() {
		graph = new InheritanceGraph();
	}

	@Test
	void testAddClassRelationshipWithParent() {
		graph.addClassRelationship( "models.User", "models.BaseEntity", List.of() );

		assertEquals( "models.BaseEntity", graph.getParent( "models.User" ) );
		assertThat( graph.getChildren( "models.BaseEntity" ) ).contains( "models.User" );
	}

	@Test
	void testAddClassRelationshipWithInterfaces() {
		graph.addClassRelationship( "models.User", null, List.of( "ISerializable", "IComparable" ) );

		Set<String> interfaces = graph.getInterfaces( "models.User" );
		assertThat( interfaces ).containsExactly( "ISerializable", "IComparable" );
	}

	@Test
	void testAddClassRelationshipWithParentAndInterfaces() {
		graph.addClassRelationship( "models.User", "models.BaseEntity", List.of( "ISerializable" ) );

		assertEquals( "models.BaseEntity", graph.getParent( "models.User" ) );
		assertThat( graph.getInterfaces( "models.User" ) ).contains( "ISerializable" );
	}

	@Test
	void testGetAncestors() {
		// Create a hierarchy: GrandChild -> Child -> Parent -> GrandParent
		graph.addClassRelationship( "GrandParent", null, List.of() );
		graph.addClassRelationship( "Parent", "GrandParent", List.of() );
		graph.addClassRelationship( "Child", "Parent", List.of() );
		graph.addClassRelationship( "GrandChild", "Child", List.of() );

		List<String> ancestors = graph.getAncestors( "GrandChild" );

		assertThat( ancestors ).containsExactly( "Child", "Parent", "GrandParent" ).inOrder();
	}

	@Test
	void testGetAncestorsWithNoParent() {
		graph.addClassRelationship( "RootClass", null, List.of() );

		List<String> ancestors = graph.getAncestors( "RootClass" );

		assertThat( ancestors ).isEmpty();
	}

	@Test
	void testGetDescendants() {
		// Create a hierarchy with multiple children
		graph.addClassRelationship( "Parent", null, List.of() );
		graph.addClassRelationship( "Child1", "Parent", List.of() );
		graph.addClassRelationship( "Child2", "Parent", List.of() );
		graph.addClassRelationship( "GrandChild1", "Child1", List.of() );

		List<String> descendants = graph.getDescendants( "Parent" );

		assertThat( descendants ).containsExactly( "Child1", "Child2", "GrandChild1" );
	}

	@Test
	void testGetDescendantsWithNoChildren() {
		graph.addClassRelationship( "LeafClass", "Parent", List.of() );

		List<String> descendants = graph.getDescendants( "LeafClass" );

		assertThat( descendants ).isEmpty();
	}

	@Test
	void testGetChildren() {
		graph.addClassRelationship( "Parent", null, List.of() );
		graph.addClassRelationship( "Child1", "Parent", List.of() );
		graph.addClassRelationship( "Child2", "Parent", List.of() );

		Set<String> children = graph.getChildren( "Parent" );

		assertThat( children ).containsExactly( "Child1", "Child2" );
	}

	@Test
	void testGetImplementors() {
		graph.addClassRelationship( "UserService", null, List.of( "IService" ) );
		graph.addClassRelationship( "ProductService", null, List.of( "IService" ) );
		graph.addClassRelationship( "OrderService", null, List.of( "IService", "ITransactional" ) );

		List<String> implementors = graph.getImplementors( "IService" );

		assertThat( implementors ).containsExactly( "UserService", "ProductService", "OrderService" );
	}

	@Test
	void testGetImplementorsForUnknownInterface() {
		List<String> implementors = graph.getImplementors( "UnknownInterface" );

		assertThat( implementors ).isEmpty();
	}

	@Test
	void testIsSubclassOf() {
		graph.addClassRelationship( "GrandParent", null, List.of() );
		graph.addClassRelationship( "Parent", "GrandParent", List.of() );
		graph.addClassRelationship( "Child", "Parent", List.of() );

		assertTrue( graph.isSubclassOf( "Child", "Parent" ) );
		assertTrue( graph.isSubclassOf( "Child", "GrandParent" ) );
		assertFalse( graph.isSubclassOf( "Parent", "Child" ) );
		assertFalse( graph.isSubclassOf( "Child", "UnknownClass" ) );
	}

	@Test
	void testImplementsInterface() {
		graph.addClassRelationship( "UserService", null, List.of( "IService", "ILoggable" ) );

		assertTrue( graph.implementsInterface( "UserService", "IService" ) );
		assertTrue( graph.implementsInterface( "UserService", "ILoggable" ) );
		assertFalse( graph.implementsInterface( "UserService", "ITransactional" ) );
	}

	@Test
	void testRemoveClass() {
		graph.addClassRelationship( "Parent", null, List.of() );
		graph.addClassRelationship( "Child", "Parent", List.of( "IService" ) );

		// Verify relationships exist
		assertEquals( "Parent", graph.getParent( "Child" ) );
		assertThat( graph.getChildren( "Parent" ) ).contains( "Child" );
		assertThat( graph.getImplementors( "IService" ) ).contains( "Child" );

		// Remove the child class
		graph.removeClass( "Child" );

		// Verify relationships are cleaned up
		assertNull( graph.getParent( "Child" ) );
		assertThat( graph.getChildren( "Parent" ) ).doesNotContain( "Child" );
		assertThat( graph.getImplementors( "IService" ) ).doesNotContain( "Child" );
	}

	@Test
	void testRemoveParentClass() {
		graph.addClassRelationship( "Parent", null, List.of() );
		graph.addClassRelationship( "Child1", "Parent", List.of() );
		graph.addClassRelationship( "Child2", "Parent", List.of() );

		// Remove the parent class
		graph.removeClass( "Parent" );

		// Children should no longer have parent reference
		assertNull( graph.getParent( "Child1" ) );
		assertNull( graph.getParent( "Child2" ) );
	}

	@Test
	void testClear() {
		graph.addClassRelationship( "Parent", null, List.of() );
		graph.addClassRelationship( "Child", "Parent", List.of( "IService" ) );

		graph.clear();

		assertNull( graph.getParent( "Child" ) );
		assertThat( graph.getChildren( "Parent" ) ).isEmpty();
		assertThat( graph.getImplementors( "IService" ) ).isEmpty();
	}

	@Test
	void testCircularReferenceProtection() {
		// This shouldn't cause infinite loop
		graph.addClassRelationship( "A", "B", List.of() );
		graph.addClassRelationship( "B", "A", List.of() );

		// Should not hang, and should return limited results
		List<String> ancestors = graph.getAncestors( "A" );
		assertThat( ancestors ).isNotEmpty();
	}

	@Test
	void testEmptyInputs() {
		// Should handle empty gracefully - these should not throw or cause issues
		graph.addClassRelationship( "", "Parent", List.of() );
		graph.addClassRelationship( "Child", null, null );
		graph.addClassRelationship( "ValidClass", "", List.of() );

		// Should not throw for empty string
		graph.removeClass( "" );

		// Empty set for empty string children lookup
		assertThat( graph.getChildren( "" ) ).isEmpty();

		// Verify valid operations still work
		graph.addClassRelationship( "RealChild", "RealParent", List.of( "IService" ) );
		assertEquals( "RealParent", graph.getParent( "RealChild" ) );
		assertThat( graph.getImplementors( "IService" ) ).contains( "RealChild" );
	}
}
