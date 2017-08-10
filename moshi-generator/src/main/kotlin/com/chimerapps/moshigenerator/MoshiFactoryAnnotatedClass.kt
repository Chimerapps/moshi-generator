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
            logger.logDebug("Examining annotation: ${mirror.annotationType}")
            if (name == mirror.annotationType.toString()) {
                logger.logDebug("Found correct one, init from here")
                buildAnnotationFromMirror(mirror);
                return@forEach
            }
        }
    }

    lateinit var moshiClasses: List<TypeName>

    val targetPackage: String by lazy {
        makePackage(_targetPackage)
    }

    fun debugLogs(): Boolean {
        return element.getAnnotation<GenerateMoshiFactory>(GenerateMoshiFactory::class.java).debugLogs
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
            logger.logDebug("Got annotation value with name: ${executableElement.simpleName}")
            logger.logDebug("Got annotation value with value: $annotationValue")
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
        logger.logDebug("Extracting class names from $value")
        moshiClasses = (value as List<AnnotationValue>).map {
            logger.logDebug("Got class name! ${ClassName.get((it.value as TypeMirror))}")
            ClassName.get((it.value as TypeMirror))
        }
        logger.logDebug("# Classes loaded ${moshiClasses.size} from value")
    }


}