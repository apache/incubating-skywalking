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

package org.apache.skywalking.apm.agent.core.plugin.bootstrap.interceptor;

import java.lang.reflect.Method;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.apache.skywalking.apm.agent.core.plugin.bootstrap.IBootstrapLog;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.BootstrapInterRuntimeAssist;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.OverrideCallable;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods. In this class, it provide a bridge between
 * byte-buddy and sky-walking plugin.
 */
public class BootstrapStaticMethodsInterWithOverrideArgs {

    private StaticMethodsAroundInterceptor interceptor;
    private static IBootstrapLog LOGGER;

    public BootstrapStaticMethodsInterWithOverrideArgs(String interceptorClassName) {
        prepare(interceptorClassName);
    }

    /**
     * Intercept the target static method.
     *
     * @param clazz        target class
     * @param allArguments all method arguments
     * @param method       method description.
     * @param zuper        the origin call ref.
     * @return the return value of target static method.
     * @throws Exception only throw exception because of zuper.call() or unexpected exception in sky-walking ( This is a
     *                   bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method,
                            @Morph OverrideCallable zuper) throws Throwable {

        MethodInterceptResult result = new MethodInterceptResult();
        try {
            if (interceptor != null) {
                interceptor.beforeMethod(clazz, method, allArguments, method.getParameterTypes(), result);
            }
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] before static method[{}] intercept failure", clazz, method.getName());
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            } else {
                ret = zuper.call(allArguments);
            }
        } catch (Throwable t) {
            try {
                if (interceptor != null) {
                    interceptor.handleMethodException(clazz, method, allArguments, method.getParameterTypes(), t);
                }
            } catch (Throwable t2) {
                LOGGER.error(
                    t2, "class[{}] handle static method[{}] exception failure", clazz, method.getName(),
                    t2.getMessage()
                );
            }
            throw t;
        } finally {
            try {
                if (interceptor != null) {
                    ret = interceptor.afterMethod(clazz, method, allArguments, method.getParameterTypes(), ret);
                }
            } catch (Throwable t) {
                LOGGER.error(
                    t, "class[{}] after static method[{}] intercept failure:{}", clazz, method.getName(),
                    t.getMessage()
                );
            }
        }
        return ret;
    }

    /**
     * Prepare the context. Link to the agent core in AppClassLoader.
     */
    private void prepare(String interceptorClassName) {
        if (interceptor == null) {
            ClassLoader loader = BootstrapInterRuntimeAssist.getAgentClassLoader();

            if (loader != null) {
                IBootstrapLog logger = BootstrapInterRuntimeAssist.getLogger(loader, interceptorClassName);
                if (logger != null) {
                    LOGGER = logger;

                    interceptor = BootstrapInterRuntimeAssist.createInterceptor(loader, interceptorClassName, LOGGER);
                }
            } else {
                LOGGER.error("Runtime ClassLoader not found when create {}." + interceptorClassName);
            }
        }
    }
}
