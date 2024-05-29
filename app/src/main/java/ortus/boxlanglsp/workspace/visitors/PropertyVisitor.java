package ortus.boxlanglsp.workspace.visitors;

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxProperty;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;
import ortus.boxlanglsp.workspace.types.ParsedProperty;

public class PropertyVisitor extends VoidBoxVisitor {

    List<ParsedProperty> properties = new ArrayList<ParsedProperty>();

    public List<ParsedProperty> getProperties() {
        return properties;
    }

    public void visit(BoxProperty node) {
        properties.add(new ParsedProperty(getName(node), getType(node), node));
    }

    private String getName(BoxProperty node) {
        BoxAnnotation nameAnnotation = node.getAllAnnotations()
                .stream()
                .filter(annotation -> annotation.getKey().getValue().equalsIgnoreCase("name"))
                .findFirst()
                .orElseGet(() -> node.getAllAnnotations().get(0));

        if (nameAnnotation.getValue() == null) {
            return nameAnnotation.getKey().getValue();
        } else if (nameAnnotation.getValue() instanceof BoxStringLiteral bsl) {
            return bsl.getValue();
        }

        return nameAnnotation.getValue().toString();
    }

    private String getType(BoxProperty node) {
        BoxAnnotation nameAnnotation = node.getAllAnnotations()
                .stream()
                .filter(annotation -> annotation.getKey().getValue().equalsIgnoreCase("type"))
                .findFirst()
                .orElseGet(() -> node.getAllAnnotations().get(0));

        if (nameAnnotation.getValue() == null) {
            return nameAnnotation.getKey().getValue();
        } else if (nameAnnotation.getValue() instanceof BoxStringLiteral bsl) {
            return bsl.getValue();
        }

        return nameAnnotation.getValue().toString();
    }

}
