/*
 * Copyright 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.akexorcist.bluetoothspp;

import java.util.ArrayList;
import java.util.Set;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

@SuppressLint("NewApi")
public class BluetoothSPP {
	// Listener for Bluetooth Status & Connection
	private BluetoothStateListener mBluetoothStateListener = null;
	private OnDataReceivedListener mDataReceivedListener = null;
	private BluetoothConnectionListener mBluetoothConnectionListener = null;
	private AutoConnectionListener mAutoConnectionListener = null;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    // Member object for the chat services
    private BluetoothService mChatService = null;

    // Name and Address of the connected device
    private String mDeviceName = null;
    private String mDeviceAddress = null;

    private boolean isAutoConnecting = false;
    private boolean isAutoConnectionEnabled = false;
    private boolean isConnected = false;
	private boolean isConnecting = false;
	private boolean isServiceRunning = false;

	private String keyword = "";
    private boolean mIsAndroid = BluetoothState.DEVICE_ANDROID;

    private BluetoothConnectionListener bcl;
    private int c = 0;

	public BluetoothSPP() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

    public interface BluetoothStateListener {
	    public void onServiceStateChanged(int state);
	}

    public interface OnDataReceivedListener {
	    public void onDataReceived(byte[] data, String message);
	}

    public interface BluetoothConnectionListener {
        public void onDeviceConnecting();
        public void onDeviceConnected(String name, String address);
        public void onDeviceDisconnected();
        public void onDeviceConnectionFailed();
        public void onToastMessage(String message);
    }

    public interface AutoConnectionListener {
	    public void onAutoConnectionStarted();
	    public void onNewConnection(String name, String address);
	}

	public boolean isBluetoothAvailable() {
        try {
        	if (mBluetoothAdapter == null || mBluetoothAdapter.getAddress().equals(null))
	            return false;
        } catch (NullPointerException e) {
        	 return false;
        }
        return true;
	}

	public boolean isBluetoothEnabled() {
		return mBluetoothAdapter.isEnabled();
	}

	public boolean isServiceAvailable() {
		return mChatService != null;
	}

    public boolean isConnected() {
        return isConnected;
    }

	public boolean isAutoConnecting() {
		return isAutoConnecting;
	}

	public boolean startDiscovery() {
		return mBluetoothAdapter.startDiscovery();
	}

	public boolean isDiscovery() {
		return mBluetoothAdapter.isDiscovering();
	}

	public boolean cancelDiscovery() {
		return mBluetoothAdapter.cancelDiscovery();
	}

	public void setupService() {
        if (null == mChatService) {
            mChatService = new BluetoothService(mHandler);
        }
	}

	public BluetoothAdapter getBluetoothAdapter() {
		return mBluetoothAdapter;
	}

	public int getServiceState() {
		if(mChatService != null)
			return mChatService.getState();
		else
			return -1;
	}

	public void startService(boolean isAndroid) {
		if (mChatService != null) {
            if (mChatService.getState() == BluetoothState.STATE_NONE) {
            	isServiceRunning = true;
            	mChatService.start(isAndroid);
            	mIsAndroid = isAndroid;
            }
        }
	}

	public void stopService() {
		if (mChatService != null) {
        	isServiceRunning = false;
        	mChatService.stop();
        }
		new Handler().postDelayed(new Runnable() {
			public void run() {
		        if (mChatService != null) {
		        	isServiceRunning = false;
		        	mChatService.stop();
		        }
			}
		}, 500);
	}

    public void setDeviceTarget(boolean isAndroid) {
    	stopService();
    	startService(isAndroid);
    	mIsAndroid = isAndroid;
    }

	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BluetoothState.MESSAGE_WRITE:
                break;
            case BluetoothState.MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                if(readBuf != null && readBuf.length > 0) {
                	if(mDataReceivedListener != null)
                		mDataReceivedListener.onDataReceived(readBuf, readMessage);
                }
                break;
            case BluetoothState.MESSAGE_DEVICE_NAME:
                mDeviceName = msg.getData().getString(BluetoothState.DEVICE_NAME);
                mDeviceAddress = msg.getData().getString(BluetoothState.DEVICE_ADDRESS);
            	if(mBluetoothConnectionListener != null)
            		mBluetoothConnectionListener.onDeviceConnected(mDeviceName, mDeviceAddress);
                isConnected = true;
                break;
            case BluetoothState.MESSAGE_TOAST:
                // Toast.makeText(mContext, msg.getData().getString(BluetoothState.TOAST)
                // 		, Toast.LENGTH_SHORT).show();
                mBluetoothConnectionListener.onToastMessage(msg.getData().getString(BluetoothState.TOAST));
                break;
            case BluetoothState.MESSAGE_STATE_CHANGE:
            	if(mBluetoothStateListener != null)
            		mBluetoothStateListener.onServiceStateChanged(msg.arg1);
                if(isConnected && msg.arg1 != BluetoothState.STATE_CONNECTED) {
                	if(mBluetoothConnectionListener != null)
                		mBluetoothConnectionListener.onDeviceDisconnected();
                	if(isAutoConnectionEnabled) {
                		isAutoConnectionEnabled = false;
                		autoConnect(keyword);
                	}
                    isConnected = false;
                    mDeviceName = null;
                    mDeviceAddress = null;
                }

                if(!isConnecting && msg.arg1 == BluetoothState.STATE_CONNECTING) {
                	isConnecting = true;
                    mBluetoothConnectionListener.onDeviceConnecting();
                } else if(isConnecting) {
                	if(msg.arg1 != BluetoothState.STATE_CONNECTED) {
                    	if(mBluetoothConnectionListener != null)
                    		mBluetoothConnectionListener.onDeviceConnectionFailed();
                	}
                	isConnecting = false;
                }
                break;
            }
        }
    };

    public void stopAutoConnect() {
    	isAutoConnectionEnabled = false;
    }

    public void connect(Intent data) {
        String address = data.getExtras().getString(BluetoothState.EXTRA_DEVICE_ADDRESS);
        connect(address);
    }

    public void connect(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        connect(device);
    }

    public void connect(BluetoothDevice device) {
        mChatService.connect(device);
    }

    public void disconnect() {
    	if(mChatService != null) {
        	isServiceRunning = false;
        	mChatService.stop();
            if(mChatService.getState() == BluetoothState.STATE_NONE) {
            	isServiceRunning = true;
            	mChatService.start(mIsAndroid);
            }
        }
    }

    public void setBluetoothStateListener (BluetoothStateListener listener) {
    	mBluetoothStateListener = listener;
    }

    public void setOnDataReceivedListener (OnDataReceivedListener listener) {
    	mDataReceivedListener = listener;
    }

    public void setBluetoothConnectionListener (BluetoothConnectionListener listener) {
    	mBluetoothConnectionListener = listener;
    }

    public void setAutoConnectionListener(AutoConnectionListener listener) {
    	mAutoConnectionListener = listener;
    }

    public void enable() {
    	mBluetoothAdapter.enable();
    }

    public void send(byte[] data) {
    	if(mChatService.getState() == BluetoothState.STATE_CONNECTED)
    		mChatService.write(data);
    }

    public void send(String data) {
    	if(mChatService.getState() == BluetoothState.STATE_CONNECTED)
    		mChatService.write(data.getBytes());
    }

    public String getConnectedDeviceName() {
    	return mDeviceName;
    }

    public String getConnectedDeviceAddress() {
    	return mDeviceAddress;
    }

    public String[] getPairedDeviceName() {
    	int c = 0;
    	Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
    	String[] name_list = new String[devices.size()];
        for(BluetoothDevice device : devices) {
        	name_list[c] = device.getName();
        	c++;
        }
        return name_list;
    }

    public String[] getPairedDeviceAddress() {
    	int c = 0;
    	Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
    	String[] address_list = new String[devices.size()];
        for(BluetoothDevice device : devices) {
        	address_list[c] = device.getAddress();
        	c++;
        }
        return address_list;
    }


    public void autoConnect(String address) {
    	if(!isAutoConnectionEnabled) {
    		isAutoConnectionEnabled = true;
            isAutoConnecting = true;
            if (mAutoConnectionListener != null) {
                mAutoConnectionListener.onAutoConnectionStarted();
            }
            final ArrayList<String> arr_filter_address = new ArrayList<String>();
	    	final ArrayList<String> arr_filter_name = new ArrayList<String>();
	    	String[] arr_name = getPairedDeviceName();
	    	String[] arr_address = getPairedDeviceAddress();
	    	for(int i = 0 ; i < arr_name.length ; i++) {
	    		if (arr_address[i].contains(address)) {
	    			arr_filter_address.add(arr_address[i]);
	    			arr_filter_name.add(arr_name[i]);
	    		}
	    	}

	    	bcl = new BluetoothConnectionListener() {
                @Override
                public void onDeviceConnecting() {

                }

                public void onDeviceConnected(String name, String address) {
					bcl = null;
		    		isAutoConnecting = false;
				}

				public void onDeviceDisconnected() { }
				public void onDeviceConnectionFailed() {
					if (isServiceRunning) {
						if (isAutoConnectionEnabled) {
							c++;
							if (c >= arr_filter_address.size()) {
						    	c = 0;
                            }
							connect(arr_filter_address.get(c));
				        	if (mAutoConnectionListener != null) {
				        		mAutoConnectionListener.onNewConnection(arr_filter_name.get(c), arr_filter_address.get(c));
                            }
						} else {
							bcl = null;
				    		isAutoConnecting = false;
						}
					}
				}

                @Override
                public void onToastMessage(String message) {

                }
            };

	    	setBluetoothConnectionListener(bcl);
	    	c = 0;
        	if (mAutoConnectionListener != null) {
        		mAutoConnectionListener.onNewConnection(arr_name[c], arr_address[c]);
            }
        	if (arr_filter_address.size() > 0) {
        		connect(arr_filter_address.get(c));
            } else {
        		// Toast.makeText(mContext, "Device name mismatch", Toast.LENGTH_SHORT).show();
            }
    	}
    }
}
