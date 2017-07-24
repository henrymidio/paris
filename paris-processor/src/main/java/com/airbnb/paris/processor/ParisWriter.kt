package com.airbnb.paris.processor

import com.airbnb.paris.processor.utils.ClassNames
import com.airbnb.paris.processor.utils.className
import com.grosner.kpoet.*
import com.squareup.javapoet.*
import java.io.IOException
import java.util.*
import javax.annotation.processing.Filer
import javax.lang.model.element.Modifier

internal object ParisWriter {

    @Throws(IOException::class)
    internal fun writeFrom(filer: Filer, styleableClassesInfo: List<StyleableInfo>) {
        val parisTypeBuilder = `public final class`(ParisProcessor.PARIS_CLASS_NAME.simpleName()) {
            extends(ParisProcessor.PARIS_BASE_CLASS_NAME)
        }.toBuilder()

        ParisProcessor.BUILT_IN_STYLE_APPLIERS.forEach { styleApplierQualifiedName, viewQualifiedName ->
            val styleApplierClassName = styleApplierQualifiedName.className()
            parisTypeBuilder.addMethod(buildStyleMethod(
                    styleApplierClassName.packageName(),
                    styleApplierClassName.simpleName(),
                    viewQualifiedName.className()))
        }

        for (styleableClassInfo in styleableClassesInfo) {
            parisTypeBuilder.addMethod(buildStyleMethod(styleableClassInfo))
        }

        parisTypeBuilder.addMethod(buildAssertStylesMethod(styleableClassesInfo))

        JavaFile.builder(ParisProcessor.PARIS_CLASS_NAME.packageName(), parisTypeBuilder.build())
                .build()
                .writeTo(filer)
    }

    private fun buildStyleMethod(styleableClassInfo: StyleableInfo): MethodSpec {
        return buildStyleMethod(
                styleableClassInfo.elementPackageName,
                String.format(Locale.US, ParisProcessor.STYLE_APPLIER_CLASS_NAME_FORMAT, styleableClassInfo.elementName),
                TypeName.get(styleableClassInfo.elementType))
    }

    private fun buildStyleMethod(styleApplierPackageName: String, styleApplierSimpleName: String, viewParameterTypeName: TypeName): MethodSpec {
        val styleApplierClassName = ClassName.get(
                styleApplierPackageName,
                styleApplierSimpleName)
        return `public static`(styleApplierClassName, "style", param(viewParameterTypeName, "view")) {
            `return`("process(new \$T(view))", styleApplierClassName)
        }
    }

    private fun buildAssertStylesMethod(styleableClassesInfo: List<StyleableInfo>): MethodSpec {
        val builder = `public static`(TypeName.VOID, "assertStylesContainSameAttributes", param(ClassNames.ANDROID_CONTEXT, "context")) {
            javadoc("For debugging")
        }.toBuilder()

        for (styleableClassInfo in styleableClassesInfo) {
            if (styleableClassInfo.styles.size > 1) {
                builder.statement("\$T \$T = new \$T(context)", styleableClassInfo.elementType, styleableClassInfo.elementType, styleableClassInfo.elementType)

                val styleVarargCodeBuilder = CodeBlock.builder()
                for ((i, style) in styleableClassInfo.styles.withIndex()) {
                    if (i > 0) {
                        styleVarargCodeBuilder.add(", ")
                    }
                    styleVarargCodeBuilder.add("new \$T(\$L)",
                            ParisProcessor.STYLE_CLASS_NAME, style.androidResourceId.code)
                }

                val assertEqualAttributesCode = CodeBlock.of("\$T.Companion.assertSameAttributes(style(\$T), \$L);\n",
                        ParisProcessor.STYLE_APPLIER_UTILS_CLASS_NAME,
                        styleableClassInfo.elementType,
                        styleVarargCodeBuilder.build())
                builder.addCode(assertEqualAttributesCode)
            }
        }

        return builder.build()
    }
}
