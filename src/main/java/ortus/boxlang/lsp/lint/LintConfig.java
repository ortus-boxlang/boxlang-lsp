package ortus.boxlang.lsp.lint;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import ortus.boxlang.lsp.config.annotation.ConfigGroup;
import ortus.boxlang.lsp.config.annotation.ConfigSetting;

/** Root configuration loaded from .bxlint.json */
@ConfigGroup( configFile = ".bxlint.json", title = "Lint Configuration", description = "Place .bxlint.json at the workspace root to control static analysis. Changes are detected and applied live without reloading the editor." )
public class LintConfig {

	/** Map of rule id -> settings */
	@ConfigSetting( key = "diagnostics", type = "object{}", description = "Map of rule ID to rule settings. Keys are rule IDs (see Lint Rules section). Each value is an object with optional 'enabled' (boolean) and 'severity' (string) fields.", defaultValue = "{}" )
	public Map<String, RuleSettings>	diagnostics	= Collections.emptyMap();

	/** Optional list of include glob patterns (workspace-relative). If empty -> include all. */
	@ConfigSetting( type = "string[]", description = "Workspace-relative glob patterns. When non-empty, only matching files are analyzed. Supports * (segment), ** (recursive), ? (single char). Always use forward slashes.", defaultValue = "[]" )
	public List<String>					include		= Collections.emptyList();

	/** Optional list of exclude glob patterns (workspace-relative). Applied after include. */
	@ConfigSetting( type = "string[]", description = "Workspace-relative glob patterns. Files matching any exclude pattern are never analyzed, even if they match an include pattern. Evaluated after include.", defaultValue = "[]" )
	public List<String>					exclude		= Collections.emptyList();

	@ConfigSetting( type = "object", description = "Formatting configuration shared across IDEs for the workspace. The experimental formatter toggle lives under formatting.experimental.enabled.", defaultValue = "{}" )
	public FormattingConfig				formatting	= new FormattingConfig();

	public static class FormattingConfig {

		public ExperimentalFormattingConfig experimental = new ExperimentalFormattingConfig();
	}

	public static class ExperimentalFormattingConfig {

		public Boolean enabled = null;
	}

	public RuleSettings forRule( String id ) {
		return diagnostics == null ? null : diagnostics.get( id );
	}

	/**
	 * Determines if a relative path (workspace-relative with forward slashes) should be analyzed based on includes/excludes.
	 * Rules:
	 * - If include list is empty -> implicitly included.
	 * - Else must match at least one include pattern.
	 * - If matches any exclude pattern -> excluded.
	 */
	public boolean shouldAnalyze( String relativePath ) {
		Objects.requireNonNull( relativePath, "relativePath" );
		String	rel			= normalize( relativePath );
		boolean	included	= include != null && !include.isEmpty() && include.stream().anyMatch( p -> compile( p ).matcher( rel ).matches() );
		if ( included ) {
			return true;
		}

		boolean excluded = exclude != null && exclude.stream().anyMatch( p -> compile( p ).matcher( rel ).matches() );
		return !excluded;
	}

	private static String normalize( String p ) {
		return p.replace( '\\', '/' );
	}

	private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

	private static Pattern compile( String glob ) {
		return PATTERN_CACHE.computeIfAbsent( glob, LintConfig::globToRegex );
	}

	private static Pattern globToRegex( String glob ) {
		String			g	= normalize( glob );
		StringBuilder	sb	= new StringBuilder();
		sb.append( '^' );
		char[]	chars	= g.toCharArray();
		int		i		= 0;
		while ( i < chars.length ) {
			char c = chars[ i ];
			if ( c == '*' ) {
				boolean doubleStar = ( i + 1 < chars.length && chars[ i + 1 ] == '*' );
				if ( doubleStar ) {
					// ** => match any chars across directories
					sb.append( ".*" );
					i += 2;
					continue;
				} else {
					sb.append( "[^/]*" );
					i++;
					continue;
				}
			} else if ( c == '?' ) {
				sb.append( '.' );
			} else if ( ".^$+{}[]|()".indexOf( c ) >= 0 ) {
				sb.append( '\\' ).append( c );
			} else {
				sb.append( c );
			}
			i++;
		}
		sb.append( '$' );
		return Pattern.compile( sb.toString() );
	}
}
