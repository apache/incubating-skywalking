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

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.expression.Expression;
import org.apache.skywalking.oap.server.core.alarm.provider.expression.ExpressionContext;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CompositeRuleEvaluatorTest {

    private CompositeRuleEvaluator ruleEvaluator;

    @Before
    public void init() {
        Expression expression = new Expression(new ExpressionContext());
        ruleEvaluator = new CompositeRuleEvaluator(expression);
    }

    @Test
    public void testEvaluatorMessageWithAndOp() {
        List<CompositeAlarmRule> compositeAlarmRules = new ArrayList<>();
        CompositeAlarmRule compositeAlarmRule = new CompositeAlarmRule("dummy", "a_rule && b_rule", "composite rule triggered!");
        compositeAlarmRules.add(compositeAlarmRule);
        List<AlarmMessage> alarmMessages = getAlarmMessages();
        List<AlarmMessage> compositeMsgs = ruleEvaluator.evaluator(compositeAlarmRules, alarmMessages);
        assertThat(compositeMsgs.size(), is(1));
        assertThat(compositeMsgs.get(0).getAlarmMessage(), is("composite rule triggered!"));
        assertThat(compositeMsgs.get(0).getRuleName(), is("dummy"));
    }

    @Test
    public void testEvaluatorMessageWithNotExistsRule() {
        List<CompositeAlarmRule> compositeAlarmRules = new ArrayList<>();
        CompositeAlarmRule compositeAlarmRule = new CompositeAlarmRule("dummy", "a_rule && not_exist_rule", "composite rule triggered!");
        compositeAlarmRules.add(compositeAlarmRule);
        List<AlarmMessage> alarmMessages = getAlarmMessages();
        List<AlarmMessage> compositeMsgs = ruleEvaluator.evaluator(compositeAlarmRules, alarmMessages);
        assertThat(compositeMsgs.size(), is(0));
    }

    @Test
    public void testEvaluatorMessageWithException() {
        List<CompositeAlarmRule> compositeAlarmRules = new ArrayList<>();
        CompositeAlarmRule compositeAlarmRule = new CompositeAlarmRule("dummy", "a_rule + b_rule", "composite rule triggered!");
        compositeAlarmRules.add(compositeAlarmRule);
        List<AlarmMessage> alarmMessages = getAlarmMessages();
        List<AlarmMessage> compositeMsgs = ruleEvaluator.evaluator(compositeAlarmRules, alarmMessages);
        assertThat(compositeMsgs.size(), is(0));
    }

    private List<AlarmMessage> getAlarmMessages() {
        List<AlarmMessage> alarmMessages = new ArrayList<>();
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setRuleName("a_rule");
        alarmMessage.setOnlyAsCondition(true);
        alarmMessage.setId0("11");
        alarmMessage.setName("");
        alarmMessage.setScope("");
        alarmMessage.setScopeId(1);
        alarmMessages.add(alarmMessage);
        alarmMessage = new AlarmMessage();
        alarmMessage.setRuleName("b_rule");
        alarmMessage.setOnlyAsCondition(true);
        alarmMessage.setId0("11");
        alarmMessage.setName("");
        alarmMessage.setScope("");
        alarmMessage.setScopeId(1);
        alarmMessages.add(alarmMessage);
        return alarmMessages;
    }

    @Test
    public void testEvaluatorMessageWithOrOp() {
        List<CompositeAlarmRule> compositeAlarmRules = new ArrayList<>();
        CompositeAlarmRule compositeAlarmRule = new CompositeAlarmRule("dummy", "a_rule || b_rule", "composite rule triggered!");
        compositeAlarmRules.add(compositeAlarmRule);
        List<AlarmMessage> alarmMessages = getAlarmMessages();
        alarmMessages.remove(0);
        List<AlarmMessage> compositeMsgs = ruleEvaluator.evaluator(compositeAlarmRules, alarmMessages);
        assertThat(compositeMsgs.size(), is(1));
        assertThat(compositeMsgs.get(0).getAlarmMessage(), is("composite rule triggered!"));
        assertThat(compositeMsgs.get(0).getRuleName(), is("dummy"));
    }
}