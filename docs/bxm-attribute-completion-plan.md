# BXM Attribute Completion - Implementation Plan

## Overview

This document outlines the implementation plan for completing **Task 3.10: BXM Tags and Attributes Completion**. The tag name completion and enhanced documentation have been implemented. This plan covers the remaining work: **attribute name completion** and **attribute value completion**.

## Current State (Completed)

### What Works Now
- ✅ **Tag name completion** - typing `<bx:` shows all available BXM tags
- ✅ **Rich documentation** - each tag shows:
  - Tag type (requires body, allows body, or self-closing)
  - List of all attributes with required/optional status
  - Default values for optional attributes
  - Formatted in Markdown for readability

### Implementation
- **Location**: `src/main/java/ortus/boxlang/lsp/workspace/completion/ComponentCompletionRule.java`
- **Method**: `getCompletions()` provides tag completions
- **Documentation**: `formatComponentDocumentation()` generates rich Markdown docs
- **Data Source**: Dynamic lookup from BoxRuntime's `ComponentService`

### Tests
- **Location**: `src/test/java/ortus/boxlang/lsp/BxmTagCompletionTest.java`
- **Passing Tests** (6):
  - `testTagNameCompletion()` - verifies tag completion works
  - `testTagCompletionHasDocumentation()` - verifies docs are present
  - `testPartialTagNameCompletion()` - verifies partial matching
  - `testNoTagCompletionInScriptContext()` - verifies context detection
  - Additional basic tests
  
- **Future Tests** (4) - Written but currently failing as expected:
  - `testAttributeNameCompletion()` - attribute name suggestions
  - `testPartialAttributeNameCompletion()` - partial attribute matching
  - `testAttributeValueCompletion()` - value suggestions for attributes
  - `testRequiredAttributesPrioritized()` - required attrs shown first
  - `testSelfClosingTagAttributes()` - attrs for self-closing tags
  - `testAttributeCompletionKind()` - proper CompletionItemKind

## Remaining Work

### Phase 1: Attribute Name Completion

#### 1.1 Extend CompletionContext

**File**: `src/main/java/ortus/boxlang/lsp/workspace/completion/CompletionContext.java`

**Add new patterns:**
```java
// Pattern to detect attribute name completion: <bx:tagname attr|
public static final Pattern BXM_ATTR_NAME_PATTERN = Pattern.compile(
    "<bx:(\\w+)\\s+[^>]*?([\\w-]*)$"
);

// Pattern to detect attribute value completion: <bx:tagname attr="|
public static final Pattern BXM_ATTR_VALUE_PATTERN = Pattern.compile(
    "<bx:(\\w+)\\s+[^>]*?(\\w+)\\s*=\\s*[\"']([^\"']*)$"
);
```

**Add new context kinds:**
```java
public enum CompletionContextKind {
    // ... existing kinds ...
    BXM_TAG_ATTRIBUTE,       // For attribute name completion
    BXM_TAG_ATTRIBUTE_VALUE  // For attribute value completion
}
```

**Update `analyzeContext()` method:**
```java
// Add checks for attribute contexts BEFORE checking for tag context
// Order matters - more specific patterns first!

// Check for attribute value completion
Matcher attrValueMatcher = BXM_ATTR_VALUE_PATTERN.matcher(prefix);
if (attrValueMatcher.find()) {
    String tagName = attrValueMatcher.group(1);
    String attrName = attrValueMatcher.group(2);
    String partialValue = attrValueMatcher.group(3);
    
    return new CompletionContext(
        CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE,
        new HashMap<>() {{
            put("tagName", tagName);
            put("attributeName", attrName);
            put("partialValue", partialValue);
        }}
    );
}

// Check for attribute name completion
Matcher attrNameMatcher = BXM_ATTR_NAME_PATTERN.matcher(prefix);
if (attrNameMatcher.find()) {
    String tagName = attrNameMatcher.group(1);
    String partialAttr = attrNameMatcher.group(2);
    
    return new CompletionContext(
        CompletionContextKind.BXM_TAG_ATTRIBUTE,
        new HashMap<>() {{
            put("tagName", tagName);
            put("partialAttribute", partialAttr);
        }}
    );
}

// Existing BXM_TAG check comes after
```

#### 1.2 Create AttributeCompletionRule

**File**: `src/main/java/ortus/boxlang/lsp/workspace/completion/AttributeCompletionRule.java` (new file)

**Purpose**: Handle attribute name and value completions for BXM tags

**Key Methods**:

