/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sentry.hdfs;

import org.apache.sentry.core.common.utils.SentryConstants;
import org.apache.sentry.service.thrift.SentryServiceState;
import org.apache.sentry.service.thrift.SentryStateBank;
import org.apache.sentry.service.thrift.SentryStateBankTestHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.apache.sentry.hdfs.ServiceConstants.SEQUENCE_NUMBER_UPDATE_UNINITIALIZED;
import static org.apache.sentry.hdfs.service.thrift.sentry_hdfs_serviceConstants.UNUSED_PATH_UPDATE_IMG_NUM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestDBUpdateForwarder {
  private ImageRetriever imageRetriever;
  private DeltaRetriever deltaRetriever;
  private DBUpdateForwarder updater;

  @Before
  public void setUp() {
    imageRetriever = Mockito.mock(ImageRetriever.class);
    deltaRetriever = Mockito.mock(DeltaRetriever.class);
    updater = new DBUpdateForwarder<>(imageRetriever, deltaRetriever);
    SentryStateBankTestHelper.clearAllStates();
  }

  @Test
  public void testEmptyListIsReturnedWhenImageNumIsZeroAndNoImagesArePersisted() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(SentryConstants.EMPTY_PATHS_SNAPSHOT_ID);

    List updates = updater.getAllUpdatesFrom(1, SentryConstants.EMPTY_PATHS_SNAPSHOT_ID);
    assertTrue(updates.isEmpty());
  }

  /**
   * Test if a empty list is returned when the Sentry Server is doing a Full update from
   * HMS.
   * @throws Exception
   */
  @Test
  public void testEmptyListIsReturnedWhenFullUpdateRunningFromStart() throws Exception {
    SentryStateBank.enableState(SentryServiceState.COMPONENT, SentryServiceState.FULL_UPDATE_RUNNING);
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);

    List updates = updater.getAllUpdatesFrom(SEQUENCE_NUMBER_UPDATE_UNINITIALIZED,1);
    assertTrue(updates.isEmpty());
  }

  /**
   * Test if a empty list is returned when the Sentry Server is doing a Full update from
   * HMS.
   * @throws Exception
   */
  @Test
  public void testEmptyListIsReturnedWhenFullUpdateRunning() throws Exception {
    SentryStateBank.enableState(SentryServiceState.COMPONENT, SentryServiceState.FULL_UPDATE_RUNNING);
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(2L);

    List updates = updater.getAllUpdatesFrom(SEQUENCE_NUMBER_UPDATE_UNINITIALIZED,1);
    assertTrue(updates.isEmpty());
  }

  @Test
  public void testEmptyListIsReturnedWhenImageIsUnusedAndNoDeltaChangesArePersisted() throws Exception {
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(SentryConstants.EMPTY_NOTIFICATION_ID);

    List updates = updater.getAllUpdatesFrom(1, UNUSED_PATH_UPDATE_IMG_NUM);
    assertTrue(updates.isEmpty());
  }

  @Test
  public void testEmptyListReturnedWhenImageSeqIsEqualToLatest() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(10L);

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(11, 1);
    assertTrue(updates.isEmpty());
  }

  @Test
  public void testFirstImageSyncIsReturnedWhenImageNumIsZero() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(1, 1, true));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(0, SentryConstants.EMPTY_PATHS_SNAPSHOT_ID);
    assertEquals(1, updates.size());
    assertEquals(1, updates.get(0).getSeqNum());
    assertEquals(1, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }

  @Test
  public void testFirstImageSyncGetsEmptySetWhenImageNumIsZeroAndFullUpdateRunning() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(1, 1, true));
    SentryStateBank.enableState(SentryServiceState.COMPONENT, SentryServiceState.FULL_UPDATE_RUNNING);

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(0, SentryConstants.EMPTY_PATHS_SNAPSHOT_ID);
    assertTrue(updates.isEmpty());
  }


  @Test
  public void testFirstImageSyncIsReturnedWhenImageNumIsUnusedButDeltasAreAvailable() throws Exception {
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(1L);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(1, 1, true));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(0, UNUSED_PATH_UPDATE_IMG_NUM);
    assertEquals(1, updates.size());
    assertEquals(1, updates.get(0).getSeqNum());
    assertEquals(1, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }

  @Test
  public void testNewImageUpdateIsReturnedWhenNewImagesArePersisted() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(2L);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(1, 2, true));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(1, 1);
    assertEquals(1, updates.size());
    assertEquals(1, updates.get(0).getSeqNum());
    assertEquals(2, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }

  @Test
  public void testNewImageUpdateIsReturnedWhenImageSeqIsGreaterThanLatestSeqByOne() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(10L);
    Mockito.when(deltaRetriever.isDeltaAvailable(15)).thenReturn(false);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(10, 1, true));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(15, 1);
    assertEquals(1, updates.size());
    assertEquals(10, updates.get(0).getSeqNum());
    assertEquals(1, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }

  @Test
  public void testNewImageUpdateIsReturnedWhenRequestedDeltaIsNotAvailable() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(3L);
    Mockito.when(deltaRetriever.isDeltaAvailable(2L)).thenReturn(false);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PathsUpdate(3, 1, true));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(2, 1);
    assertEquals(1, updates.size());
    assertEquals(3, updates.get(0).getSeqNum());
    assertEquals(1, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }

  @Test
  public void testNewDeltasAreReturnedWhenRequestedDeltaIsAvailable() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(1L);
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(3L);
    Mockito.when(deltaRetriever.isDeltaAvailable(2L)).thenReturn(true);
    Mockito.when(deltaRetriever.retrieveDelta(2L, 1L))
        .thenReturn(Arrays.asList(new PathsUpdate(3, 1, false)));

    List<PathsUpdate> updates = updater.getAllUpdatesFrom(2, 1);
    assertEquals(1, updates.size());
    assertEquals(3, updates.get(0).getSeqNum());
    assertEquals(1, updates.get(0).getImgNum());
    assertFalse(updates.get(0).hasFullImage());
  }

  @Test
  public void testNewImageIsReturnedWhenZeroSeqNumAndUnusedImgNumAreUsed() throws Exception {
    Mockito.when(imageRetriever.getLatestImageID()).thenReturn(0L);
    Mockito.when(deltaRetriever.getLatestDeltaID()).thenReturn(0L);
    Mockito.when(imageRetriever.retrieveFullImage())
        .thenReturn(new PermissionsUpdate(1, true));

    List<PermissionsUpdate> updates = updater.getAllUpdatesFrom(0, UNUSED_PATH_UPDATE_IMG_NUM);
    assertEquals(1, updates.size());
    assertEquals(1, updates.get(0).getSeqNum());
    assertEquals(UNUSED_PATH_UPDATE_IMG_NUM, updates.get(0).getImgNum());
    assertTrue(updates.get(0).hasFullImage());
  }
}
