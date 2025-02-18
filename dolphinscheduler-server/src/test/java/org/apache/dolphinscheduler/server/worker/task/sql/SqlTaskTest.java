/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.server.worker.task.sql;

import org.apache.dolphinscheduler.common.Constants;
import org.apache.dolphinscheduler.common.datasource.DatasourceUtil;
import org.apache.dolphinscheduler.common.utils.ParameterUtils;
import org.apache.dolphinscheduler.dao.AlertDao;
import org.apache.dolphinscheduler.remote.command.alert.AlertSendResponseCommand;
import org.apache.dolphinscheduler.server.entity.SQLTaskExecutionContext;
import org.apache.dolphinscheduler.server.entity.TaskExecutionContext;
import org.apache.dolphinscheduler.server.worker.task.TaskProps;
import org.apache.dolphinscheduler.service.alert.AlertClientService;
import org.apache.dolphinscheduler.service.bean.SpringApplicationContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  sql task test
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {SqlTask.class, DatasourceUtil.class, SpringApplicationContext.class,
        ParameterUtils.class, AlertSendResponseCommand.class})
public class SqlTaskTest {

    private static final Logger logger = LoggerFactory.getLogger(SqlTaskTest.class);

    private static final String CONNECTION_PARAMS = "{\"user\":\"root\",\"password\":\"123456\",\"address\":\"jdbc:mysql://127.0.0.1:3306\","
            + "\"database\":\"test\",\"jdbcUrl\":\"jdbc:mysql://127.0.0.1:3306/test\"}";

    private SqlTask sqlTask;

    private TaskExecutionContext taskExecutionContext;

    private AlertClientService alertClientService;
    @Before
    public void before() throws Exception {
        taskExecutionContext = new TaskExecutionContext();

        TaskProps props = new TaskProps();
        props.setExecutePath("/tmp");
        props.setTaskAppId(String.valueOf(System.currentTimeMillis()));
        props.setTaskInstanceId(1);
        props.setTenantCode("1");
        props.setEnvFile(".dolphinscheduler_env.sh");
        props.setTaskStartTime(new Date());
        props.setTaskTimeout(0);
        props.setTaskParams(
                "{\"localParams\":[{\"prop\":\"ret\", \"direct\":\"OUT\", \"type\":\"VARCHAR\", \"value\":\"\"}],"
                        + "\"type\":\"POSTGRESQL\",\"datasource\":1,\"sql\":\"insert into tb_1 values('1','2')\","
                        + "\"sqlType\":1}");

        taskExecutionContext = PowerMockito.mock(TaskExecutionContext.class);
        PowerMockito.when(taskExecutionContext.getTaskParams()).thenReturn(props.getTaskParams());
        PowerMockito.when(taskExecutionContext.getExecutePath()).thenReturn("/tmp");
        PowerMockito.when(taskExecutionContext.getTaskAppId()).thenReturn("1");
        PowerMockito.when(taskExecutionContext.getTenantCode()).thenReturn("root");
        PowerMockito.when(taskExecutionContext.getStartTime()).thenReturn(new Date());
        PowerMockito.when(taskExecutionContext.getTaskTimeout()).thenReturn(10000);
        PowerMockito.when(taskExecutionContext.getLogPath()).thenReturn("/tmp/dx");
        PowerMockito.when(taskExecutionContext.getVarPool()).thenReturn("[{\"direct\":\"IN\",\"prop\":\"test\",\"type\":\"VARCHAR\",\"value\":\"\"}]");

        SQLTaskExecutionContext sqlTaskExecutionContext = new SQLTaskExecutionContext();
        sqlTaskExecutionContext.setConnectionParams(CONNECTION_PARAMS);
        PowerMockito.when(taskExecutionContext.getSqlTaskExecutionContext()).thenReturn(sqlTaskExecutionContext);

        PowerMockito.mockStatic(SpringApplicationContext.class);
        PowerMockito.when(SpringApplicationContext.getBean(Mockito.any())).thenReturn(new AlertDao());
        alertClientService = PowerMockito.mock(AlertClientService.class);
        sqlTask = new SqlTask(taskExecutionContext, logger, alertClientService);
        sqlTask.getParameters().setVarPool(taskExecutionContext.getVarPool());
        sqlTask.init();
    }

    @Test
    public void testGetParameters() {
        Assert.assertNotNull(sqlTask.getParameters());
    }

    @Test
    public void testHandle() throws Exception {
        Connection connection = PowerMockito.mock(Connection.class);
        PreparedStatement preparedStatement = PowerMockito.mock(PreparedStatement.class);
        PowerMockito.when(connection.prepareStatement(Mockito.any())).thenReturn(preparedStatement);
        PowerMockito.mockStatic(ParameterUtils.class);
        PowerMockito.when(ParameterUtils.replaceScheduleTime(Mockito.any(), Mockito.any())).thenReturn("insert into tb_1 values('1','2')");
        PowerMockito.mockStatic(DatasourceUtil.class);
        PowerMockito.when(DatasourceUtil.getConnection(Mockito.any(), Mockito.any())).thenReturn(connection);

        sqlTask.handle();
        Assert.assertEquals(Constants.EXIT_CODE_SUCCESS, sqlTask.getExitStatusCode());
    }

    @Test
    public void testResultProcess() throws Exception {
        // test input null and will not throw a exception
        AlertSendResponseCommand mockResponseCommand = PowerMockito.mock(AlertSendResponseCommand.class);
        PowerMockito.when(mockResponseCommand.getResStatus()).thenReturn(true);
        PowerMockito.when(alertClientService.sendAlert(0, "null query result sets", "[]")).thenReturn(mockResponseCommand);
        String result = Whitebox.invokeMethod(sqlTask, "resultProcess", null);
        Assert.assertNotNull(result);
    }

    @Test
    public void testResultProcess02() throws Exception {
        // test input not null
        ResultSet resultSet = PowerMockito.mock(ResultSet.class);
        ResultSetMetaData mockResultMetaData = PowerMockito.mock(ResultSetMetaData.class);
        PowerMockito.when(resultSet.getMetaData()).thenReturn(mockResultMetaData);
        PowerMockito.when(mockResultMetaData.getColumnCount()).thenReturn(2);
        PowerMockito.when(resultSet.next()).thenReturn(true);
        PowerMockito.when(resultSet.getObject(Mockito.anyInt())).thenReturn(1);
        PowerMockito.when(mockResultMetaData.getColumnLabel(Mockito.anyInt())).thenReturn("a");

        AlertSendResponseCommand mockResponseCommand = PowerMockito.mock(AlertSendResponseCommand.class);
        PowerMockito.when(mockResponseCommand.getResStatus()).thenReturn(true);
        PowerMockito.when(alertClientService.sendAlert(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponseCommand);

        String result = Whitebox.invokeMethod(sqlTask, "resultProcess", resultSet);
        Assert.assertNotNull(result);
    }
}
