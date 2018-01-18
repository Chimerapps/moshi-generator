package com.chimerapps.moshigenerator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.type.TypeMirror

/**
 * @author Nicola Verbeeck
 * @date 26/05/2017.
 */
class MoshiFactoryAnnotatedClass(val element: Element, val logger: SimpleLogger) {

    var className: String = "MoshiFactory"
    private var _targetPackage: String = ""

    init {
        val name = GenerateMoshiFactory::class.java.name
        element.annotationMirrors.forEach { mirror ->
            if (name == mirror.annotationType.toString()) {
                buildAnnotationFromMirror(mirror)
                return@forEach
            }
        }
    }

    lateinit var moshiClasses: List<TypeName>

    val targetPackage: String by lazy {
        makePackage(_targetPackage)
    }

    private fun findPackageOfElement(element: Element?): String {
        if (element == null)
            return ""

        if (element is PackageElement) {
            return element.qualifiedName.toString()
        } else {
            return findPackageOfElement(element.enclosingElement)
        }
    }

    private fun buildAnnotationFromMirror(mirror: AnnotationMirror) {
        mirror.elementValues.forEach { executableElement, annotationValue ->
            when (executableElement.simpleName.toString()) {
                "value" -> makeValue(annotationValue.value)
                "targetClassName" -> className = annotationValue.value as String
                "targetPackage" -> _targetPackage = annotationValue.value as String
            }
        }
    }

    private fun makePackage(annotationValue: String): String {
        return if (annotationValue.isNullOrBlank())
            findPackageOfElement(element)
        else
            annotationValue.trim()
    }

    @Suppress("UNCHECKED_CAST")
    private fun makeValue(value: Any) {
        moshiClasses = (value as List<AnnotationValue>).map {
            ClassName.get((it.value as TypeMirror))
        }
    }


}