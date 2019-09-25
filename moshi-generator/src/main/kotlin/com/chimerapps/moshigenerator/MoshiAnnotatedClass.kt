/*
 *    Copyright 2017 - Chimerapps BVBA
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.chimerapps.moshigenerator

import java.util.*
import javax.lang.model.element.*
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * @author Nicola Verbeeck
 * *         Date 23/05/2017.
 */
class MoshiAnnotatedClass(private val logger: SimpleLogger,
                          val element: TypeElement,
                          val elementUtil: Elements,
                          val typeUtil: Types) {

    fun checkValid() {
        if (!element.modifiers.contains(Modifier.PUBLIC)) {
            throw AnnotationError("Class ${element.qualifiedName.toString()} is not public")
        }

        // Check if it's an abstract class
        if (element.modifiers.contains(Modifier.ABSTRACT)) {
            throw AnnotationError("Class ${element.qualifiedName.toString()} is abstract")
        }
    }

    val packageName: String
        get() {
            var parent: Element? = element.enclosingElement
            while (parent != null) {
                if (parent.kind == ElementKind.PACKAGE) {
                    return (parent as PackageElement).qualifiedName.toString()
                }
                parent = parent.enclosingElement
            }
            throw AnnotationError("Failed to find package of ${element.qualifiedName.toString()}")
        }

    private val isParcelable: Boolean
        get() {
            val parcelable = elementUtil.getTypeElement("android.os.Parcelable")
            return if (parcelable == null)
                false
            else
                typeUtil.isAssignable(element.asType(), parcelable.asType())
        }


    val fields: List<VariableElement> by lazy {
        var constructor: ExecutableElement? = null
        for (childElement in element.enclosedElements) {
            if (childElement.kind == ElementKind.CONSTRUCTOR) {
                val constructorElement = childElement as ExecutableElement

                if (isParcelable && isParcelConstructor(constructorElement)) {
                    continue
                }
                if (constructor != null) {
                    throw AnnotationError("Class ${element.qualifiedName} must have only 1 constructor")
                }
                constructor = constructorElement
            }
        }
        if (constructor == null) {
            throw AnnotationError("Class ${element.qualifiedName} must have a constructor")
        }
        if (constructor.parameters.isEmpty()) {
            throw AnnotationError("Class ${element.qualifiedName} must have a non-empty constructor")
        }

        ArrayList<VariableElement>(constructor.parameters)
    }

    val writerFields: List<VariableElement> by lazy {
        val elements = mutableListOf<VariableElement>()
        var parent = element.asType()
        while (parent.kind == TypeKind.DECLARED) {
            val element = typeUtil.asElement(parent) as TypeElement
            if (element.qualifiedName.toString() == "java.lang.Object")
                break;
            for (childElement in element.enclosedElements) {
                if (childElement.kind == ElementKind.FIELD) {
                    val asVariable = childElement as VariableElement
                    if (!childElement.modifiers.contains(Modifier.TRANSIENT) && !childElement.modifiers.contains(Modifier.STATIC)) {
                        if (childElement.modifiers.contains(Modifier.PUBLIC)
                                || hasGetter("get${asVariable.simpleName.toString().capitalize()}", childElement.asType())
                                || hasGetter("is${asVariable.simpleName.toString().capitalize()}", childElement.asType())
                                || hasGetter(asVariable.simpleName.toString(), childElement.asType())) {
                            elements.add(asVariable)
                        }
                    }
                }
            }
            parent = element.superclass
        }
        elements
    }

    private fun isParcelConstructor(constructorElement: ExecutableElement): Boolean {
        val parameters = constructorElement.parameters
        if (parameters.size != 1)
            return false

        return typeUtil.isAssignable(parameters[0].asType(), elementUtil.getTypeElement("android.os.Parcel").asType())
    }

    fun generatesFactory(): Boolean {
        return element.getAnnotation<GenerateMoshi>(GenerateMoshi::class.java).generateFactory
    }

    fun hasVisibleField(name: String): Boolean {
        return getFieldByName(name)?.modifiers?.contains(Modifier.PRIVATE) == false
    }

    fun getFieldByName(name: String): VariableElement? {
        return element.enclosedElements.find {
            it.kind == ElementKind.FIELD && (it as VariableElement).simpleName.toString() == name
        } as VariableElement?
    }

    fun generatesWriter(): Boolean {
        return element.getAnnotation<GenerateMoshi>(GenerateMoshi::class.java).generateWriter
    }

    fun writerSerializesNulls(): Boolean {
        return element.getAnnotation<GenerateMoshi>(GenerateMoshi::class.java).writerSerializesNulls
    }

    fun debugLogs(): Boolean {
        return element.getAnnotation<GenerateMoshi>(GenerateMoshi::class.java).debugLogs
    }

    fun hasGetter(name: String, returnType: TypeMirror): Boolean {
        var checking = element.asType()
        while (checking.kind == TypeKind.DECLARED) {
            val el = typeUtil.asElement(checking) as TypeElement
            if (el.qualifiedName.toString() == "java.lang.Object")
                break;
            if (el.enclosedElements.find {
                it.kind == ElementKind.METHOD &&
                        (it as ExecutableElement).simpleName.toString() == name && it.parameters.isEmpty() &&
                        typeUtil.isSameType(it.returnType, returnType) &&
                        (it.modifiers.contains(Modifier.PUBLIC))
            } != null) {
                return true
            }
            checking = el.superclass
        }
        return false
    }

}
