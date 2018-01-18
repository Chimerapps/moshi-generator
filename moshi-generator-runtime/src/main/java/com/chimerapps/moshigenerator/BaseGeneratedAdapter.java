package com.chimerapps.moshigenerator;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

/**
 * Base for the generated adapters
 *
 * @author nicolaverbeeck
 */
public abstract class BaseGeneratedAdapter<T> extends JsonAdapter<T> {

    protected Moshi moshi;

    protected final Factory factory;
    protected final Type type;
    protected static final Set<Annotation> EMPTY_ANNOTATIONS = Collections.emptySet();

    public BaseGeneratedAdapter(final Factory factory, final Type type) {
        this.factory = factory;
        this.type = type;
    }

    public void setMoshi(final Moshi moshi) {
        this.moshi = moshi;
    }
}
