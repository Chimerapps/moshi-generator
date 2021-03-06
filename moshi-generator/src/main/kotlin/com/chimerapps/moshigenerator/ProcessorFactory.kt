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

import com.chimerapps.moshigenerator.utils.tracePerformance
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

/**
 * @author Nicola Verbeeck
 * *         Date 23/05/2017
 */
const val OPTION_PERFORMANCE_TRACE = "moshiGenPerformanceTrace"

@SuppressWarnings("unused")
class ProcessorFactory : AbstractProcessor() {
    private lateinit var filer: Filer
    private lateinit var messager: Messager
    private lateinit var elementUtils: Elements
    private lateinit var typeUtils: Types
    private lateinit var logger: SimpleLogger

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (processingEnv.options[OPTION_PERFORMANCE_TRACE] != null)
            tracePerformance = true

        tracePerformance(logger, "Process") {
            try {
                if (roundEnv.processingOver()) {
                    return true
                }

                try {
                    val classes = processDataClasses(roundEnv)
                    if (tracePerformance) logger.logInfo("Generated ${classes.size} adapter classes")
                    processFactory(roundEnv, classes)
                } catch (annotationError: Exception) {
                    logger.logError(annotationError.message
                            ?: "<Unknown error> ${annotationError.javaClass.canonicalName}", annotationError)
                    messager.printMessage(Diagnostic.Kind.ERROR, annotationError.message
                            ?: "<Unknown error> ${annotationError.javaClass.canonicalName}")
                }

            } catch (e: Exception) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to execute round: $e")
                logger.logError(e.message ?: "", e)
            }
        }

        return true
    }

    private fun processDataClasses(roundEnv: RoundEnvironment): List<MoshiAnnotatedClass> {
        tracePerformance(logger, "Process data classes") {
            val classes = ArrayList<MoshiAnnotatedClass>()

            for (element in roundEnv.getElementsAnnotatedWith(GenerateMoshi::class.java)) {
                if (element.kind !== ElementKind.CLASS) {
                    throw AnnotationError("Only classes can be annotated with @GenerateMoshi (${element.simpleName})")
                }

                val typeElement = element as TypeElement
                val clazz = MoshiAnnotatedClass(logger, typeElement, elementUtils, typeUtils)
                clazz.checkValid()

                classes.add(clazz)

                AdapterGenerator(clazz, filer, elementUtils, logger).generate()
            }
            return classes
        }
    }

    private fun processFactory(roundEnv: RoundEnvironment, classes: List<MoshiAnnotatedClass>) {
        tracePerformance(logger, "Process factory") {
            val knownClasses = hashSetOf<String>()

            var factoryCount = 0
            for (element in roundEnv.getElementsAnnotatedWith(GenerateMoshiFactory::class.java)) {
                val clazz = MoshiFactoryAnnotatedClass(element, logger)

                MoshiFactoryGenerator(clazz.className, clazz.targetPackage, clazz.moshiClasses, filer, elementUtils).generate()

                clazz.moshiClasses.forEach {
                    if (knownClasses.contains(it.toString())) {
                        messager.printMessage(Diagnostic.Kind.WARNING, "Class '$it' is registered in multiple factories")
                    } else {
                        knownClasses.add(it.toString())
                    }
                }
                ++factoryCount
            }

            classes.filter { !knownClasses.contains(it.element.qualifiedName.toString()) && !it.generatesFactory() }
                    .forEach {
                        messager.printMessage(Diagnostic.Kind.WARNING, "Class '${it.element.qualifiedName}' is not registered in any factory")
                    }

            if (tracePerformance) logger.logInfo("Generated $factoryCount factory classes")
        }
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(GenerateMoshi::class.java.canonicalName, GenerateMoshiFactory::class.java.canonicalName)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
        elementUtils = processingEnv.elementUtils
        typeUtils = processingEnv.typeUtils
        logger = SimpleLogger(messager)
    }

    override fun getSupportedOptions(): MutableSet<String> {
        return super.getSupportedOptions().toMutableSet()
                .apply {
                    add(OPTION_PERFORMANCE_TRACE)
                }
    }
}