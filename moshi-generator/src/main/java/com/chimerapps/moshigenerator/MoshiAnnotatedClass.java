package com.chimerapps.moshigenerator;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Nicola Verbeeck
 * @date 23/05/2017.
 */
public class MoshiAnnotatedClass {

	private final TypeElement mElement;
	private final Messager mMessager;

	public MoshiAnnotatedClass(final TypeElement element, final Messager messager) {
		mElement = element;
		mMessager = messager;
	}

	public TypeElement getElement() {
		return mElement;
	}

	public boolean isValid() {
		if (!mElement.getModifiers().contains(Modifier.PUBLIC)) {
			mMessager.printMessage(Diagnostic.Kind.ERROR, String.format("Class %s is not public", mElement.getQualifiedName().toString()));
			return false;
		}

		// Check if it's an abstract class
		if (mElement.getModifiers().contains(Modifier.ABSTRACT)) {
			mMessager.printMessage(Diagnostic.Kind.ERROR, String.format("Class %s is abstract", mElement.getQualifiedName().toString()));
			return false;
		}
		return true;
	}

	public String getPackage() throws IOException {
		Element parent = mElement.getEnclosingElement();
		while (parent != null) {
			if (parent.getKind() == ElementKind.PACKAGE) {
				return ((PackageElement) parent).getQualifiedName().toString();
			}
			parent = parent.getEnclosingElement();
		}
		throw new IOException(String.format("Failed to find package of %s", mElement.getQualifiedName().toString()));
	}

	public List<VariableElement> getFields() throws IOException {
		ExecutableElement constructor = null;
		for (final Element element : mElement.getEnclosedElements()) {
			if (element.getKind() == ElementKind.CONSTRUCTOR) {
				final ExecutableElement constructorElement = (ExecutableElement) element;
				if (constructor != null) {
					throw new IOException(String.format("Class %s must have only 1 constructor", mElement.getQualifiedName().toString()));
				}
				constructor = constructorElement;
			}
		}
		if (constructor == null) {
			throw new IOException(String.format("Class %s must have a constructor", mElement.getQualifiedName().toString()));
		}
		if (constructor.getParameters().isEmpty()) {
			throw new IOException(String.format("Class %s must have a non-empty constructor", mElement.getQualifiedName().toString()));
		}

		return new ArrayList<>(constructor.getParameters());
	}

}
