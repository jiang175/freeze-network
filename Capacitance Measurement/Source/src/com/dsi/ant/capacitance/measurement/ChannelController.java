/*
 * Copyright 2012 Dynastream Innovations Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.dsi.ant.capacitance.measurement;

import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.dsi.ant.channel.AntChannel;
import com.dsi.ant.channel.AntCommandFailedException;
import com.dsi.ant.channel.IAntChannelEventHandler;
import com.dsi.ant.message.ChannelId;
import com.dsi.ant.message.ChannelType;
import com.dsi.ant.message.fromant.BroadcastDataMessage;
import com.dsi.ant.message.fromant.ChannelEventMessage;
import com.dsi.ant.message.fromant.MessageFromAntType;
import com.dsi.ant.message.ipc.AntMessageParcel;

import java.util.Random;

public class ChannelController {
    // The device type and transmission type to be part of the channel ID message
    private static final int CHANNEL_PROOF_DEVICE_TYPE = 0x01;
    private static final int CHANNEL_PROOF_TRANSMISSION_TYPE = 0x02;

    // The period and frequency values the channel will be configured to
    private static final int CHANNEL_PROOF_PERIOD = 819; // 60hz
    private static final int CHANNEL_PROOF_FREQUENCY = 66 ;

    private static final String TAG = ChannelController.class.getSimpleName();

    private static Random randGen = new Random();

    private AntChannel mAntChannel;
    private AntChannel mBackgroundScanChannel;
    private ChannelBroadcastListener mChannelBroadcastListener  ;

    private ChannelEventCallback mChannelEventCallback = new ChannelEventCallback();

    private ChannelInfo mChannelInfo;

    private boolean mIsOpen;
    private boolean mBackgroundScanInProgress = false;
    private boolean mBackgroundScanIsConfigured = false;
    private int counter = 1;
    private int counter_c = 1;
    private int x = 1;
    private int y = 1;



    static public abstract class ChannelBroadcastListener {
        public abstract void onBroadcastChanged(ChannelInfo newInfo);
    }

    public void openBackgroundScanningChannel() {
        if (null != mBackgroundScanChannel) {
            if (mIsOpen) {
                Log.w(TAG, "Channel was already opened");
            } else {
                try {
                    if (!mBackgroundScanInProgress) {
                        // Opening channel
                        mBackgroundScanChannel.open();
                        mIsOpen = true;
                        Log.d(TAG, "Opened background scanning channel");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "", e);
                } catch (AntCommandFailedException e) {
                    // This will release, and therefore unassign if required
                    Log.e(TAG, "", e);
                }
            }
        } else {
            Log.w(TAG, "No channel available");
        }
    }


    public ChannelController(AntChannel antChannel,
                             ChannelBroadcastListener broadcastListener) {
        mAntChannel = antChannel;
        mChannelInfo = new ChannelInfo(randGen.nextInt(256));
        mChannelBroadcastListener = broadcastListener;

        openChannel();
    }


    boolean openChannel() {
        if (null != mAntChannel) {
            if (mIsOpen) {
                Log.w(TAG, "Channel was already open");
            } else {
                /*
                 * Although this reference code sets ChannelType to either a transmitting master or a receiving slave,
                 * the standard for ANT is that channels communication is bidirectional. The use of single-direction 
                 * communication in this app is for ease of understanding as reference code. For more information and
                 * any additional features on ANT channel communication, refer to the ANT Protocol Doc found at: 
                 * http://www.thisisant.com/resources/ant-message-protocol-and-usage/
                 */
                ChannelType channelType = (ChannelType.SHARED_BIDIRECTIONAL_MASTER);

                // Channel ID message contains device number, type and transmission type. In 
                // order for master (TX) channels and slave (RX) channels to connect, they 
                // must have the same channel ID, or wildcard (0) is used.
                ChannelId channelId = new ChannelId(0x01,
                        CHANNEL_PROOF_DEVICE_TYPE, CHANNEL_PROOF_TRANSMISSION_TYPE);

                try {
                    // Setting the channel event handler so that we can receive messages from ANT
                    mAntChannel.setChannelEventHandler(mChannelEventCallback);

                    // Performs channel assignment by assigning the type to the channel. Additional 
                    // features (such as, background scanning and frequency agility) can be enabled 
                    // by passing an ExtendedAssignment object to assign(ChannelType, ExtendedAssignment).
                    mAntChannel.assign(channelType);
                    
                    /*
                     * Configures the channel ID, messaging period and rf frequency after assigning, 
                     * then opening the channel.
                     * 
                     * For any additional ANT features such as proximity search or background scanning, refer to 
                     * the ANT Protocol Doc found at: 
                     * http://www.thisisant.com/resources/ant-message-protocol-and-usage/
                     */
                    mAntChannel.setChannelId(channelId);
                    mAntChannel.setPeriod(CHANNEL_PROOF_PERIOD);
                    mAntChannel.setRfFrequency(CHANNEL_PROOF_FREQUENCY);
                    mAntChannel.open();
                    mIsOpen = true;

                    Log.d(TAG, "Opened channel with device number: " + mChannelInfo.deviceNumber);
                } catch (RemoteException e) {
                    channelError(e);
                } catch (AntCommandFailedException e) {
                    // This will release, and therefore unassign if required
                    channelError("Open failed", e);
                }
            }
        } else {
            Log.w(TAG, "No channel available");
        }

        return mIsOpen;
    }

    /**
     * Implements the Channel Event Handler Interface so that messages can be
     * received and channel death events can be handled.
     */
    public class ChannelEventCallback implements IAntChannelEventHandler {
        private void updateData(byte[] data) {
            Log.d(TAG, "PUSH: " + "["+data[0]+"]"+"["+data[1]+"]"+"["+data[2]+"]"+"["+data[3]+"]"+"["+data[4]+"]"+"["+data[5]+"]"+"["+data[6]+"]"+"["+data[7]+"]");
            mChannelInfo.broadcastData = data;
            mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
    }

        @Override
        public void onChannelDeath() {
            // Display channel death message when channel dies
            displayChannelError("Channel Death");
        }

        @Override
        public void onReceiveMessage(MessageFromAntType messageType, AntMessageParcel antParcel) {
            Log.d(TAG, "Rx: " + antParcel);

            // Switching on message type to handle different types of messages
            switch (messageType) {
                // If data message, construct from parcel and update channel data
                case BROADCAST_DATA:
                    // Rx Data
                    updateData(new BroadcastDataMessage(antParcel).getPayload());
                    break;
                case ACKNOWLEDGED_DATA:
                    // Rx Data
                    //updateData(new AcknowledgedDataMessage(antParcel).getPayload());
                    break;
                case CHANNEL_EVENT:
                    // Constructing channel event message from parcel
                    ChannelEventMessage eventMessage = new ChannelEventMessage(antParcel);

                    // Switching on event code to handle the different types of channel events
                    switch (eventMessage.getEventCode()) {
                        case TX:

                            // Use old info as this is what remote device has just received
                            if(ChannelList.checkoff() == 2) {
                                x = (int)(ChannelList.checkab(counter,2*counter_c - 2)*10000);
                                y = (int)(ChannelList.checkab(counter,2*counter_c - 1)*10);
                                mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
                                mChannelInfo.broadcastData[0] = (byte) (counter);
                                mChannelInfo.broadcastData[1] = 9;
                                mChannelInfo.broadcastData[2] = (byte) (ChannelList.checkoff()+ counter_c - 1);
                                mChannelInfo.broadcastData[3] = (byte) ((x >> 16) & 0xFF);
                                mChannelInfo.broadcastData[4] = (byte) ((x >> 8) & 0xFF);
                                mChannelInfo.broadcastData[5] = (byte) (x  & 0xFF);
                                mChannelInfo.broadcastData[6] = (byte) ((y >> 8) & 0xFF);
                                mChannelInfo.broadcastData[7] = (byte) (y  & 0xFF);
                            }
                            else{
                                mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
                                mChannelInfo.broadcastData[0] = (byte) (counter);
                                mChannelInfo.broadcastData[1] = 9;
                                mChannelInfo.broadcastData[2] = (byte) (ChannelList.checkoff());
                                mChannelInfo.broadcastData[3] = (byte) (ChannelList.checkdelay());
                                mChannelInfo.broadcastData[4] = (byte) (ChannelList.checkcavg() & 0xFF);
                                mChannelInfo.broadcastData[5] = (byte) ((ChannelList.checkcavg() >> 8) & 0xFF);
                                mChannelInfo.broadcastData[6] = (byte) (ChannelList.checkcytime());
                                mChannelInfo.broadcastData[7] = (byte) (ChannelList.checkrdc());
                                //counter ++;
                            }
                            //int i =  (mChannelInfo.broadcastData[5] & 0xFF) | ((mChannelInfo.broadcastData[4] & 0xFF) << 8) | ((mChannelInfo.broadcastData[3] & 0x0F) << 16);
                            //Log.d(TAG,counter  + "   TEST:  "+i +"    A:"+x+"B"+y+"counter"+counter+"        counter"+counter_c);
                            counter++;
                            if(counter == 11){
                                counter = 1;
                                counter_c++;
                            }
                            if(counter_c == 4) {
                                counter_c = 1;
                            }

                            if (mIsOpen) {
                                try {
                                    // Setting the data to be broadcast on the next channel period
                                    mAntChannel.setBroadcastData(mChannelInfo.broadcastData);
                                } catch (RemoteException e) {
                                    channelError(e);
                                }
                            }

                            break;
                        case RX_SEARCH_TIMEOUT:
                            // TODO May want to keep searching
                            displayChannelError("No Device Found");

                            break;
                        case CHANNEL_CLOSED:
                        case CHANNEL_COLLISION:
                        case RX_FAIL:
                        case RX_FAIL_GO_TO_SEARCH:
                        case TRANSFER_RX_FAILED:
                        case TRANSFER_TX_COMPLETED:
                        case TRANSFER_TX_FAILED:
                        case TRANSFER_TX_START:
                        case UNKNOWN:
                            // TODO More complex communication will need to handle these events
                            break;
                    }
                    break;
                case ANT_VERSION:
                case BURST_TRANSFER_DATA:
                case CAPABILITIES:
                case CHANNEL_ID:
                case CHANNEL_RESPONSE:
                case CHANNEL_STATUS:
                case SERIAL_NUMBER:
                case OTHER:
                    // TODO More complex communication will need to handle these message types
                    break;
            }
        }
    }

    public ChannelInfo getCurrentInfo() {
        return mChannelInfo;
    }

    void displayChannelError(String displayText) {
        mChannelInfo.die(displayText);
        mChannelBroadcastListener.onBroadcastChanged(mChannelInfo);
    }

    void channelError(RemoteException e) {
        String logString = "Remote service communication failed.";

        Log.e(TAG, logString);

        displayChannelError(logString);
    }

    void channelError(String error, AntCommandFailedException e) {
        StringBuilder logString;

        if (e.getResponseMessage() != null) {
            String initiatingMessageId = "0x" + Integer.toHexString(
                    e.getResponseMessage().getInitiatingMessageId());
            String rawResponseCode = "0x" + Integer.toHexString(
                    e.getResponseMessage().getRawResponseCode());

            logString = new StringBuilder(error)
                    .append(". Command ")
                    .append(initiatingMessageId)
                    .append(" failed with code ")
                    .append(rawResponseCode);
        } else {
            String attemptedMessageId = "0x" + Integer.toHexString(
                    e.getAttemptedMessageType().getMessageId());
            String failureReason = e.getFailureReason().toString();

            logString = new StringBuilder(error)
                    .append(". Command ")
                    .append(attemptedMessageId)
                    .append(" failed with reason ")
                    .append(failureReason);
        }

        Log.e(TAG, logString.toString());

        mAntChannel.release();

        displayChannelError("ANT Command Failed");
    }

    public void close() {
        // TODO kill all our resources
        if (null != mAntChannel) {
            mIsOpen = false;

            // Releasing the channel to make it available for others. 
            // After releasing, the AntChannel instance cannot be reused.
            mAntChannel.release();
            mAntChannel = null;
        }

        displayChannelError("Channel Closed");
    }
}
