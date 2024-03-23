/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7;


import io.xream.internal.util.ClassFileReader;
import io.xream.x7.config.HttpBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class HttpExchangeRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {

        String startClassName = annotationMetadata.getClassName();
        String basePackage = startClassName.substring(0, startClassName.lastIndexOf("."));

        Set<Class<?>> set = ClassFileReader.getClasses(basePackage);

        Map<String, Object> attributes = annotationMetadata.getAnnotationAttributes(EnableHttpExchange.class.getName());

        Object obj = attributes.get("basePackages");
        if (obj != null){
            String[] strs = (String[]) obj;
            for (String str : strs){
                Set<Class<?>> set1 = ClassFileReader.getClasses(str);
                set.addAll(set1);
            }
        }

        List<String> beanNameList = new ArrayList<>();

        List<Class> list = new ArrayList<>();
        for (Class clz : set) {
            Annotation annotation = clz.getAnnotation(HttpExchange.class);
            if (annotation == null)
                continue;

            list.add(clz);
        }

        for (Class<?> clazz : list) {

            HttpExchange annotation = clazz.getAnnotation(HttpExchange.class);
            if (!clazz.isInterface() || annotation == null) {
                continue;
            }

            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
            GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();

            definition.getConstructorArgumentValues()
                    .addGenericArgumentValue(clazz);

            definition.setInstanceSupplier(()-> new HttpBeanFactory<>(clazz).getObject());

            definition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
            String simpleName = clazz.getSimpleName();
            simpleName = StringUtils.uncapitalize(simpleName);
            registry.registerBeanDefinition(simpleName, definition);
        }
    }


}