```java
public class AttributeCompletionRule implements CompletionRule {
    
    @Override
    public boolean appliesTo(CompletionContext context, DocumentModel doc) {
        if (!doc.getPath().toString().endsWith(".bxm")) {
            return false;
        }
        
        return context.getKind() == CompletionContextKind.BXM_TAG_ATTRIBUTE ||
               context.getKind() == CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE;
    }
    
    @Override
    public List<CompletionItem> getCompletions(CompletionContext context, DocumentModel doc) {
        if (context.getKind() == CompletionContextKind.BXM_TAG_ATTRIBUTE) {
            return getAttributeNameCompletions(context, doc);
        } else {
            return getAttributeValueCompletions(context, doc);
        }
    }
    
    private List<CompletionItem> getAttributeNameCompletions(
        CompletionContext context, 
        DocumentModel doc
    ) {
        String tagName = (String) context.getMetadata().get("tagName");
        
        // Get component descriptor from ComponentService
        ComponentDescriptor descriptor = getComponentDescriptor(tagName);
        if (descriptor == null) {
            return Collections.emptyList();
        }
        
        List<CompletionItem> items = new ArrayList<>();
        Attribute[] attributes = descriptor.getComponent().getDeclaredAttributes();
        
        // Sort: required first, then alphabetical
        Arrays.sort(attributes, (a, b) -> {
            boolean aRequired = Arrays.asList(a.validators()).contains(Validator.REQUIRED);
            boolean bRequired = Arrays.asList(b.validators()).contains(Validator.REQUIRED);
            
            if (aRequired != bRequired) {
                return aRequired ? -1 : 1;
            }
            return a.name().compareTo(b.name());
        });
        
        for (Attribute attr : attributes) {
            CompletionItem item = new CompletionItem();
            item.setLabel(attr.name());
            item.setKind(CompletionItemKind.Property);
            
            // Set insert text with snippet for value
            boolean isRequired = Arrays.asList(attr.validators()).contains(Validator.REQUIRED);
            if (isRequired) {
                item.setInsertText(attr.name() + "=\"$1\"$0");
                item.setInsertTextFormat(InsertTextFormat.Snippet);
            } else {
                item.setInsertText(attr.name() + "=\"\"");
            }
            
            // Build documentation
            item.setDocumentation(formatAttributeDocumentation(attr));
            
            // Prioritize required attributes
            if (isRequired) {
                item.setSortText("0_" + attr.name());
            } else {
                item.setSortText("1_" + attr.name());
            }
            
            items.add(item);
        }
        
        return items;
    }
    
    private List<CompletionItem> getAttributeValueCompletions(
        CompletionContext context,
        DocumentModel doc
    ) {
        String tagName = (String) context.getMetadata().get("tagName");
        String attrName = (String) context.getMetadata().get("attributeName");
        
        // Get component descriptor
        ComponentDescriptor descriptor = getComponentDescriptor(tagName);
        if (descriptor == null) {
            return Collections.emptyList();
        }
        
        // Find the specific attribute
        Attribute attr = findAttribute(descriptor, attrName);
        if (attr == null) {
            return Collections.emptyList();
        }
        
        // Extract possible values from validators
        return extractValueSuggestions(attr);
    }
    
    private MarkupContent formatAttributeDocumentation(Attribute attr) {
        StringBuilder doc = new StringBuilder();
        
        boolean isRequired = Arrays.asList(attr.validators()).contains(Validator.REQUIRED);
        
        doc.append("**").append(attr.name()).append("**");
        if (isRequired) {
            doc.append(" *(required)*");
        }
        doc.append("\n\n");
        
        // Add type information from validators
        List<String> validators = Arrays.stream(attr.validators())
            .map(v -> v.toString())
            .collect(Collectors.toList());
        
        if (!validators.isEmpty()) {
            doc.append("Validators: `").append(String.join(", ", validators)).append("`\n\n");
        }
        
        // Add default value
        Object defaultValue = attr.defaultValue();
        if (defaultValue != null) {
            doc.append("Default: `").append(defaultValue.toString()).append("`");
        }
        
        MarkupContent markup = new MarkupContent();
        markup.setKind(MarkupKind.MARKDOWN);
        markup.setValue(doc.toString());
        return markup;
    }
    
    private List<CompletionItem> extractValueSuggestions(Attribute attr) {
        List<CompletionItem> items = new ArrayList<>();
        
        // Check validators for enum-like values
        for (Validator validator : attr.validators()) {
            // BoxRuntime validators may include ONEOF, REGEX, etc.
            // This would need investigation into BoxRuntime's Validator API
            // to extract allowed values
            
            // Example pattern (pseudo-code):
            // if (validator instanceof OneOfValidator) {
            //     for (String value : validator.getAllowedValues()) {
            //         CompletionItem item = new CompletionItem();
            //         item.setLabel(value);
            //         item.setKind(CompletionItemKind.Value);
            //         items.add(item);
            //     }
            // }
        }
        
        // If no specific values found, check for common boolean attributes
        String attrName = attr.name().toLowerCase();
        if (attrName.contains("enabled") || attrName.contains("required") || 
            attrName.contains("async") || attrName.equals("throw")) {
            items.add(createValueCompletion("true"));
            items.add(createValueCompletion("false"));
        }
        
        return items;
    }
    
    private ComponentDescriptor getComponentDescriptor(String tagName) {
        // Use BoxRuntime's ComponentService
        // Similar to ComponentCompletionRule implementation
        BoxRuntime runtime = BoxRuntime.getInstance();
        ComponentService service = runtime.getComponentService();
        Key componentKey = Key.of(tagName);
        
        try {
            return service.getComponent(componentKey);
        } catch (Exception e) {
            return null;
        }
    }
    
    private CompletionItem createValueCompletion(String value) {
        CompletionItem item = new CompletionItem();
        item.setLabel(value);
        item.setKind(CompletionItemKind.Value);
        item.setInsertText(value);
        return item;
    }
}
```

