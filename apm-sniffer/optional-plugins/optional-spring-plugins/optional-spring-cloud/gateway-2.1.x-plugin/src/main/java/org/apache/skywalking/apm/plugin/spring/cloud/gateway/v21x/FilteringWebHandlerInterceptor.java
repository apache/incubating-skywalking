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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.context.SWTransmitter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import java.lang.reflect.Method;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;


/**
 * @author songxiaoyue
 */
public class FilteringWebHandlerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String SPRING_CLOUD_GATEWAY_ROUTE_PREFIX = "GATEWAY/";
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        EnhancedInstance instance = getInstance(allArguments[0]);
        if (instance == null) {
            return;
        }
        AbstractSpan span = (AbstractSpan) instance.getSkyWalkingDynamicField();
        if (span == null) {
            return;
        }
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        String operationName = SPRING_CLOUD_GATEWAY_ROUTE_PREFIX;
        ContextSnapshot capture = ContextManager.capture();
        Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
        operationName = operationName + route.getId();
        span.setOperationName(operationName);
        SWTransmitter transmitter = new SWTransmitter(span.prepareForAsync(),capture,operationName);
        instance.setSkyWalkingDynamicField(transmitter);
        ContextManager.stopSpan(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }


    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

    public static EnhancedInstance getInstance(Object o) {
        EnhancedInstance instance = null;
        if (o instanceof ServerWebExchangeDecorator) {
            ServerWebExchange delegate = ((ServerWebExchangeDecorator) o).getDelegate();
            if (delegate instanceof DefaultServerWebExchange) {
                instance = (EnhancedInstance) delegate;
            }
        } else if (o instanceof DefaultServerWebExchange) {
            instance = (EnhancedInstance) o;
        }
        return instance;
    }
}
