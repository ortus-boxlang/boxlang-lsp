package ortus.boxlang.lsp.workspace;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ortus.boxlang.lsp.config.annotation.ConfigGroup;
import ortus.boxlang.lsp.config.annotation.ConfigSetting;

@ConfigGroup( configFile = "boxlang.json", title = "Project Mappings", description = "Place boxlang.json at the workspace root (or any ancestor directory) to define virtual paths, classpaths, and module directories. Supports // line comments." )
public class MappingConfig {

	@ConfigSetting( type = "object{}", description = "Map of virtual path prefix (e.g. \"/models\") to absolute or relative filesystem path. Supports ${user-dir}, ${boxlang-home}, and ${env.VAR:default} variable expansion.", defaultValue = "{}" )
	private final Map<String, Path>	mappings;

	@ConfigSetting( type = "string[]", description = "List of directories to include in the classpath for type resolution. Paths may be absolute or relative to the boxlang.json file.", defaultValue = "[]" )
	private final List<Path>		classPaths;

	@ConfigSetting( type = "string[]", description = "List of directories containing BoxLang modules. Defaults to boxlang_modules/ relative to boxlang.json. Paths may be absolute or relative.", defaultValue = "[\"boxlang_modules\"]" )
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
