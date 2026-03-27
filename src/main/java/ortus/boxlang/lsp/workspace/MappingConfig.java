package ortus.boxlang.lsp.workspace;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MappingConfig {

	private final Map<String, Path>	mappings;
	private final List<Path>		classPaths;
	private final List<Path>		modulesDirectory;
	private final Path				workspaceRoot;

	public MappingConfig( Map<String, Path> mappings, List<Path> classPaths, List<Path> modulesDirectory, Path workspaceRoot ) {
		this.mappings			= Collections.unmodifiableMap( mappings );
		this.classPaths			= Collections.unmodifiableList( classPaths );
		this.modulesDirectory	= Collections.unmodifiableList( modulesDirectory );
		this.workspaceRoot		= workspaceRoot;
	}

	public Map<String, Path> getMappings() {
		return mappings;
	}

	public List<Path> getClassPaths() {
		return classPaths;
	}

	public List<Path> getModulesDirectory() {
		return modulesDirectory;
	}

	public Path getWorkspaceRoot() {
		return workspaceRoot;
	}
}
