/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.android.server.telecom.tests;

import com.android.internal.telecom.IConnectionService;
import com.android.internal.telecom.IConnectionServiceAdapter;
import com.android.internal.telecom.IVideoProvider;
import com.android.internal.telecom.RemoteServiceCallback;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.ParcelableConference;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Controls a test {@link IConnectionService} as would be provided by a source of connectivity
 * to the Telecom framework.
 */
public class ConnectionServiceFixture implements TestFixture<IConnectionService> {

    private IConnectionService.Stub mConnectionService =
            Mockito.mock(IConnectionService.Stub.class);

    public class ConnectionInfo {
        PhoneAccountHandle connectionManagerPhoneAccount;
        String id;
        boolean ringing;
        ConnectionRequest request;
        boolean isIncoming;
        boolean isUnknown;
        int state;
        int addressPresentation;
        int capabilities;
        StatusHints statusHints;
        DisconnectCause disconnectCause;
        String conferenceId;
        String callerDisplayName;
        int callerDisplayNamePresentation;
        final List<String> conferenceableConnectionIds = new ArrayList<>();
        IVideoProvider videoProvider;
        int videoState;
        boolean isVoipAudioMode;
    }

    public class ConferenceInfo {
        PhoneAccountHandle phoneAccount;
        int state;
        int capabilities;
        final List<String> connectionIds = new ArrayList<>();
        IVideoProvider videoProvider;
        int videoState;
        long connectTimeMillis;
        StatusHints statusHints;
    }

    public String mLatestConnectionId;
    public final Set<IConnectionServiceAdapter> mConnectionServiceAdapters = new HashSet<>();
    public final Map<String, ConnectionInfo> mConnectionById = new HashMap<>();
    public final Map<String, ConferenceInfo> mConferenceById = new HashMap<>();
    public final List<ComponentName> mRemoteConnectionServiceNames = new ArrayList<>();
    public final List<IBinder> mRemoteConnectionServices = new ArrayList<>();