#### 1.3 Register AttributeCompletionRule

**File**: `src/main/java/ortus/boxlang/lsp/workspace/completion/CompletionEngine.java`

Add the new rule to the rules list:
```java
public CompletionEngine() {
    this.rules = Arrays.asList(
        new KeywordCompletionRule(),
        new ComponentCompletionRule(),
        new AttributeCompletionRule(),  // Add this line
        new BIFCompletionRule(),
        // ... other rules
    );
}
```

### Phase 2: Testing and Refinement

#### 2.1 Enable Failing Tests

The tests in `BxmTagCompletionTest.java` should now pass:
- `testAttributeNameCompletion()`
- `testPartialAttributeNameCompletion()`
- `testAttributeValueCompletion()`
- `testRequiredAttributesPrioritized()`
- `testSelfClosingTagAttributes()`
- `testAttributeCompletionKind()`

#### 2.2 Add Edge Case Tests

Add tests for:
- Attributes with special characters
- Multiple attributes on same line
- Attributes spanning multiple lines
- Mixed case tag/attribute names
- Invalid/unknown tag names
- Self-closing tags (`<bx:import />`)

#### 2.3 Performance Testing

Test with:
- Large BXM files (1000+ lines)
- Many attributes on a single tag
- Rapid typing scenarios

### Phase 3: Documentation and Polish

#### 3.1 Update User Documentation
- Add examples to README showing attribute completion
- Document supported attribute value types
- Add screenshots/GIFs of completion in action

#### 3.2 Code Documentation
- JavaDoc for new classes and methods
- Inline comments explaining regex patterns
- Document any BoxRuntime API assumptions

#### 3.3 Update Roadmap
- Mark Task 3.10 as fully complete
- Add detailed entry to `development_log.md`

## Technical Challenges and Solutions

### Challenge 1: Context Detection Ordering

**Problem**: Regex patterns can overlap. For example, `<bx:output va` could match both tag name pattern and attribute name pattern.

**Solution**: Check patterns in order of specificity:
1. Attribute value pattern (most specific)
2. Attribute name pattern
3. Tag name pattern (least specific)

### Challenge 2: Extracting Allowed Values

**Problem**: BoxRuntime's `Validator` interface may not expose allowed values in a standard way.

**Solution**: 
1. Investigate `ortus.boxlang.runtime.components.Attribute` and `ortus.boxlang.runtime.validation.Validator` classes
2. If no standard API exists, use heuristics:
   - Boolean-like attributes → suggest true/false
   - Attributes with "type" in name → suggest common types
   - Check attribute name patterns for common values
3. Consider maintaining a small static map of well-known attribute values as fallback

### Challenge 3: Performance with Many Attributes

**Problem**: Some BXM tags may have 20+ attributes, slowing down completion.

**Solution**:
1. Cache `ComponentDescriptor` lookups per file
2. Sort and filter completions efficiently
3. Limit completions to top 50 most relevant
4. Use prefix matching to reduce candidate set

### Challenge 4: Partial Attribute Input

