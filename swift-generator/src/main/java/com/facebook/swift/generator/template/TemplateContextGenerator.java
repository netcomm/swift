/*
 * Copyright (C) 2012 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.swift.generator.template;

import com.facebook.swift.generator.SwiftGeneratorConfig;
import com.facebook.swift.generator.SwiftGeneratorTweak;
import com.facebook.swift.generator.SwiftJavaType;
import com.facebook.swift.generator.TypeRegistry;
import com.facebook.swift.generator.TypeToJavaConverter;
import com.facebook.swift.parser.model.AbstractStruct;
import com.facebook.swift.parser.model.IntegerEnum;
import com.facebook.swift.parser.model.IntegerEnumField;
import com.facebook.swift.parser.model.Service;
import com.facebook.swift.parser.model.StringEnum;
import com.facebook.swift.parser.model.ThriftField;
import com.facebook.swift.parser.model.ThriftMethod;
import com.google.common.base.Preconditions;

import java.util.HashSet;
import java.util.Set;

import static com.facebook.swift.generator.util.SwiftInternalStringUtils.isBlank;


public class TemplateContextGenerator
{
    private static final MethodContext CLOSE_METHOD_CONTEXT = new MethodContext(null, true, "close", "void");

    private final SwiftGeneratorConfig generatorConfig;
    private final TypeRegistry typeRegistry;
    private final TypeToJavaConverter typeConverter;
    private final String defaultNamespace;

    public TemplateContextGenerator(final SwiftGeneratorConfig generatorConfig,
                                    final TypeRegistry typeRegistry,
                                    final TypeToJavaConverter typeConverter,
                                    final String defaultNamespace)
    {
        this.generatorConfig = generatorConfig;
        this.typeRegistry = typeRegistry;
        this.defaultNamespace = defaultNamespace;
        this.typeConverter = typeConverter;
    }

    public ServiceContext serviceFromThrift(final Service service)
    {
        final String name = mangleJavatypeName(service.getName());
        final SwiftJavaType javaType = typeRegistry.findType(defaultNamespace, name);
        final SwiftJavaType parentType = typeRegistry.findType(service.getParent().orNull());

        final Set<String> javaParents = new HashSet<>();
        if (parentType != null) {
            javaParents.add(parentType.getClassName());
        }

        final ServiceContext serviceContext = new ServiceContext(name,
                                                                 javaType.getPackage(),
                                                                 javaType.getSimpleName(),
                                                                 javaParents);

        if (generatorConfig.containsTweak(SwiftGeneratorTweak.ADD_CLOSEABLE_INTERFACE)) {
            javaParents.add("Closeable");
            serviceContext.addMethod(CLOSE_METHOD_CONTEXT);
        }

        return serviceContext;
    }

    public StructContext structFromThrift(final AbstractStruct struct)
    {
        final String name = mangleJavatypeName(struct.getName());
        final SwiftJavaType javaType = typeRegistry.findType(defaultNamespace, name);

        return new StructContext(name,
                                 javaType.getPackage(),
                                 javaType.getSimpleName());
    }

    public MethodContext methodFromThrift(final ThriftMethod method)
    {
        return new MethodContext(method.getName(),
                                 method.isOneway(),
                                 mangleJavamethodName(method.getName()),
                                 typeConverter.convertType(method.getReturnType()));
    }

    public FieldContext fieldFromThrift(final ThriftField field)
    {
        Preconditions.checkState(field.getIdentifier().isPresent(), "exception %s has no identifier!", field.getName());

        return new FieldContext(field.getName(),
                                field.getIdentifier().get().shortValue(),
                                typeConverter.convertType(field.getType()),
                                mangleJavamethodName(field.getName()),
                                getterName(field),
                                setterName(field));
    }

    public ExceptionContext exceptionFromThrift(final ThriftField field)
    {
        Preconditions.checkState(field.getIdentifier().isPresent(), "exception %s has no identifier!", field.getName());
        return new ExceptionContext(typeConverter.convertType(field.getType()), field.getIdentifier().get().shortValue());
    }

    public EnumContext enumFromThrift(final IntegerEnum integerEnum)
    {
        final String name = mangleJavatypeName(integerEnum.getName());
        final SwiftJavaType javaType = typeRegistry.findType(defaultNamespace, name);
        return new EnumContext(javaType.getPackage(), javaType.getSimpleName());
    }

    public EnumContext enumFromThrift(final StringEnum stringEnum)
    {
        final String name = mangleJavatypeName(stringEnum.getName());
        final SwiftJavaType javaType = typeRegistry.findType(defaultNamespace, name);
        return new EnumContext(javaType.getPackage(), javaType.getSimpleName());
    }

    public EnumFieldContext fieldFromThrift(final IntegerEnumField field)
    {
        Preconditions.checkState(field.getValue().get() != null, "field value for integer field %s is null!", field.getName());
        return new EnumFieldContext(mangleJavaConstantName(field.getName()), field.getValue().get());
    }

    public EnumFieldContext fieldFromThrift(final String value)
    {
        return new EnumFieldContext(mangleJavaConstantName(value), null);
    }

    /**
     * Turn an incoming snake case name into camel case for use in a java method name.
     */
    public static final String mangleJavamethodName(final String src)
    {
        return mangleJavaName(src, false);
    }

    /**
     * Turn an incoming snake case name into camel case for use in a java type name.
     */
    public static final String mangleJavatypeName(final String src)
    {
        return mangleJavaName(src, true);
    }

    private static final String mangleJavaName(final String src, boolean capitalize)
    {
        Preconditions.checkArgument(!isBlank(src), "input name must not be blank!");

        final StringBuilder sb = new StringBuilder();
        boolean upCase = capitalize;
        for (int i = 0; i < src.length(); i++) {
            if (src.charAt(i) == '_') {
                upCase = true;
                continue;
            }
            else {
                sb.append(upCase ? Character.toUpperCase(src.charAt(i)) : src.charAt(i));
                upCase = false;
            }
        }
        return sb.toString();
    }

    public static final String mangleJavaConstantName(final String src)
    {
        final StringBuilder sb = new StringBuilder();
        if (!isBlank(src)) {
            boolean lowerCase = false;
            for (int i = 0; i < src.length(); i++) {
                if (Character.isUpperCase(src.charAt(i))) {
                    if (lowerCase) {
                        sb.append('_');
                    }
                    sb.append(Character.toUpperCase(src.charAt(i)));
                    lowerCase = false;
                }
                else {
                    sb.append(Character.toUpperCase(src.charAt(i)));
                    lowerCase = true;
                }
            }
        }
        return sb.toString();
    }

    private String getterName(final ThriftField field)
    {
        final String type = typeConverter.convertType(field.getType());
        return ("boolean".equals(type) ? "is" : "get") + mangleJavatypeName(field.getName());
    }

    private String setterName(final ThriftField field)
    {
        return "set" + mangleJavatypeName(field.getName());
    }
}