    public ConnectionServiceFixture() throws Exception {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                IConnectionServiceAdapter a = (IConnectionServiceAdapter)
                        invocation.getArguments()[0];
                if (!mConnectionServiceAdapters.add(a)) {
                    throw new RuntimeException("Adapter already added: " + a);
                }
                return null;
            }
        }).when(mConnectionService).addConnectionServiceAdapter(
                any(IConnectionServiceAdapter.class));

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                IConnectionServiceAdapter a = (IConnectionServiceAdapter)
                        invocation.getArguments()[0];
                if (!mConnectionServiceAdapters.remove(a)) {
                    throw new RuntimeException("Adapter never added: " + a);
                }
                return null;
            }
        }).when(mConnectionService).removeConnectionServiceAdapter(
                any(IConnectionServiceAdapter.class));

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                String id = (String) invocation.getArguments()[1];
                if (mConnectionById.containsKey(id)) {
                    throw new RuntimeException("Connection already exists: " + id);
                }
                mLatestConnectionId = id;
                ConnectionInfo c = new ConnectionInfo();
                c.connectionManagerPhoneAccount = (PhoneAccountHandle) invocation.getArguments()[0];
                c.id = id;
                c.request = (ConnectionRequest) invocation.getArguments()[2];
                c.isIncoming = (boolean) invocation.getArguments()[3];
                c.isUnknown = (boolean) invocation.getArguments()[4];
                mConnectionById.put(id, c);
                return null;
            }
        }).when(mConnectionService).createConnection(
                any(PhoneAccountHandle.class),
                any(String.class),
                any(ConnectionRequest.class),
                any(Boolean.TYPE),
                any(Boolean.TYPE));

        when(mConnectionService.asBinder())
                .thenReturn(mConnectionService);
        when(mConnectionService.queryLocalInterface(anyString()))
                .thenReturn(mConnectionService);
    }

    @Override
    public IConnectionService getTestDouble() {
        return mConnectionService;
    }

    public void sendHandleCreateConnectionComplete(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.handleCreateConnectionComplete(
                    id,
                    mConnectionById.get(id).request,
                    parcelable(mConnectionById.get(id)));
        }
    }

    public void sendSetActive(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_ACTIVE;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setActive(id);
        }
    }

    public void sendSetRinging(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_RINGING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setRinging(id);
        }
    }

    public void sendSetDialing(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_DIALING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setDialing(id);
        }
    }

    public void sendSetDisconnected(String id, int disconnectCause) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_DISCONNECTED;
        mConnectionById.get(id).disconnectCause = new DisconnectCause(disconnectCause);
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setDisconnected(id, mConnectionById.get(id).disconnectCause);
        }
    }

    public void sendSetOnHold(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_HOLDING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setOnHold(id);
        }
    }

    public void sendSetRingbackRequested(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setRingbackRequested(id, mConnectionById.get(id).ringing);
        }
    }

    public void sendSetConnectionCapabilities(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConnectionCapabilities(id, mConnectionById.get(id).capabilities);
        }
    }

    public void sendSetIsConferenced(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setIsConferenced(id, mConnectionById.get(id).conferenceId);
        }
    }

    public void sendAddConferenceCall(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.addConferenceCall(id, parcelable(mConferenceById.get(id)));
        }
    }

    public void sendRemoveCall(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.removeCall(id);
        }
    }

    public void sendOnPostDialWait(String id, String remaining) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.onPostDialWait(id, remaining);
        }
    }

    public void sendOnPostDialChar(String id, char nextChar) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.onPostDialChar(id, nextChar);
        }
    }

    public void sendQueryRemoteConnectionServices() throws Exception {
        mRemoteConnectionServices.clear();
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.queryRemoteConnectionServices(new RemoteServiceCallback.Stub() {
                @Override
                public void onError() throws RemoteException {
                    throw new RuntimeException();
                }

                @Override
                public void onResult(
                        List<ComponentName> names,
                        List<IBinder> services)
                        throws RemoteException {
                    TestCase.assertEquals(names.size(), services.size());
                    mRemoteConnectionServiceNames.addAll(names);
                    mRemoteConnectionServices.addAll(services);
                }

                @Override
                public IBinder asBinder() {
                    return this;
                }
            });
        }
    }

    public void sendSetVideoProvider(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setVideoProvider(id, mConnectionById.get(id).videoProvider);
        }
    }

    public void sendSetVideoState(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setVideoState(id, mConnectionById.get(id).videoState);
        }
    }

    public void sendSetIsVoipAudioMode(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setIsVoipAudioMode(id, mConnectionById.get(id).isVoipAudioMode);
        }
    }

    public void sendSetStatusHints(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setStatusHints(id, mConnectionById.get(id).statusHints);
        }
    }

    public void sendSetAddress(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setAddress(
                    id,
                    mConnectionById.get(id).request.getAddress(),
                    mConnectionById.get(id).addressPresentation);
        }
    }

    public void sendSetCallerDisplayName(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setCallerDisplayName(
                    id,
                    mConnectionById.get(id).callerDisplayName,
                    mConnectionById.get(id).callerDisplayNamePresentation);
        }
    }

    public void sendSetConferenceableConnections(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConferenceableConnections(id, mConnectionById.get(id).conferenceableConnectionIds);
        }
    }

    public void sendAddExistingConnection(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.addExistingConnection(id, parcelable(mConnectionById.get(id)));
        }
    }

    private ParcelableConference parcelable(ConferenceInfo c) {
        return new ParcelableConference(
                c.phoneAccount,
                c.state,
                c.capabilities,
                c.connectionIds,
                c.videoProvider,
                c.videoState,
                c.connectTimeMillis,
                c.statusHints);
    }

    private ParcelableConnection parcelable(ConnectionInfo c) {
        return new ParcelableConnection(
                c.request.getAccountHandle(),
                c.state,
                c.capabilities,
                c.request.getAddress(),
                c.addressPresentation,
                c.callerDisplayName,
                c.callerDisplayNamePresentation,
                c.videoProvider,
                c.videoState,
                false, /* ringback requested */
                false, /* voip audio mode */
                c.statusHints,
                c.disconnectCause,
                c.conferenceableConnectionIds,
                0 /* call substate */);
    }
}
