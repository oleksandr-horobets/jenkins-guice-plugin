/*
 * Copyright 2015 Oleksandr Horobets.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jenkinsci.plugins.guice;

import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import jenkins.model.Jenkins;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Oleksandr Horobets
 */
public class DependencyInjectionConverter extends ReflectionConverter implements Converter {
    private static final Logger LOGGER = Logger.getLogger(DependencyInjectionConverter.class.getName());

    private Injector injectorInstance;

    private final Class beanType;
    private final Class beanFactoryClass;

    private final Method beanFactoryMethod;
    private final List<Method> beanGetters;

    public DependencyInjectionConverter(Mapper mapper, ReflectionProvider reflectionProvider, Class beanType) {
        super(mapper, reflectionProvider, beanType);

        this.beanType = beanType;
        this.beanFactoryClass = getBeanFactoryClass();
        this.beanFactoryMethod = getBeanFactoryMethod();
        this.beanGetters = getBeanGetters();
    }

    private List<Method> getBeanGetters() {
        Method factoryMethod = getBeanFactoryMethod();
        List<Method> beanGetters = new LinkedList<Method>();

        Map<String, Method> assistedGetters = new HashMap<String, Method>();

        for(Method getter : beanType.getMethods()){
            AssistedGetter annotation = getter.getAnnotation(AssistedGetter.class);

            if(annotation != null && annotation.value() != null){
                assistedGetters.put(annotation.value(), getter);
            }
        }

        for(Parameter parameter : factoryMethod.getParameters()){
            Assisted annotation = parameter.getAnnotation(Assisted.class);

            if(annotation != null && annotation.value() != null && !annotation.value().equals("")){
                Method getter = assistedGetters.get(annotation.value());

                if(getter != null){
                    beanGetters.add(getter);
                } else {
                    throw new RuntimeException("Could not find method annotated with @AssistedGetter(\"" + annotation.value() + "\")");
                }

            } else {
                throw new RuntimeException("Factory method parameter should be annotated with @Assisted(\"paramName\")");
            }
        }

        return beanGetters;
    }

    private Class getBeanFactoryClass(){
        AssistedFactory annotation = (AssistedFactory) beanType.getAnnotation(AssistedFactory.class);

        if(annotation == null) {
            throw new RuntimeException("Bean " + beanType + " should have @AssistedFactory annotation");
        }

        return annotation.value();
    }

    private Method getBeanFactoryMethod(){
        for(Method method : beanFactoryClass.getMethods()){
            if(method.getName().equals("create")){
                //TODO add parameters check
                return method;
            }
        }

        throw new RuntimeException("Unable to find factory method");
    }

    @Override
    public void marshal(java.lang.Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
        super.marshal(o, hierarchicalStreamWriter, marshallingContext);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
        Object xstreamObject = super.unmarshal(hierarchicalStreamReader, unmarshallingContext);
        Injector injector = blockingGetInjector();

        Object[] actualArguments = new Object[beanGetters.size()];
        int i = 0;

        for(Method getter : beanGetters){
            try {
                actualArguments[i++] = getter.invoke(xstreamObject);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            return beanFactoryMethod.invoke(injector.getInstance(beanFactoryClass), actualArguments);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private Injector blockingGetInjector() {
        while(injectorInstance == null){
            injectorInstance = Jenkins.getInstance().lookup.get(Injector.class);

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                LOGGER.severe("Unexpected exception: " + e.toString());
            }
        }

        return injectorInstance;
    }

    @Override
    public boolean canConvert(Class aClass) {
        return beanType.equals(aClass);
    }
}
