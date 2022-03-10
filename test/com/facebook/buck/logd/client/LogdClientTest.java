/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.logd.client;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.facebook.buck.logd.LogDaemonException;
import com.facebook.buck.logd.proto.CreateLogRequest;
import com.facebook.buck.logd.proto.CreateLogResponse;
import com.facebook.buck.logd.proto.LogMessage;
import com.facebook.buck.logd.proto.LogType;
import com.facebook.buck.logd.proto.LogdServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import io.grpc.util.MutableHandlerRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.easymock.Capture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for logD client */
public class LogdClientTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
  private final TestHelper testHelper = mock(TestHelper.class);

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private LogDaemonClient client;

  @Before
  public void setUp() throws Exception {
    // Generate a unique in-process server name
    String serverName = InProcessServerBuilder.generateName();
    String fakeBuildId = "c0fd2155-96aa-4770-a272-f4f23d9bd056";
    // Use a mutable service registry for later registering the service impl for each test case.
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .fallbackHandlerRegistry(serviceRegistry)
            .directExecutor()
            .build()
            .start());
    client =
        new LogdClient(
            InProcessChannelBuilder.forName(serverName).directExecutor(),
            fakeBuildId,
            new TestStreamObserverFactory(testHelper));
  }

  @After
  public void tearDown() {
    client.shutdown();
  }

  @Test
  public void createLogFile() {
    // This tests if requests and responses are sent correctly via StreamObservers
    // calling createLogFile(filePath, logType) should return an id = 1
    String filePath = getTestFilePath();
    LogType logType = LogType.BUCK_LOG;

    LogdServiceGrpc.LogdServiceImplBase createLogFileImpl =
        new LogdServiceGrpc.LogdServiceImplBase() {
          @Override
          public void createLogFile(
              CreateLogRequest request, StreamObserver<CreateLogResponse> responseObserver) {
            // first log file requested should have generated id = 1
            responseObserver.onNext(CreateLogResponse.newBuilder().setLogId(1).build());
            responseObserver.onCompleted();
          }
        };
    serviceRegistry.addService(createLogFileImpl);

    assertEquals(1, client.createLogFile(filePath, logType));
  }

  @Test(expected = LogDaemonException.class)
  public void createLogFile_error() {
    // This tests if error is indeed thrown by responseObserver
    String filePath = getTestFilePath();
    LogType logType = LogType.BUCK_LOG;

    StatusRuntimeException fakeError = new StatusRuntimeException(Status.CANCELLED);

    LogdServiceGrpc.LogdServiceImplBase createLogFileImpl =
        new LogdServiceGrpc.LogdServiceImplBase() {
          @Override
          public void createLogFile(
              CreateLogRequest request, StreamObserver<CreateLogResponse> responseObserver) {
            responseObserver.onError(fakeError);
          }
        };
    serviceRegistry.addService(createLogFileImpl);

    assertNotEquals(1, client.createLogFile(filePath, logType));
    fail("Exception expected before this line");
  }

  @Test
  public void openLog() throws IOException {
    // This tests if messages are processed by StreamObserver when client streams messages
    List<LogMessage> messagesDelivered = new ArrayList<>();
    com.google.rpc.Status fakeResponse =
        com.google.rpc.Status.newBuilder().setCode(Status.Code.OK.value()).build();

    // implement fake service
    LogdServiceGrpc.LogdServiceImplBase openLogImpl =
        new LogdServiceGrpc.LogdServiceImplBase() {
          @Override
          public StreamObserver<LogMessage> openLog(
              StreamObserver<com.google.rpc.Status> responseObserver) {
            return new StreamObserver<LogMessage>() {
              @Override
              public void onNext(LogMessage logMessage) {
                messagesDelivered.add(logMessage);
              }

              @Override
              public void onError(Throwable t) {}

              @Override
              public void onCompleted() {
                responseObserver.onNext(fakeResponse);
                responseObserver.onCompleted();
              }
            };
          }
        };
    serviceRegistry.addService(openLogImpl);

    int logFileId = 1;

    String logMessage1 = "Hello";
    String logMessage2 = "Hello again";
    String logMessage3 = "Hello again again";

    List<String> messages = Arrays.asList(logMessage1, logMessage2, logMessage3);
    StringBuilder sb = new StringBuilder();

    // set mock expectations
    testHelper.onMessage(fakeResponse);
    expectLastCall();
    replay(testHelper);

    try (LogdStream logdStream = client.openLog(logFileId)) {
      for (String message : messages) {
        sb.append(message);
        logdStream.write(message.getBytes(StandardCharsets.UTF_8));
      }
    }

    // assert above messages were indeed sent
    // all 3 messages are concatenated because logd stream is buffered
    assertEquals(sb.toString(), messagesDelivered.get(0).getLogMessage());

    verify(testHelper);
  }

  @Test
  public void openLog_error() throws IOException {
    // This tests if an rpc error is thrown and caught properly
    StatusRuntimeException fakeError = new StatusRuntimeException(Status.CANCELLED);

    // implement fake service
    LogdServiceGrpc.LogdServiceImplBase openLogImpl =
        new LogdServiceGrpc.LogdServiceImplBase() {
          @Override
          public StreamObserver<LogMessage> openLog(
              StreamObserver<com.google.rpc.Status> responseObserver) {
            // send an error immediately
            responseObserver.onError(fakeError);

            return new StreamObserver<LogMessage>() {
              @Override
              public void onNext(LogMessage logMessage) {}

              @Override
              public void onError(Throwable t) {}

              @Override
              public void onCompleted() {}
            };
          }
        };

    serviceRegistry.addService(openLogImpl);

    // set mock expectations, an error must be captured
    Capture<Throwable> errorCaptor = Capture.newInstance();
    testHelper.onRpcError(capture(errorCaptor));
    replay(testHelper);

    int logFileId = 1;
    try (LogdStream logdStream = client.openLog(logFileId)) {
      logdStream.write("Hello".getBytes(StandardCharsets.UTF_8));
    }

    verify(testHelper);
    assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
  }

  private String getTestFilePath() {
    return tempFolder.getRoot().getAbsolutePath() + "/logs/buck.log";
  }
}