**Problem**: User types `<bx:output enc` - need to detect this is partial attribute "encodefor"

**Solution**: 
1. Extract partial attribute from regex capture group
2. Filter attribute completions by prefix match
3. Use fuzzy matching for better UX (optional enhancement)

## API Research Needed

Before implementing, investigate these BoxRuntime APIs:

### 1. Component Attribute API
```java
ortus.boxlang.runtime.components.Attribute
- name() - String
- validators() - Validator[]
- defaultValue() - Object
- type() - ??? (check if exists)
- description() - ??? (check for documentation)
```

### 2. Validator API
```java
ortus.boxlang.runtime.validation.Validator
- Check for: getAllowedValues(), getType(), etc.
- Look for: OneOfValidator, RegexValidator, TypeValidator
- Investigate how to extract allowed values
```

### 3. ComponentDescriptor API
```java
ortus.boxlang.runtime.components.ComponentDescriptor
- getComponent() - Component
- Check: getAttributesByType(), getRequiredAttributes()
```

**Research Task**: 
1. Read BoxRuntime source code for these classes
2. Check JavaDocs in BoxRuntime project
3. Write small test to dump attribute metadata
4. Document findings before implementing

## File Checklist

### Files to Create
- [ ] `src/main/java/ortus/boxlang/lsp/workspace/completion/AttributeCompletionRule.java`

### Files to Modify
- [ ] `src/main/java/ortus/boxlang/lsp/workspace/completion/CompletionContext.java`
  - Add `BXM_ATTR_NAME_PATTERN`
  - Add `BXM_ATTR_VALUE_PATTERN`
  - Add enum values to `CompletionContextKind`
  - Update `analyzeContext()` method

- [ ] `src/main/java/ortus/boxlang/lsp/workspace/completion/CompletionEngine.java`
  - Register `AttributeCompletionRule`

### Files to Update (Tests)
- [ ] `src/test/java/ortus/boxlang/lsp/BxmTagCompletionTest.java`
  - Verify existing tests pass
  - Add edge case tests

### Files to Update (Documentation)
- [ ] `lsp-roadmap.md` - Mark task 3.10 complete
- [ ] `development_log.md` - Add completion entry
- [ ] `README.md` - Add attribute completion examples

## Estimated Complexity

- **Context Detection**: Medium (2-3 hours)
  - Regex patterns are straightforward
  - Ordering and testing needs care

- **AttributeCompletionRule**: Medium-High (4-6 hours)
  - Core logic is clear
  - Value extraction depends on BoxRuntime API
  - May need fallback heuristics

- **Testing**: Medium (2-3 hours)
  - Tests already written
  - Need edge cases
  - Performance testing

- **Documentation**: Low (1 hour)
  - Straightforward updates

**Total Estimate**: 9-13 hours

## Dependencies

- BoxRuntime API (already integrated)
- LSP4J library (already used)
- No new external dependencies needed

## Success Criteria

1. ✅ Typing `<bx:output ` shows all output attributes
2. ✅ Required attributes appear first in list
3. ✅ Each attribute shows documentation with type/default value
4. ✅ Typing `<bx:output var="` shows value suggestions (if applicable)
5. ✅ All tests in `BxmTagCompletionTest.java` pass
6. ✅ No performance degradation on large files
7. ✅ Feature documented in README with examples

## Future Enhancements (Post-3.10)

- **Snippet completion**: `<bx:loop` expands to full template with required attributes
- **Signature help**: Show parameter info while typing attributes
- **Attribute validation**: Real-time error checking for invalid attributes
- **Value validation**: Check attribute values against validators
- **Hover documentation**: Show attribute docs on hover
- **Go to definition**: Jump to component source in BoxRuntime

## References

- **Current Implementation**: `src/main/java/ortus/boxlang/lsp/workspace/completion/ComponentCompletionRule.java`
- **Test File**: `src/test/java/ortus/boxlang/lsp/BxmTagCompletionTest.java`
- **BoxRuntime Component API**: Check `boxlang-runtime` source code
- **LSP Completion Spec**: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion

## Notes

- This plan assumes BoxRuntime's `Attribute` API provides sufficient metadata
- If API is insufficient, may need to maintain static attribute database
- Prioritize common attributes over exhaustive value suggestions
- User experience > perfect completions

---

**Document Created**: 2026-01-23  
**Author**: OpenCode AI  
**Task**: 3.10 - BXM Tags and Attributes Completion  
**Status**: Implementation plan for Phase 2 (attribute completion)
