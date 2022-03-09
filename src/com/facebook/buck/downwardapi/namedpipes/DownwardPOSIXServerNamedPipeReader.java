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

package com.facebook.buck.downwardapi.namedpipes;

import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.downward.model.EndEvent;
import com.facebook.buck.downward.model.EventTypeMessage;
import com.facebook.buck.downwardapi.protocol.DownwardProtocol;
import com.facebook.buck.downwardapi.protocol.DownwardProtocolType;
import com.facebook.buck.io.namedpipes.NamedPipeFactory;
import com.facebook.buck.io.namedpipes.NamedPipeServer;
import com.facebook.buck.io.namedpipes.NamedPipeWriter;
import com.facebook.buck.io.namedpipes.posix.POSIXServerNamedPipeReader;
import com.facebook.buck.util.types.Unit;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/** {@link POSIXServerNamedPipeReader} specific to Downward API. */
public class DownwardPOSIXServerNamedPipeReader extends POSIXServerNamedPipeReader
    implements NamedPipeServer, SupportsDownwardProtocol {

  private static final Logger LOG = Logger.get(DownwardPOSIXServerNamedPipeReader.class);

  private static final long SHUTDOWN_TIMEOUT = 2;
  private static final TimeUnit SHUTDOWN_TIMEOUT_UNIT = TimeUnit.SECONDS;

  private final AtomicReference<DownwardProtocol> protocolReference = new AtomicReference<>();

  protected DownwardPOSIXServerNamedPipeReader(Path path) throws IOException {
    super(path);
  }

  @Override
  public void setProtocol(DownwardProtocol protocol) {
    LOG.info("Set protocol to %s", protocol.getProtocolName());
    boolean updated = protocolReference.compareAndSet(null, protocol);
    if (!updated) {
      DownwardProtocol existingProtocol = Objects.requireNonNull(getProtocol());
      Preconditions.checkState(
          existingProtocol.equals(protocol),
          "Cannot set a downward protocol to `%s` once it has been established to `%s`",
          protocol.getProtocolName(),
          existingProtocol.getProtocolName());
    }
  }

  @Nullable
  @Override
  public DownwardProtocol getProtocol() {
    return protocolReference.get();
  }

  /**
   * Prepare to close this named pipe by writing an {@link EndEvent} to indicate protocol
   * termination.
   *
   * <p>At this point, the launched subprocess has completed, and all of its {@link NamedPipeWriter}
   * instances should be closed. This method connects a new instance of {@link NamedPipeWriter},
   * which should be the only connected writer now. This writer writes the {@link EndEvent} into the
   * named pipe, which the event handler will consume as a signal for termination. This method then
   * waits until the handler signals that it has terminated.
   */
  @Override
  public void prepareToClose(Future<Unit> readerFinished)
      throws IOException, ExecutionException, TimeoutException, InterruptedException {

    if (readerFinished.isDone()) {
      LOG.debug(
          "Named pipe reader for %s is already finished. No need to send an end event.", getName());
      return;
    }

    try (NamedPipeWriter writer =
            NamedPipeFactory.getFactory().connectAsWriter(Paths.get(getName()));
        OutputStream outputStream = writer.getOutputStream()) {
      // This null check is not perfectly synchronized with the handler, but in practice by the
      // time the subprocess has finished, the handler should have read the protocol from the
      // subprocess already, if any, so this is okay.
      DownwardProtocol protocol = getProtocol();
      if (protocol == null) {
        // Client has not written anything into named pipe. Arbitrarily pick binary protocol to
        // communicate with handler
        DownwardProtocolType protocolType = DownwardProtocolType.BINARY;
        LOG.info("End event with a binary protocol");
        protocolType.writeDelimitedTo(outputStream);
        protocol = protocolType.getDownwardProtocol();
      }
      protocol.write(
          EventTypeMessage.newBuilder().setEventType(EventTypeMessage.EventType.END_EVENT).build(),
          EndEvent.getDefaultInstance(),
          outputStream);
      readerFinished.get(SHUTDOWN_TIMEOUT, SHUTDOWN_TIMEOUT_UNIT);
    }
  }
}
