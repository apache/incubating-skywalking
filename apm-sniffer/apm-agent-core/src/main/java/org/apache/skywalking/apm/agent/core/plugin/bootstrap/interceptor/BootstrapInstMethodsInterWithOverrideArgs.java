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
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.plugin.bootstrap.IBootstrapLog;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.BootstrapInterRuntimeAssist;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.OverrideCallable;

/**
 * The actual byte-buddy's interceptor to intercept bootstrap class instance methods. In this class, it provide a bridge between
 * byte-buddy and sky-walking plugin.
 */
public class BootstrapInstMethodsInterWithOverrideArgs {

    private InstanceMethodsAroundInterceptor interceptor;
    private static IBootstrapLog LOGGER;

    public BootstrapInstMethodsInterWithOverrideArgs(String interceptorClassName) {
        prepare(interceptorClassName);
    }

    /**
     * Intercept the target instance method.
     *
     * @param obj          target class instance.
     * @param allArguments all method arguments
     * @param method       method description.
     * @param zuper        the origin call ref.
     * @return the return value of target instance method.
     * @throws Exception only throw exception because of zuper.call() or unexpected exception in sky-walking ( This is a
     *                   bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @Morph OverrideCallable zuper,
                            @Origin Method method) throws Throwable {
        EnhancedInstance targetObject = (EnhancedInstance) obj;

        MethodInterceptResult result = new MethodInterceptResult();
        try {
            if (interceptor != null) {
                interceptor.beforeMethod(targetObject, method, allArguments, method.getParameterTypes(), result);
            }
        } catch (Throwable t) {
            if (LOGGER != null) {
                LOGGER.error(t, "class[{}] before method[{}] intercept failure", obj.getClass(), method.getName());
            }
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
                    interceptor.handleMethodException(
                        targetObject, method, allArguments, method.getParameterTypes(), t);
                }
            } catch (Throwable t2) {
                if (LOGGER != null) {
                    LOGGER.error(t2, "class[{}] handle method[{}] exception failure", obj.getClass(), method.getName());
                }
            }
            throw t;
        } finally {
            try {
                if (interceptor != null) {
                    ret = interceptor.afterMethod(targetObject, method, allArguments, method.getParameterTypes(), ret);
                }
            } catch (Throwable t) {
                if (LOGGER != null) {
                    LOGGER.error(t, "class[{}] after method[{}] intercept failure", obj.getClass(), method.getName());
                }
            }
        }

        return ret;
    }

    /**
     * Prepare the context. Link to the agent core in AppClassLoader.
     */
    private void prepare(String interceptorClassName) {
        if (this.interceptor == null) {
            ClassLoader loader = BootstrapInterRuntimeAssist.getAgentClassLoader();

            if (loader != null) {
                IBootstrapLog logger = BootstrapInterRuntimeAssist.getLogger(loader, interceptorClassName);
                if (logger != null) {
                    LOGGER = logger;

                    this.interceptor = BootstrapInterRuntimeAssist.createInterceptor(loader, interceptorClassName, LOGGER);
                }
            } else {
                LOGGER.error("Runtime ClassLoader not found when create {}." + interceptorClassName);
            }
        }
    }
}

