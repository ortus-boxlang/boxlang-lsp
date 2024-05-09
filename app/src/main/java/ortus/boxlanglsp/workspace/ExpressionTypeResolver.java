package ortus.boxlanglsp.workspace;

import ortus.boxlang.compiler.ast.BoxExpression;
import ortus.boxlang.compiler.ast.expression.BoxArrayLiteral;
import ortus.boxlang.compiler.ast.expression.BoxBooleanLiteral;
import ortus.boxlang.compiler.ast.expression.BoxClosure;
import ortus.boxlang.compiler.ast.expression.BoxDecimalLiteral;
import ortus.boxlang.compiler.ast.expression.BoxIntegerLiteral;
import ortus.boxlang.compiler.ast.expression.BoxLambda;
import ortus.boxlang.compiler.ast.expression.BoxStringInterpolation;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.runtime.types.BoxLangType;

public class ExpressionTypeResolver {

    public static BoxLangType determineType(BoxExpression node) {
        if (node instanceof BoxStringLiteral) {
            return BoxLangType.STRING;
        } else if (node instanceof BoxStringInterpolation) {
            return BoxLangType.STRING;
        } else if (node instanceof BoxIntegerLiteral) {
            return BoxLangType.NUMERIC;
        } else if (node instanceof BoxDecimalLiteral) {
            return BoxLangType.NUMERIC;
        } else if (node instanceof BoxBooleanLiteral) {
            return BoxLangType.BOOLEAN;
        } else if (node instanceof BoxArrayLiteral) {
            return BoxLangType.ARRAY;
        } else if (node instanceof BoxStringLiteral) {
            return BoxLangType.STRUCT;
        } else if (node instanceof BoxClosure) {
            return BoxLangType.UDF;
        } else if (node instanceof BoxLambda) {
            return BoxLangType.UDF;
        }

        return null;
    }
}
