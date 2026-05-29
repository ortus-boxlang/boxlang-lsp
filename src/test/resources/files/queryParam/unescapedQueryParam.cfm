<cfquery datasource="#thing#">
	SELECT * FROM items u
	WHERE u.code = '#paramRef#'
</cfquery>