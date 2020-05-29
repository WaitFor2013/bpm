/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.flowable.engine.test.api.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.cmmn.api.CallbackTypes;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.common.engine.impl.util.CollectionUtil;
import org.flowable.engine.history.DeleteReason;
import org.flowable.engine.history.HistoricDetail;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.HistoricDetailVariableInstanceUpdateEntity;
import org.flowable.engine.impl.test.HistoryTestHelper;
import org.flowable.engine.impl.test.JobTestHelper;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.engine.test.Deployment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.Test;

/**
 * @author Frederik Heremans
 * @author Joram Barrez
 */
public class RuntimeServiceTest extends PluggableFlowableTestCase {

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceWithVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("basicType", new DummySerializable());
        runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);
        org.flowable.task.api.Task task = taskService.createTaskQuery().includeProcessVariables().singleResult();
        assertNotNull(task.getProcessVariables());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceWithLongStringVariable() {
        Map<String, Object> vars = new HashMap<>();
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 4001; i++) {
            longString.append("c");
        }
        vars.put("longString", longString.toString());
        runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);
        org.flowable.task.api.Task task = taskService.createTaskQuery().includeProcessVariables().singleResult();
        assertNotNull(task.getProcessVariables());
        assertEquals(longString.toString(), task.getProcessVariables().get("longString"));
    }

    @Test
    public void testStartProcessInstanceByKeyNullKey() {
        try {
            runtimeService.startProcessInstanceByKey(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException e) {
            // Expected exception
        }
    }

    @Test
    public void testStartProcessInstanceByKeyUnexistingKey() {
        try {
            runtimeService.startProcessInstanceByKey("unexistingkey");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("No process definition found for key 'unexistingkey'", ae.getMessage());
            assertEquals(ProcessDefinition.class, ae.getObjectClass());
        }
    }

    @Test
    public void testStartProcessInstanceByIdNullId() {
        try {
            runtimeService.startProcessInstanceById(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException e) {
            // Expected exception
        }
    }

    @Test
    public void testStartProcessInstanceByIdUnexistingId() {
        try {
            runtimeService.startProcessInstanceById("unexistingId");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("No process definition found for id = 'unexistingId'", ae.getMessage());
            assertEquals(ProcessDefinition.class, ae.getObjectClass());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceByIdNullVariables() {
        runtimeService.startProcessInstanceByKey("oneTaskProcess", (Map<String, Object>) null);
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceWithBusinessKey() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();

        // by key
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", "123");
        assertNotNull(processInstance);
        assertEquals("123", processInstance.getBusinessKey());
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        // by key with variables
        processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", "456", CollectionUtil.singletonMap("var", "value"));
        assertNotNull(processInstance);
        assertEquals(2, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("value", runtimeService.getVariable(processInstance.getId(), "var"));

        // by id
        processInstance = runtimeService.startProcessInstanceById(processDefinition.getId(), "789");
        assertNotNull(processInstance);
        assertEquals(3, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        // by id with variables
        processInstance = runtimeService.startProcessInstanceById(processDefinition.getId(), "101123", CollectionUtil.singletonMap("var", "value2"));
        assertNotNull(processInstance);
        assertEquals(4, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "var"));
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceByProcessInstanceBuilder() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();

        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        // by key
        ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").businessKey("123").start();
        assertNotNull(processInstance);
        assertEquals("123", processInstance.getBusinessKey());
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        // by key, with processInstance name with variables
        processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").businessKey("456").variable("var", "value").name("processName1")
            .start();
        assertNotNull(processInstance);
        assertEquals(2, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("processName1", processInstance.getName());
        assertEquals("456", processInstance.getBusinessKey());
        assertEquals("value", runtimeService.getVariable(processInstance.getId(), "var"));

        processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        // by id
        processInstance = processInstanceBuilder.processDefinitionId(processDefinition.getId()).businessKey("789").start();
        assertNotNull(processInstance);
        assertEquals(3, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("789", processInstance.getBusinessKey());

        processInstanceBuilder = runtimeService.createProcessInstanceBuilder();
        // by id with variables
        processInstance = processInstanceBuilder.processDefinitionId(processDefinition.getId()).businessKey("101123").variable("var", "value2").start();
        assertNotNull(processInstance);
        assertEquals(4, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "var"));
        assertEquals("101123", processInstance.getBusinessKey());

        processInstanceBuilder = runtimeService.createProcessInstanceBuilder();
        // by id and processInstance name
        processInstance = processInstanceBuilder.processDefinitionId(processDefinition.getId()).businessKey("101124").name("processName2").start();
        assertNotNull(processInstance);
        assertEquals(5, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("processName2", processInstance.getName());
        assertEquals("101124", processInstance.getBusinessKey());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceByProcessInstanceBuilderAsync() {
        repositoryService.createProcessDefinitionQuery().singleResult();

        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        // by key
        ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").businessKey("123").startAsync();
        assertNotNull(processInstance);
        assertEquals("123", processInstance.getBusinessKey());
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("Process is started, but its execution waits on the job",
            0, taskService.createTaskQuery().processInstanceId(processInstance.getId()).count());

        JobTestHelper.waitForJobExecutorToProcessAllJobs(processEngineConfiguration, managementService, 2000, 200);
        assertEquals("The task is created from the job execution",
            1, taskService.createTaskQuery().processInstanceId(processInstance.getId()).count());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceByProcessInstanceBuilderAsyncWithDefinitionId() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();

        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        // by definitionId
        ProcessInstance processInstance = processInstanceBuilder.processDefinitionId(processDefinition.getId()).businessKey("123").startAsync();
        assertNotNull(processInstance);
        assertEquals("123", processInstance.getBusinessKey());
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());
        assertEquals("Process is started, but its execution waits on the job",
            0, taskService.createTaskQuery().processInstanceId(processInstance.getId()).count());

        JobTestHelper.waitForJobExecutorToProcessAllJobs(processEngineConfiguration, managementService, 2000, 200);
        assertEquals("The task is created from the job execution",
            1, taskService.createTaskQuery().processInstanceId(processInstance.getId()).count());
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderAsyncWithoutKeyAndDefinitionId() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(() -> processInstanceBuilder.startAsync())
            .isExactlyInstanceOf(FlowableIllegalArgumentException.class)
            .hasMessage("No processDefinitionId, processDefinitionKey provided");
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderAsyncWithNonExistingDefKey() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(() -> processInstanceBuilder.processDefinitionKey("nonExistingKey").startAsync())
            .isExactlyInstanceOf(FlowableObjectNotFoundException.class)
            .hasMessage("No process definition found for key 'nonExistingKey'");
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderAsyncWithNonExistingDefId() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(() -> processInstanceBuilder.processDefinitionId("nonExistingDefinitionId").startAsync())
            .isExactlyInstanceOf(FlowableObjectNotFoundException.class)
            .hasMessage("No process definition found for id = 'nonExistingDefinitionId'");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testProcessInstanceDefinitionInformation() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").start();

        assertNotNull(processInstance);
        assertEquals(processDefinition.getDeploymentId(), processInstance.getDeploymentId());
        assertEquals(processDefinition.getId(), processInstance.getProcessDefinitionId());
        assertEquals(processDefinition.getKey(), processInstance.getProcessDefinitionKey());
        assertEquals(processDefinition.getVersion(), processInstance.getProcessDefinitionVersion().intValue());
        assertEquals(processDefinition.getName(), processInstance.getProcessDefinitionName());
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderWithTenantId() {
        org.flowable.engine.repository.Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml").
                tenantId("flowable").
                deploy();
        try {
            ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

            ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").businessKey("123").
                tenantId("flowable").start();
            assertNotNull(processInstance);
            assertEquals("123", processInstance.getBusinessKey());
            assertEquals("flowable", processInstance.getTenantId());

            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task.getTenantId(), is("flowable"));
        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderWithOverrideTenantId() {
        org.flowable.engine.repository.Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml")
            .tenantId("flowable")
            .deploy();

        try {
            ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

            ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess")
                .businessKey("123")
                .tenantId("flowable")
                .overrideProcessDefinitionTenantId("customTenant")
                .start();

            assertNotNull(processInstance);
            assertEquals("123", processInstance.getBusinessKey());
            assertEquals("customTenant", processInstance.getTenantId());

            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task.getTenantId(), is("customTenant"));

        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    public void testStartProcessInstanceByProcessInstanceBuilderWithDefaultDefinitionTenantId() {
        org.flowable.engine.repository.Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml")
            .deploy();

        try {
            ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

            ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess")
                .businessKey("123")
                .overrideProcessDefinitionTenantId("customTenant")
                .start();

            assertNotNull(processInstance);
            assertEquals("123", processInstance.getBusinessKey());
            assertEquals("customTenant", processInstance.getTenantId());

            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task.getTenantId(), is("customTenant"));

        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testNonUniqueBusinessKey() {
        runtimeService.startProcessInstanceByKey("oneTaskProcess", "123");

        // Behaviour changed: https://activiti.atlassian.net/browse/ACT-1860
        runtimeService.startProcessInstanceByKey("oneTaskProcess", "123");
        assertEquals(2, runtimeService.createProcessInstanceQuery().processInstanceBusinessKey("123").count());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceFormWithoutFormKey() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("basicType", new DummySerializable());

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();

        runtimeService.startProcessInstanceWithForm(processDefinition.getId(), null, vars, null);
        org.flowable.task.api.Task task = taskService.createTaskQuery().includeProcessVariables().singleResult();
        assertNotNull(task.getProcessVariables());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceFormWithoutVariables() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();
        ProcessInstance processInstance = runtimeService.startProcessInstanceWithForm(processDefinition.getId(), "outcome", null, null);
        assertEquals(0, runtimeService.getVariables(processInstance.getId()).size());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartProcessInstanceFormWithEmptyVariables() {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().singleResult();
        ProcessInstance processInstance = runtimeService.startProcessInstanceWithForm(processDefinition.getId(), "outcome", new HashMap<>(), null);
        assertEquals(0, runtimeService.getVariables(processInstance.getId()).size());
    }

    // some databases might react strange on having multiple times null for the
    // business key
    // when the unique constraint is {processDefinitionId, businessKey}
    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testMultipleNullBusinessKeys() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        assertNull(processInstance.getBusinessKey());

        runtimeService.startProcessInstanceByKey("oneTaskProcess");
        runtimeService.startProcessInstanceByKey("oneTaskProcess");

        assertEquals(3, runtimeService.createProcessInstanceQuery().count());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testDeleteProcessInstance() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        String deleteReason = "testing instance deletion";
        runtimeService.deleteProcessInstance(processInstance.getId(), deleteReason);
        assertEquals(0, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        // test that the delete reason of the process instance shows up as
        // delete reason of the task in history ACT-848
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {

            HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery().processInstanceId(processInstance.getId())
                .singleResult();

            assertEquals(deleteReason, historicTaskInstance.getDeleteReason());

            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstance.getId())
                .singleResult();

            assertNotNull(historicInstance);
            assertEquals(deleteReason, historicInstance.getDeleteReason());
            assertNotNull(historicInstance.getEndTime());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testDeleteProcessInstanceNullReason() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        // Deleting without a reason should be possible
        runtimeService.deleteProcessInstance(processInstance.getId(), null);
        assertEquals(0, runtimeService.createProcessInstanceQuery().processDefinitionKey("oneTaskProcess").count());

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstance.getId())
                .singleResult();

            assertNotNull(historicInstance);
            assertEquals(DeleteReason.PROCESS_INSTANCE_DELETED, historicInstance.getDeleteReason());
        }
    }

    @Test
    public void testDeleteProcessInstanceUnexistingId() {
        try {
            runtimeService.deleteProcessInstance("enexistingInstanceId", null);
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("No process instance found for id", ae.getMessage());
            assertEquals(ProcessInstance.class, ae.getObjectClass());
        }
    }

    @Test
    public void testDeleteProcessInstanceNullId() {
        try {
            runtimeService.deleteProcessInstance(null, "test null id delete");
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("processInstanceId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testFindActiveActivityIds() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

        List<String> activities = runtimeService.getActiveActivityIds(processInstance.getId());
        assertNotNull(activities);
        assertEquals(1, activities.size());
    }

    @Test
    public void testFindActiveActivityIdsUnexistingExecututionId() {
        try {
            runtimeService.getActiveActivityIds("unexistingExecutionId");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingExecutionId doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testFindActiveActivityIdsNullExecututionId() {
        try {
            runtimeService.getActiveActivityIds(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    /**
     * Testcase to reproduce ACT-950 (https://jira.codehaus.org/browse/ACT-950)
     */
    @Test
    @Deployment
    public void testFindActiveActivityIdProcessWithErrorEventAndSubProcess() {
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("errorEventSubprocess");

        List<String> activeActivities = runtimeService.getActiveActivityIds(processInstance.getId());
        assertEquals(5, activeActivities.size());

        List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().list();
        assertEquals(2, tasks.size());

        org.flowable.task.api.Task parallelUserTask = null;
        for (org.flowable.task.api.Task task : tasks) {
            if (!task.getName().equals("ParallelUserTask") && !task.getName().equals("MainUserTask")) {
                fail("Expected: <ParallelUserTask> or <MainUserTask> but was <" + task.getName() + ">.");
            }
            if (task.getName().equals("ParallelUserTask")) {
                parallelUserTask = task;
            }
        }
        assertNotNull(parallelUserTask);

        taskService.complete(parallelUserTask.getId());

        Execution execution = runtimeService.createExecutionQuery().processInstanceId(processInstance.getId()).activityId("subprocess1WaitBeforeError")
            .singleResult();
        runtimeService.trigger(execution.getId());

        activeActivities = runtimeService.getActiveActivityIds(processInstance.getId());
        assertEquals(4, activeActivities.size());

        tasks = taskService.createTaskQuery().list();
        assertEquals(2, tasks.size());

        org.flowable.task.api.Task beforeErrorUserTask = null;
        for (org.flowable.task.api.Task task : tasks) {
            if (!task.getName().equals("BeforeError") && !task.getName().equals("MainUserTask")) {
                fail("Expected: <BeforeError> or <MainUserTask> but was <" + task.getName() + ">.");
            }
            if (task.getName().equals("BeforeError")) {
                beforeErrorUserTask = task;
            }
        }
        assertNotNull(beforeErrorUserTask);

        taskService.complete(beforeErrorUserTask.getId());

        activeActivities = runtimeService.getActiveActivityIds(processInstance.getId());
        assertEquals(2, activeActivities.size());

        tasks = taskService.createTaskQuery().list();
        assertEquals(2, tasks.size());

        org.flowable.task.api.Task afterErrorUserTask = null;
        for (org.flowable.task.api.Task task : tasks) {
            if (!task.getName().equals("AfterError") && !task.getName().equals("MainUserTask")) {
                fail("Expected: <AfterError> or <MainUserTask> but was <" + task.getName() + ">.");
            }
            if (task.getName().equals("AfterError")) {
                afterErrorUserTask = task;
            }
        }
        assertNotNull(afterErrorUserTask);

        taskService.complete(afterErrorUserTask.getId());

        tasks = taskService.createTaskQuery().list();
        assertEquals(1, tasks.size());
        assertEquals("MainUserTask", tasks.get(0).getName());

        activeActivities = runtimeService.getActiveActivityIds(processInstance.getId());
        assertEquals(1, activeActivities.size());
        assertEquals("MainUserTask", activeActivities.get(0));

        taskService.complete(tasks.get(0).getId());
        assertProcessEnded(processInstance.getId());
    }

    @Test
    public void testSignalUnexistingExecututionId() {
        try {
            runtimeService.trigger("unexistingExecutionId");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingExecutionId doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testSignalNullExecutionId() {
        try {
            runtimeService.trigger(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment
    public void testSignalWithProcessVariables() {

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testSignalWithProcessVariables");
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("variable", "value");

        // signal the execution while passing in the variables
        Execution execution = runtimeService.createExecutionQuery().activityId("receiveMessage").singleResult();
        runtimeService.trigger(execution.getId(), processVariables);

        Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
        assertEquals(processVariables, variables);

    }

    @Test
    public void testGetVariablesUnexistingExecutionId() {
        try {
            runtimeService.getVariables("unexistingExecutionId");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingExecutionId doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testGetVariablesNullExecutionId() {
        try {
            runtimeService.getVariables(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    public void testGetVariableUnexistingExecutionId() {
        try {
            runtimeService.getVariables("unexistingExecutionId");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingExecutionId doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testGetVariableNullExecutionId() {
        try {
            runtimeService.getVariables(null);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableUnexistingVariableName() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        Object variableValue = runtimeService.getVariable(processInstance.getId(), "unexistingVariable");
        assertNull(variableValue);
    }

    @Test
    public void testSetVariableUnexistingExecutionId() {
        try {
            runtimeService.setVariable("unexistingExecutionId", "variableName", "value");
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingExecutionId doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testSetVariableNullExecutionId() {
        try {
            runtimeService.setVariable(null, "variableName", "variableValue");
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testSetVariableNullVariableName() {
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
            runtimeService.setVariable(processInstance.getId(), null, "variableValue");
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("variableName is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testSetVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        runtimeService.setVariables(processInstance.getId(), vars);

        assertEquals("value1", runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "variable2"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSetVariablesUnexistingExecutionId() {
        try {
            runtimeService.setVariables("unexistingexecution", Collections.EMPTY_MAP);
            fail("ActivitiException expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertTextPresent("execution unexistingexecution doesn't exist", ae.getMessage());
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSetVariablesNullExecutionId() {
        try {
            runtimeService.setVariables(null, Collections.EMPTY_MAP);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    private void checkHistoricVariableUpdateEntity(String variableName, String processInstanceId) {
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.FULL, processEngineConfiguration)) {
            boolean deletedVariableUpdateFound = false;

            List<HistoricDetail> resultSet = historyService.createHistoricDetailQuery().processInstanceId(processInstanceId).list();
            for (HistoricDetail currentHistoricDetail : resultSet) {
                assertTrue(currentHistoricDetail instanceof HistoricDetailVariableInstanceUpdateEntity);
                HistoricDetailVariableInstanceUpdateEntity historicVariableUpdate = (HistoricDetailVariableInstanceUpdateEntity) currentHistoricDetail;

                if (historicVariableUpdate.getName().equals(variableName)) {
                    if (historicVariableUpdate.getValue() == null) {
                        if (deletedVariableUpdateFound) {
                            fail("Mismatch: A HistoricVariableUpdateEntity with a null value already found");
                        } else {
                            deletedVariableUpdateFound = true;
                        }
                    }
                }
            }

            assertTrue(deletedVariableUpdateFound);
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testRemoveVariable() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        runtimeService.setVariables(processInstance.getId(), vars);

        runtimeService.removeVariable(processInstance.getId(), "variable1");

        assertNull(runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "variable2"));

        checkHistoricVariableUpdateEntity("variable1", processInstance.getId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneSubProcess.bpmn20.xml" })
    public void testRemoveVariableInParentScope() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("startSimpleSubProcess", vars);
        org.flowable.task.api.Task currentTask = taskService.createTaskQuery().singleResult();

        runtimeService.removeVariable(currentTask.getExecutionId(), "variable1");

        assertNull(runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "variable2"));

        checkHistoricVariableUpdateEntity("variable1", processInstance.getId());
    }

    @Test
    public void testRemoveVariableNullExecutionId() {
        try {
            runtimeService.removeVariable(null, "variable");
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testRemoveVariableLocal() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);
        runtimeService.removeVariableLocal(processInstance.getId(), "variable1");

        assertNull(runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "variable2"));

        checkHistoricVariableUpdateEntity("variable1", processInstance.getId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneSubProcess.bpmn20.xml" })
    public void testRemoveVariableLocalWithParentScope() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("startSimpleSubProcess", vars);
        org.flowable.task.api.Task currentTask = taskService.createTaskQuery().singleResult();
        runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "local value");

        assertEquals("local value", runtimeService.getVariableLocal(currentTask.getExecutionId(), "localVariable"));

        runtimeService.removeVariableLocal(currentTask.getExecutionId(), "localVariable");

        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "localVariable"));
        assertNull(runtimeService.getVariableLocal(currentTask.getExecutionId(), "localVariable"));

        assertEquals("value1", runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(processInstance.getId(), "variable2"));

        assertEquals("value1", runtimeService.getVariable(currentTask.getExecutionId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(currentTask.getExecutionId(), "variable2"));

        checkHistoricVariableUpdateEntity("localVariable", processInstance.getId());
    }

    @Test
    public void testRemoveLocalVariableNullExecutionId() {
        try {
            runtimeService.removeVariableLocal(null, "variable");
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testRemoveVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);
        runtimeService.setVariable(processInstance.getId(), "variable3", "value3");

        runtimeService.removeVariables(processInstance.getId(), vars.keySet());

        assertNull(runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariable(processInstance.getId(), "variable2"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable2"));

        assertEquals("value3", runtimeService.getVariable(processInstance.getId(), "variable3"));
        assertEquals("value3", runtimeService.getVariableLocal(processInstance.getId(), "variable3"));

        checkHistoricVariableUpdateEntity("variable1", processInstance.getId());
        checkHistoricVariableUpdateEntity("variable2", processInstance.getId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneSubProcess.bpmn20.xml" })
    public void testRemoveVariablesWithParentScope() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("startSimpleSubProcess", vars);
        runtimeService.setVariable(processInstance.getId(), "variable3", "value3");

        org.flowable.task.api.Task currentTask = taskService.createTaskQuery().singleResult();

        runtimeService.removeVariables(currentTask.getExecutionId(), vars.keySet());

        assertNull(runtimeService.getVariable(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable1"));
        assertNull(runtimeService.getVariable(processInstance.getId(), "variable2"));
        assertNull(runtimeService.getVariableLocal(processInstance.getId(), "variable2"));

        assertEquals("value3", runtimeService.getVariable(processInstance.getId(), "variable3"));
        assertEquals("value3", runtimeService.getVariableLocal(processInstance.getId(), "variable3"));

        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "variable1"));
        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "variable2"));

        assertEquals("value3", runtimeService.getVariable(currentTask.getExecutionId(), "variable3"));

        checkHistoricVariableUpdateEntity("variable1", processInstance.getId());
        checkHistoricVariableUpdateEntity("variable2", processInstance.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveVariablesNullExecutionId() {
        try {
            runtimeService.removeVariables(null, Collections.EMPTY_LIST);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneSubProcess.bpmn20.xml" })
    public void testRemoveVariablesLocalWithParentScope() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("variable1", "value1");
        vars.put("variable2", "value2");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("startSimpleSubProcess", vars);

        org.flowable.task.api.Task currentTask = taskService.createTaskQuery().singleResult();
        Map<String, Object> varsToDelete = new HashMap<>();
        varsToDelete.put("variable3", "value3");
        varsToDelete.put("variable4", "value4");
        varsToDelete.put("variable5", "value5");
        runtimeService.setVariablesLocal(currentTask.getExecutionId(), varsToDelete);
        runtimeService.setVariableLocal(currentTask.getExecutionId(), "variable6", "value6");

        assertEquals("value3", runtimeService.getVariable(currentTask.getExecutionId(), "variable3"));
        assertEquals("value3", runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable3"));
        assertEquals("value4", runtimeService.getVariable(currentTask.getExecutionId(), "variable4"));
        assertEquals("value4", runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable4"));
        assertEquals("value5", runtimeService.getVariable(currentTask.getExecutionId(), "variable5"));
        assertEquals("value5", runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable5"));
        assertEquals("value6", runtimeService.getVariable(currentTask.getExecutionId(), "variable6"));
        assertEquals("value6", runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable6"));

        runtimeService.removeVariablesLocal(currentTask.getExecutionId(), varsToDelete.keySet());

        assertEquals("value1", runtimeService.getVariable(currentTask.getExecutionId(), "variable1"));
        assertEquals("value2", runtimeService.getVariable(currentTask.getExecutionId(), "variable2"));

        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "variable3"));
        assertNull(runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable3"));
        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "variable4"));
        assertNull(runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable4"));
        assertNull(runtimeService.getVariable(currentTask.getExecutionId(), "variable5"));
        assertNull(runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable5"));

        assertEquals("value6", runtimeService.getVariable(currentTask.getExecutionId(), "variable6"));
        assertEquals("value6", runtimeService.getVariableLocal(currentTask.getExecutionId(), "variable6"));

        checkHistoricVariableUpdateEntity("variable3", processInstance.getId());
        checkHistoricVariableUpdateEntity("variable4", processInstance.getId());
        checkHistoricVariableUpdateEntity("variable5", processInstance.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveVariablesLocalNullExecutionId() {
        try {
            runtimeService.removeVariablesLocal(null, Collections.EMPTY_LIST);
            fail("ActivitiException expected");
        } catch (FlowableIllegalArgumentException ae) {
            assertTextPresent("executionId is null", ae.getMessage());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/runtime/RuntimeServiceTest.catchAlertSignal.bpmn20.xml",
        "org/flowable/engine/test/api/runtime/RuntimeServiceTest.catchPanicSignal.bpmn20.xml" })
    public void testSignalEventReceived() {

        startSignalCatchProcesses();
        // 15, because the signal catch is a scope
        assertEquals(15, runtimeService.createExecutionQuery().count());
        runtimeService.signalEventReceived("alert");
        assertEquals(9, runtimeService.createExecutionQuery().count());
        runtimeService.signalEventReceived("panic");
        assertEquals(0, runtimeService.createExecutionQuery().count());

        // //// test signalEventReceived(String, String)
        startSignalCatchProcesses();

        // signal the executions one at a time:
        for (int executions = 3; executions > 0; executions--) {
            List<Execution> page = runtimeService.createExecutionQuery().signalEventSubscriptionName("alert").listPage(0, 1);
            runtimeService.signalEventReceived("alert", page.get(0).getId());

            assertEquals(executions - 1, runtimeService.createExecutionQuery().signalEventSubscriptionName("alert").count());
        }

        for (int executions = 3; executions > 0; executions--) {
            List<Execution> page = runtimeService.createExecutionQuery().signalEventSubscriptionName("panic").listPage(0, 1);
            runtimeService.signalEventReceived("panic", page.get(0).getId());

            assertEquals(executions - 1, runtimeService.createExecutionQuery().signalEventSubscriptionName("panic").count());
        }

    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/runtime/RuntimeServiceTest.catchAlertMessage.bpmn20.xml",
        "org/flowable/engine/test/api/runtime/RuntimeServiceTest.catchPanicMessage.bpmn20.xml" })
    public void testMessageEventReceived() {

        startMessageCatchProcesses();
        // 12, because the signal catch is a scope
        assertEquals(12, runtimeService.createExecutionQuery().count());

        // signal the executions one at a time:
        for (int executions = 3; executions > 0; executions--) {
            List<Execution> page = runtimeService.createExecutionQuery().messageEventSubscriptionName("alert").listPage(0, 1);
            runtimeService.messageEventReceived("alert", page.get(0).getId());

            assertEquals(executions - 1, runtimeService.createExecutionQuery().messageEventSubscriptionName("alert").count());
        }

        for (int executions = 3; executions > 0; executions--) {
            List<Execution> page = runtimeService.createExecutionQuery().messageEventSubscriptionName("panic").listPage(0, 1);
            runtimeService.messageEventReceived("panic", page.get(0).getId());

            assertEquals(executions - 1, runtimeService.createExecutionQuery().messageEventSubscriptionName("panic").count());
        }

    }

    @Test
    public void testSignalEventReceivedNonExistingExecution() {
        try {
            runtimeService.signalEventReceived("alert", "nonexistingExecution");
            fail("exception expected");
        } catch (FlowableObjectNotFoundException ae) {
            // this is good
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    public void testMessageEventReceivedNonExistingExecution() {
        try {
            runtimeService.messageEventReceived("alert", "nonexistingExecution");
            fail("exception expected");
        } catch (FlowableObjectNotFoundException ae) {
            assertEquals(Execution.class, ae.getObjectClass());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/runtime/RuntimeServiceTest.catchAlertSignal.bpmn20.xml" })
    public void testExecutionWaitingForDifferentSignal() {
        runtimeService.startProcessInstanceByKey("catchAlertSignal");
        Execution execution = runtimeService.createExecutionQuery().signalEventSubscriptionName("alert").singleResult();
        try {
            runtimeService.signalEventReceived("bogusSignal", execution.getId());
            fail("exception expected");
        } catch (FlowableException e) {
            // this is good
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testSetProcessInstanceName() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        assertNotNull(processInstance);
        assertNull(processInstance.getName());

        // Set the name
        runtimeService.setProcessInstanceName(processInstance.getId(), "New name");
        processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
        assertNotNull(processInstance);
        assertEquals("New name", processInstance.getName());

        // Set the name to null
        runtimeService.setProcessInstanceName(processInstance.getId(), null);
        processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
        assertNotNull(processInstance);
        assertNull(processInstance.getName());

        // Set name for unexisting process instance, should fail
        try {
            runtimeService.setProcessInstanceName("unexisting", null);
            fail("Exception expected");
        } catch (FlowableObjectNotFoundException aonfe) {
            assertEquals(ProcessInstance.class, aonfe.getObjectClass());
        }

        processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
        assertNotNull(processInstance);
        assertNull(processInstance.getName());

        // Set name for suspended process instance, should fail
        runtimeService.suspendProcessInstanceById(processInstance.getId());
        try {
            runtimeService.setProcessInstanceName(processInstance.getId(), null);
            fail("Exception expected");
        } catch (FlowableException ae) {
            assertEquals("process instance " + processInstance.getId() + " is suspended, cannot set name", ae.getMessage());
        }

        processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
        assertNotNull(processInstance);
        assertNull(processInstance.getName());
    }

    private void startSignalCatchProcesses() {
        for (int i = 0; i < 3; i++) {
            runtimeService.startProcessInstanceByKey("catchAlertSignal");
            runtimeService.startProcessInstanceByKey("catchPanicSignal");
        }
    }

    private void startMessageCatchProcesses() {
        for (int i = 0; i < 3; i++) {
            runtimeService.startProcessInstanceByKey("catchAlertMessage");
            runtimeService.startProcessInstanceByKey("catchPanicMessage");
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableUnexistingVariableNameWithCast() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        String variableValue = runtimeService.getVariable(processInstance.getId(), "unexistingVariable", String.class);
        assertNull(variableValue);
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableExistingVariableNameWithCast() {
        Map<String, Object> params = new HashMap<>();
        params.put("var1", true);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", params);
        Boolean variableValue = runtimeService.getVariable(processInstance.getId(), "var1", Boolean.class);
        assertTrue(variableValue);
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableExistingVariableNameWithInvalidCast() {
        Map<String, Object> params = new HashMap<>();
        params.put("var1", true);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", params);
        assertThatThrownBy(() -> runtimeService.getVariable(processInstance.getId(), "var1", String.class))
            .isExactlyInstanceOf(ClassCastException.class);
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableLocalUnexistingVariableNameWithCast() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        String variableValue = runtimeService.getVariableLocal(processInstance.getId(), "var1", String.class);
        assertNull(variableValue);
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableLocalExistingVariableNameWithCast() {
        Map<String, Object> params = new HashMap<>();
        params.put("var1", true);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", params);
        Boolean variableValue = runtimeService.getVariableLocal(processInstance.getId(), "var1", Boolean.class);
        assertTrue(variableValue);
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testGetVariableLocalExistingVariableNameWithInvalidCast() {
        Map<String, Object> params = new HashMap<>();
        params.put("var1", true);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", params);
        assertThatThrownBy(() -> runtimeService.getVariableLocal(processInstance.getId(), "var1", String.class))
            .isExactlyInstanceOf(ClassCastException.class);
    }

    // Test for https://activiti.atlassian.net/browse/ACT-2186
    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testHistoricVariableRemovedWhenRuntimeVariableIsRemoved() {
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.AUDIT, processEngineConfiguration)) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("var1", "Hello");
            vars.put("var2", "World");
            vars.put("var3", "!");
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);

            // Verify runtime
            assertEquals(3, runtimeService.getVariables(processInstance.getId()).size());
            assertEquals(3, runtimeService.getVariables(processInstance.getId(), Arrays.asList("var1", "var2", "var3")).size());
            assertNotNull(runtimeService.getVariable(processInstance.getId(), "var2"));

            waitForHistoryJobExecutorToProcessAllJobs(7000, 100);

            // Verify history
            assertEquals(3, historyService.createHistoricVariableInstanceQuery().list().size());
            assertNotNull(historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstance.getId()).variableName("var2").singleResult());

            // Remove one variable
            runtimeService.removeVariable(processInstance.getId(), "var2");

            waitForHistoryJobExecutorToProcessAllJobs(7000, 100);

            // Verify runtime
            assertEquals(2, runtimeService.getVariables(processInstance.getId()).size());
            assertEquals(2, runtimeService.getVariables(processInstance.getId(), Arrays.asList("var1", "var2", "var3")).size());
            assertNull(runtimeService.getVariable(processInstance.getId(), "var2"));

            // Verify history
            assertEquals(2, historyService.createHistoricVariableInstanceQuery().list().size());
            assertNull(historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstance.getId()).variableName("var2").singleResult());
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartTimeProcessInstance() {
        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2010);
        calendar.set(Calendar.MONTH, 8);
        calendar.set(Calendar.DAY_OF_MONTH, 30);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date noon = calendar.getTime();

        processEngineConfiguration.getClock().setCurrentTime(noon);
        final ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

        assertEquals(noon, processInstance.getStartTime());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testAuthenticatedStartUserProcessInstance() {
        final String authenticatedUser = "user1";
        identityService.setAuthenticatedUserId(authenticatedUser);
        final ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

        assertEquals(authenticatedUser, processInstance.getStartUserId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testNoAuthenticatedStartUserProcessInstance() {
        final ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

        assertNull(processInstance.getStartUserId());
    }

    @Test
    @Deployment(resources = "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml")
    public void testAdhocCallbacks() {
        ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder().processDefinitionKey("oneTaskProcess").
            callbackId("nonExistingCase").
            callbackType(CallbackTypes.CASE_ADHOC_CHILD).
            start();

        assertThat(processInstance.getCallbackId(), is("nonExistingCase"));
        assertThat(processInstance.getCallbackType(), is(CallbackTypes.CASE_ADHOC_CHILD));
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartByProcessInstanceBuilderWithFallbackToDefaultTenant() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").
            tenantId("flowable").
            fallbackToDefaultTenant().
            start();

        assertThat(processInstance, is(notNullValue()));
    }

    @Test
    public void testStartByProcessInstanceBuilderWithFallbackToDefaultTenant_definitionNotFound() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(
            () -> processInstanceBuilder.processDefinitionKey("nonExistingDefinition").
                tenantId("flowable").
                fallbackToDefaultTenant().
                start()
        ).
            isExactlyInstanceOf(FlowableObjectNotFoundException.class).
            hasMessage("No process definition found for key 'nonExistingDefinition'. Fallback to default tenant was also applied.");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" },
        tenantId = "nonDefaultTenant"
    )
    public void testStartByProcessInstanceBuilderWithFallbackToDefaultTenant_definitionNotFoundInNonDefaultTenant() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(
            () -> processInstanceBuilder.processDefinitionKey("oneTaskProcess").
                tenantId("flowable").
                fallbackToDefaultTenant().
                start()
        ).
            isExactlyInstanceOf(FlowableObjectNotFoundException.class).
            hasMessage("No process definition found for key 'oneTaskProcess'. Fallback to default tenant was also applied.");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" })
    public void testStartAsyncWithFallbackToDefaultTenant() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        ProcessInstance processInstance = processInstanceBuilder.processDefinitionKey("oneTaskProcess").
            tenantId("flowable").
            fallbackToDefaultTenant().
            startAsync();

        assertThat(processInstance, is(notNullValue()));
    }

    @Test
    public void testStartAsyncWithFallbackToDefaultTenant_definitionNotFound() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(
            () -> processInstanceBuilder.processDefinitionKey("nonExistingDefinition").
                tenantId("flowable").
                fallbackToDefaultTenant().
                startAsync()
        ).
            isExactlyInstanceOf(FlowableObjectNotFoundException.class).
            hasMessage("No process definition found for key 'nonExistingDefinition'. Fallback to default tenant was also applied.");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/api/oneTaskProcess.bpmn20.xml" },
        tenantId = "nonDefaultTenant"
    )
    public void testStartAsyncWithFallbackToDefaultTenant_definitionNotFoundInNonDefaultTenant() {
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();

        assertThatThrownBy(
            () -> processInstanceBuilder.processDefinitionKey("oneTaskProcess").
                tenantId("flowable").
                fallbackToDefaultTenant().
                startAsync()
        ).
            isExactlyInstanceOf(FlowableObjectNotFoundException.class).
            hasMessage("No process definition found for key 'oneTaskProcess'. Fallback to default tenant was also applied.");
    }

}
