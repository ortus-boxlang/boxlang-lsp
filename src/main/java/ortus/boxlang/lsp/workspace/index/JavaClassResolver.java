package ortus.boxlang.lsp.workspace.index;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import ortus.boxlang.runtime.BoxRuntime;

/**
 * Reflection metadata for explicit Java references. Kept separate from the source index and its disk cache.
 */
public class JavaClassResolver {

	public static boolean isJavaClass( String name ) {
		return name != null && name.regionMatches( true, 0, "java:", 0, 5 );
	}

	private static Optional<Class<?>> load( String name ) {
		if ( !isJavaClass( name ) ) {
			return Optional.empty();
		}
		try {
			// Never run a project's static initializers while providing editor features.
			return Optional.of( Class.forName( name.substring( 5 ), false, BoxRuntime.getInstance().getRuntimeLoader() ) );
		} catch ( ClassNotFoundException | LinkageError | SecurityException e ) {
			return Optional.empty();
		}
	}

	public static Optional<IndexedClass> findClass( String name ) {
		try {
			return load( name ).map( clazz -> new IndexedClass(
			    clazz.getSimpleName(), javaName( clazz ), null, null,
			    clazz.getSuperclass() == null ? null : javaName( clazz.getSuperclass() ),
			    Arrays.stream( clazz.getInterfaces() ).map( JavaClassResolver::javaName ).toList(),
			    modifiers( clazz.getModifiers() & Modifier.classModifiers() ), clazz.isInterface(), null, null ) );
		} catch ( LinkageError | SecurityException e ) {
			return Optional.empty();
		}
	}

	public static List<IndexedMethod> getMethods( String name ) {
		try {
			return load( name ).map( clazz -> {
				List<Method> methods = new ArrayList<>( Arrays.asList( clazz.getMethods() ) );
				for ( Class<?> parent = clazz; parent != null; parent = parent.getSuperclass() ) {
					Arrays.stream( parent.getDeclaredMethods() )
					    .filter( method -> Modifier.isProtected( method.getModifiers() ) )
					    .forEach( methods::add );
				}
				var signatures = new HashSet<String>();
				return methods.stream()
				    .filter( method -> !method.isBridge() && !method.isSynthetic() )
				    .filter( method -> signatures.add( method.getName() + Arrays.toString( method.getParameterTypes() ) ) )
				    .sorted( Comparator.comparingInt( Method::getParameterCount ).thenComparing( Method::toString ) )
				    .map( method -> new IndexedMethod(
				        method.getName(), javaName( method.getDeclaringClass() ), null, null,
				        method.getReturnType().isPrimitive() ? method.getReturnType().getTypeName() : javaName( method.getReturnType() ),
				        Arrays.stream( method.getParameters() )
				            .map( param -> new IndexedParameter( param.getName(), param.getType().getTypeName(), true, null ) ).toList(),
				        Modifier.isProtected( method.getModifiers() ) ? "protected" : "public",
				        modifiers( method.getModifiers() & Modifier.methodModifiers() ), null ) )
				    .toList();
			} ).orElse( List.of() );
		} catch ( LinkageError | SecurityException e ) {
			return List.of();
		}
	}

	public static List<IndexedProperty> getProperties( String name ) {
		try {
			return load( name ).map( clazz -> Arrays.stream( clazz.getFields() )
			    .map( field -> new IndexedProperty( field.getName(), javaName( field.getDeclaringClass() ), null, null,
			        field.getType().getTypeName(), null, false, false ) )
			    .toList() ).orElse( List.of() );
		} catch ( LinkageError | SecurityException e ) {
			return List.of();
		}
	}

	private static String javaName( Class<?> clazz ) {
		return "java:" + clazz.getTypeName();
	}

	private static List<String> modifiers( int modifiers ) {
		String text = Modifier.toString( modifiers );
		return text.isEmpty() ? List.of() : List.of( text.split( " " ) );
	}
}
