package ortus.boxlang.lsp.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention( RetentionPolicy.RUNTIME )
@Target( ElementType.FIELD )
public @interface ConfigSetting {

	String key() default "";

	String type();

	String description();

	String defaultValue();

	String since() default "";
}
