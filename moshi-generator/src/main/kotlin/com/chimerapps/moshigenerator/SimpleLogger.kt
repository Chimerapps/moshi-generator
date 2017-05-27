package com.chimerapps.moshigenerator

import java.io.PrintWriter
import java.io.StringWriter
import javax.annotation.processing.Messager
import javax.tools.Diagnostic

/**
 * @author Nicola Verbeeck
 * Date 26/05/2017.
 */
class SimpleLogger(val messager: Messager) {

    fun logDebug(message: String, error: Throwable? = null) {
        messager.printMessage(Diagnostic.Kind.NOTE, makeMessage(message, error))
    }

    fun logInfo(message: String, error: Throwable? = null) {
        messager.printMessage(Diagnostic.Kind.NOTE, makeMessage(message, error))
    }

    fun logError(message: String, error: Throwable? = null) {
        messager.printMessage(Diagnostic.Kind.WARNING, makeMessage(message, error))
    }

    companion object {
        private fun makeMessage(message: String, error: Throwable?): String {
            if (error == null)
                return message

            val stringWriter = StringWriter()

            PrintWriter(stringWriter).use {
                it.println(message)
                error.printStackTrace(it)
            }
            return stringWriter.buffer.toString()
        }
    }

}