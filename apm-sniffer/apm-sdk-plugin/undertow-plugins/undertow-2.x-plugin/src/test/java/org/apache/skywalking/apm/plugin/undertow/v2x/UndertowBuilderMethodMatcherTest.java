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
 *
 */
package org.apache.skywalking.apm.plugin.undertow.v2x;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * @author AI
 * 2019-08-10
 */
public class UndertowBuilderMethodMatcherTest {

    @Test
    public void testMatch() throws Throwable {
        ElementMatcher<MethodDescription> matcher = named("addHttpListener").and(takesArgument(2, HttpHandler.class));
        Method method = Undertow.Builder.class.getMethod("addHttpListener", int.class, String.class, HttpHandler.class);
        MethodDescription md = new MethodDescription.ForLoadedMethod(method);
        boolean r = matcher.matches(md);
        Assert.assertTrue(r);
    }

}
