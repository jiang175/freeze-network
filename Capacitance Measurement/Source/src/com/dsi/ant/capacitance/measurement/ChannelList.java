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

    import android.annotation.SuppressLint;
    import android.app.Activity;
    import android.content.ComponentName;
    import android.content.Context;
    import android.content.Intent;
    import android.content.ServiceConnection;
    import android.content.SharedPreferences;
    import android.net.Uri;
    import android.os.Bundle;
    import android.os.Environment;
    import android.os.Handler;
    import android.os.IBinder;
    import android.util.Log;
    import android.util.SparseArray;
    import android.view.View;
    import android.view.View.OnClickListener;
    import android.view.WindowManager;
    import android.widget.AdapterView;
    import android.widget.ArrayAdapter;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.ExpandableListAdapter;
    import android.widget.ExpandableListView;
    import android.widget.ListView;
    import android.widget.SimpleExpandableListAdapter;
    import android.widget.Spinner;
    import android.widget.Toast;
    import android.widget.ToggleButton;

    import com.dsi.ant.capacitance.measurement.ChannelService.ChannelChangedListener;
    import com.dsi.ant.capacitance.measurement.ChannelService.ChannelServiceComm;
    import com.dsi.ant.channel.ChannelNotAvailableException;

    import java.io.File;
    import java.io.FileNotFoundException;
    import java.io.FileOutputStream;
    import java.io.OutputStreamWriter;
    import java.io.PrintWriter;
    import java.nio.ByteBuffer;
    import java.nio.ByteOrder;
    import java.util.ArrayList;
    import java.util.Calendar;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    //import com.google.common.io.LineProcessor;

    public class ChannelList extends Activity {

        private static final String TAG = ChannelList.class.getSimpleName();
        static String displayText = null;
        //calibration data
        //1st column reperesents devn
        //2nd [0]=old c1,[1]=old c2,[2]=old c3,[3]=old temp,[4]=old m,[5]=c
        //static float[][] cali_data = new float[60][6];//for capacitance conversioin, not being used
        static float[][] data = new float[61][8];//data matrix, every row represent data for one device,
        //[0] dev number, [1] c1, [2] c2. [3] c3, [4] dev on/off, [5] temp. [6] res10, [7] res11
        static float[][] cali = new float[61][7];
        private static int cali_count = 0;
        private static int c0 = 1200;
        private static int delay = 1;//delay time
        private static int onoff = 0;//RTD on/off flag
        private static int c_avg = 1000;// C_AVG
        private static int cytime = 4;
        private static int rdc_sel = 6;
        private static Calendar c; // calendar
        private static int list_pos = 0; // position for deivce in dev list

        List listDataHeader;
        List listDatachild;
        private Map<String, List<String>> Collections;

        //Coefficients for RTD conversion
        private static double aa = 3.9083 * Math.pow(10, -3);
        private static double bb = -5.775 * Math.pow(10, -7);
        private static double cc = -4.183 * Math.pow(10, -12);
        private final String PREF_TX_BUTTON_CHECKED_KEY = "ChannelList.TX_BUTTON_CHECKED";
        private boolean mCreateChannelAsMaster;
        private ArrayList<String> devlist = new ArrayList<String>();
        private SimpleExpandableListAdapter expListAdapter;
        //private String display;
        private ArrayAdapter<String> devlistListAdapter; // display adapter for device list
        private ChannelServiceComm mChannelService;

        private Spinner spinner1;
        private Spinner spinner2;

        private int lasttime;





        //private float[] c1_data = new float[30];
        //private float[] c2_data = new float[30];
        //private float[] c3_data = new float[30];
        //private int t1 = 0;
        private ServiceConnection mChannelServiceConnection;
        private ArrayList<String> mChannelDisplayList = new ArrayList<String>();
        private ArrayAdapter<String> mChannelListAdapter; // display adapter for data list
        private SparseArray<Integer> mIdChannelListIndexMap = new SparseArray<Integer>();
        private boolean mChannelServiceBound = false;
        {
            mChannelServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
                    Log.v(TAG, "mChannelServiceConnection.onServiceConnected...");
                    mChannelService = (ChannelServiceComm) serviceBinder;

                    // Sets a listener that handles channel events
                    mChannelService.setOnChannelChangedListener(new ChannelChangedListener() {
                        // Occurs when a channel has new info/data
                        @Override
                        public void onChannelChanged(final ChannelInfo newInfo) {
                            if (newInfo.error) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // ERROR
                                    }
                                });
                            } else {
                                //convert data
                                final int devn = newInfo.broadcastData[0];
                                if (devn <= 60 & devn > 0 && newInfo.broadcastData[1] < 9) {
                                    //Add device to dev list if the dev is new
                                    if (data[devn][4] == 0) {
                                        data[devn][4] = 1;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                devlistListAdapter.add("Device # " + devn);
                                                devlistListAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                    c = Calendar.getInstance();
                                    if (c.get(Calendar.SECOND) != lasttime) {

                                        lasttime = c.get(Calendar.SECOND);
                                        convertData(newInfo.broadcastData);//covert the incoming data and save it to data matrix
                                        //data[devn][5] = data[devn][5] + (float)11.5;
                                        //check if all 4 data are received
                                        if (onoff == 1) { //display the data when RTD is enabled
                                            //display data once all 6 data is received
                                            if ((data[devn][1] != 0) & (data[devn][2] != 0) & (data[devn][3] != 0) & (data[devn][7] != 0)) {
                                                ListView listView_devlist = (ListView) findViewById(R.id.listView_devlist);
                                                String display = getDisplayText(devn, data[devn][1], data[devn][2], data[devn][3], data[devn][0], data[devn][6], data[devn][7], data[devn][5]);
                                                //write data to local txt file
                                                writedata(devn, display);
                                                //devlistListAdapter.get
                                                //get the selected dev number, and diaplay the data to the list if dev is same
                                                if (Integer.parseInt(listView_devlist.getItemAtPosition(list_pos).toString().replaceAll("[^0-9]", "")) == devn) {
                                                    MyRunnable obj = new MyRunnable(display);
                                                    Handler handler = new Handler();
                                                    handler.post(obj);
                                                }
                                                //Toast.makeText(getApplicationContext(), "List"+listView_devlist.getItemAtPosition(list_pos).toString().charAt(9),Toast.LENGTH_LONG).show();
                                                //Make sure that the data selection on dev list stays when data is add to data list
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ListView listView_devlist = (ListView) findViewById(R.id.listView_devlist);
                                                        listView_devlist.requestFocusFromTouch();
                                                        listView_devlist.setSelection(list_pos);
                                                        listView_devlist.requestFocus();
                                                        devlistListAdapter.notifyDataSetChanged();
                                                        mChannelListAdapter.notifyDataSetChanged();
                                                        //Toast.makeText(getApplicationContext(), "List"+list_pos,Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                                Log.d(TAG, "TEMP:" + data[devn][6] + "    " + data[devn][7]);
                                                // clear the displayed data in data matrix, getting ready for next data
                                                data[devn][0] = 0;
                                                data[devn][1] = 0;
                                                data[devn][2] = 0;
                                                data[devn][3] = 0;
                                                data[devn][5] = 0;
                                                data[devn][6] = 0;
                                                data[devn][7] = 0;
                                            }
                                        } else {
                                            //display the data when RTD is disabled
                                            if ((data[devn][1] != 0) & (data[devn][2] != 0) & (data[devn][3] != 0)) {
                                                ListView listView_devlist = (ListView) findViewById(R.id.listView_devlist);
                                                String display = getDisplayTextoff(devn, data[devn][1], data[devn][2], data[devn][3], data[devn][0]);
                                                writedata(devn, display);
                                                //devlistListAdapter.get

                                                if (Integer.parseInt(listView_devlist.getItemAtPosition(list_pos).toString().replaceAll("[^0-9]", "")) == devn) {
                                                    //c1_data[t1] = data[devn][1];
                                                    //t1 = t1 + 1;
                                                    MyRunnable obj = new MyRunnable(display);
                                                    Handler handler = new Handler();
                                                    handler.post(obj);
                                                    Log.d(TAG, "OMGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG");

                                                    //inigraph();
                                                    // mChannelListAdapter.notifyDataSetChanged();
                                                }
                                                //Toast.makeText(getApplicationContext(), "List"+listView_devlist.getItemAtPosition(list_pos).toString().charAt(9),Toast.LENGTH_LONG).show();
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ListView listView_devlist = (ListView) findViewById(R.id.listView_devlist);
                                                        listView_devlist.requestFocusFromTouch();
                                                        listView_devlist.setSelection(list_pos);
                                                        listView_devlist.requestFocus();
                                                        devlistListAdapter.notifyDataSetChanged();
                                                        mChannelListAdapter.notifyDataSetChanged();
                                                        //Toast.makeText(getApplicationContext(), "List"+list_pos,Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                                //Log.d(TAG, "TEMP:" + data[devn][6] + "    " + data[devn][7]);
                                                data[devn][0] = 0;
                                                data[devn][1] = 0;
                                                data[devn][2] = 0;
                                                data[devn][3] = 0;
                                                data[devn][5] = 0;
                                                data[devn][6] = 0;
                                                data[devn][7] = 0;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Updates the UI to allow/disallow acquiring new channels
                        @Override
                        public void onAllowAddChannel(boolean addChannelAllowed) {
                            // Enable Add Channel button and Master/Slave toggle if
                            // adding channels is allowed
                        }
                    });

                    // Initial check when connecting to ChannelService if adding channels is allowed
                    // boolean allowAcquireChannel = mChannelService.isAddChannelAllowed();
                    //((Button)findViewById(R.id.button_AddChannel)).setEnabled(allowAcquireChannel);
                    //((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(allowAcquireChannel);

                    //refreshList();

                    Log.v(TAG, "...mChannelServiceConnection.onServiceConnected");
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    Log.v(TAG, "mChannelServiceConnection.onServiceDisconnected...");

                    // Clearing and disabling when disconnecting from ChannelService
                    mChannelService = null;

                    ((Button) findViewById(R.id.button_ClearChannels)).setEnabled(false);
                    ((Button) findViewById(R.id.button_AddChannel)).setEnabled(false);
                    //((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(false);

                    Log.v(TAG, "...mChannelServiceConnection.onServiceDisconnected");
                }
            };
        }

        @SuppressLint("DefaultLocale")
        private static String getDisplayText(int devn, float data1, float data2, float data3, float data8, float res10, float res11, float temp) {
            Log.v(TAG, "getDisplayText...");

            if (false) {
                //displayText = String.format("#%-6d !:%s", devn, channelInfo.getErrorString());
            } else {
                c = Calendar.getInstance();
                int month = c.get(Calendar.MONTH) + 1;
                int day = c.get(Calendar.DAY_OF_MONTH);
                int seconds = c.get(Calendar.SECOND);
                int hours = c.get(Calendar.HOUR);
                int mins = c.get(Calendar.MINUTE);
                int AM_orPM = c.get(Calendar.AM_PM);

                int int8 = (int) data8;
                if (hours == 00) {
                    hours = 12;
                }
                if(AM_orPM == 0) {
                    if (int8 != 0) {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d AM   %.8s      %.5f     %.5f     %.5f    %.5f    %.5f    %.1f", devn, month, day, hours, mins, seconds, Integer.toBinaryString(int8), data1, data2, data3, res10, res11, temp);
                    } else {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d AM   00000000      %.5f     %.5f     %.5f    %.5f    %.5f    %.1f", devn, month, day, hours, mins, seconds, data1, data2, data3, res10, res11, temp);
                    }
                }
                else
                {
                    if (int8 != 0) {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d PM   %.8s      %.5f     %.5f     %.5f    %.5f    %.5f    %.1f", devn, month, day, hours, mins, seconds, Integer.toBinaryString(int8), data1, data2, data3, res10, res11, temp);
                    } else {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d PM   00000000      %.5f     %.5f     %.5f    %.5f    %.5f    %.1f", devn, month, day, hours, mins, seconds, data1, data2, data3, res10, res11, temp);
                    }
                }
            }

            Log.v(TAG, "...getDisplayText");

            return displayText;
        }

        private static String getDisplayTextoff(int devn, float data1, float data2, float data3, float data8) {
            Log.v(TAG, "getDisplayText...");

            if (false) {
                //displayText = String.format("#%-6d !:%s", devn, channelInfo.getErrorString());
            } else {
                c = Calendar.getInstance();
                int month = c.get(Calendar.MONTH) + 1;
                int day = c.get(Calendar.DAY_OF_MONTH) ;
                int seconds = c.get(Calendar.SECOND);
                int hours = c.get(Calendar.HOUR_OF_DAY);
                int mins = c.get(Calendar.MINUTE);
                int int8 = (int) data8;
                int AM_orPM = c.get(Calendar.AM_PM);

                if (hours == 00) {
                    hours = 12;
                }

                if(AM_orPM == 0) {
                    if (int8 != 0) {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d AM   %.8s      %.5f     %.5f     %.5f", devn, month, day, hours, mins, seconds, Integer.toBinaryString(int8), data1, data2, data3);
                    } else {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d AM   00000000      %.5f     %.5f     %.5f", devn, month, day, hours, mins, seconds, data1, data2, data3);
                    }
                }
                else
                {
                    if (int8 != 0) {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d PM   %.8s      %.5f     %.5f     %.5f", devn, month, day, hours, mins, seconds, Integer.toBinaryString(int8), data1, data2, data3);
                    } else {
                        displayText = String.format("%d  %02d/%02d  %02d:%02d:%02d PM   00000000      %.5f     %.5f     %.5f", devn, month, day, hours, mins, seconds, data1, data2, data3);
                    }
                }


            }

            Log.v(TAG, "...getDisplayText");

            return displayText;
        }

        public static int checkdelay() {
            return delay;
        }
        public static float checkab(int dev,int num) {
            return cali[dev][num];
        }

        public static int checkoff() {
            return onoff;
        }

        public static int checkcavg() {
            return c_avg;
        }

        public static int checkcytime() { return cytime;}

        public static int checkrdc() {return rdc_sel;}

        /* Creating the Hashmap for the row */
        @SuppressWarnings("unchecked")
        private List createGroupList() {
            ArrayList result = new ArrayList();
            for( int i = 1 ; i < 15 ; ++i ) { // 15 groups........
                HashMap m = new HashMap();
                m.put("Group Item", "Device #" + i); // the key and it's value.
                result.add( m );
            }
            return (List)result;
        }


        /* creatin the HashMap for the children */
        @SuppressWarnings("unchecked")
        private List createChildList() {
            ArrayList result = new ArrayList();
            for( int i = 1 ; i < 15 ; ++i ) { // this -15 is the number of groups(Here it's fifteen)
                ArrayList secList = new ArrayList();

                HashMap child = new HashMap();
                child.put( "Sub Item", "a1 = " + Float.toString(cali[i][0]));
                secList.add( child );

                HashMap child1 = new HashMap();
                child1.put( "Sub Item", "b1 = " +  Float.toString(cali[i][1]) );
                secList.add( child1 );

                HashMap child2 = new HashMap();
                child2.put( "Sub Item", "a2 = " + Float.toString(cali[i][2]));
                secList.add( child2 );

                HashMap child3 = new HashMap();
                child3.put( "Sub Item", "b2 = " +  Float.toString(cali[i][3]) );
                secList.add( child3 );


                HashMap child4 = new HashMap();
                child4.put( "Sub Item", "a3 = " + Float.toString(cali[i][4]));
                secList.add( child4);

                HashMap child5 = new HashMap();
                child5.put( "Sub Item", "b3 = " +  Float.toString(cali[i][5]) );
                secList.add( child5 );


                result.add( secList );
            }
            return result;
        }


        private void initButtons() {//initilize data on UI
            Log.v(TAG, "initButtons...");
            final EditText text_c0 = (EditText) findViewById(R.id.editText1);
            final EditText text_delay = (EditText) findViewById(R.id.editText_delay);
            final EditText text_cavg = (EditText) findViewById(R.id.editText_cavg);
            final EditText text_cytime = (EditText) findViewById(R.id.editText_cy);
            final EditText text_rdc = (EditText) findViewById(R.id.editText_rdc);
            final EditText text_x = (EditText) findViewById(R.id.editText_x);
            final EditText text_y = (EditText) findViewById(R.id.editText_y);

            //xet x y value
            Button button_set = (Button) findViewById(R.id.button_set);
            //button_addChannel.setEnabled(false) 	;//mChannelServiceBound
            button_set.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int id = spinner1.getSelectedItemPosition() + 1;
                    int c_num = spinner2.getSelectedItemPosition() + 1;
                    if (!text_x.getText().toString().equals("")) {
                        cali[id][2 * c_num - 2] = Float.parseFloat(text_x.getText().toString());
                    }
                    if (!text_y.getText().toString().equals("")) {
                        cali[id][2 * c_num - 1] = Float.parseFloat(text_y.getText().toString());
                    }

                    //listDatachild.clear();
                    //listDataHeader.clear();
                   // listDataHeader = createGroupList();
                    listDataHeader = createGroupList();
                    listDatachild = createChildList();

                    ExpandableListView doubi = (ExpandableListView) findViewById(R.id.expandableListView);
                    expListAdapter =
                            new SimpleExpandableListAdapter(
                                    getApplicationContext(),
                                    listDataHeader,              // Creating group List.
                                    R.layout.group_row,             // Group item layout XML.
                                    new String[]{"Group Item"},  // the key of group item.
                                    new int[]{R.id.row_name},    // ID of each group item.-Data under the key goes into this TextView.
                                    listDatachild,              // childData describes second-level entries.
                                    R.layout.child_row,             // Layout for sub-level entries(second level).
                                    new String[]{"Sub Item"},      // Keys in childData maps to display.
                                    new int[]{R.id.grp_child}     // Data under the keys above go into these TextViews.
                            );
                    doubi.setAdapter(expListAdapter);

                }
            });

            //Register Add Channel Button handler
            Button button_addChannel = (Button) findViewById(R.id.button_AddChannel);
            //button_addChannel.setEnabled(false) 	;//mChannelServiceBound
            button_addChannel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Button button_addChannel = (Button) findViewById(R.id.button_AddChannel);
                    addNewChannel();
                    //Button button_addChannel = (Button)findViewById(R.id.button_AddChannel);
                    button_addChannel.setEnabled(false);
                    Toast.makeText(getApplicationContext(), "Channel Opened", Toast.LENGTH_LONG).show();
                }
            });

            //Temp On/Off toggle button listener
            final ToggleButton tempon = (ToggleButton) findViewById(R.id.toggleButton_temp);
            final ToggleButton program = (ToggleButton) findViewById(R.id.toggleButton_program);

            tempon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tempon.isChecked()) {
                        onoff = 1;
                        program.setChecked(false);
                        program.setEnabled(false);
                    } else {
                        onoff = 0;
                        program.setEnabled(true);
                    }
                }
            });

            program.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (program.isChecked()) {
                        onoff = 2;
                        tempon.setChecked(false);
                        tempon.setEnabled(false);
                    } else {
                        onoff = 0;
                        tempon.setEnabled(true);
                    }
                }
            });

            //Register Clear Channels Button handler
            Button button_clearChannels = (Button) findViewById(R.id.button_ClearChannels);
            button_clearChannels.setEnabled(mChannelServiceBound);
            button_clearChannels.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    //clearAllChannels();
                    mChannelDisplayList.clear();
                    mChannelListAdapter.notifyDataSetChanged();
                    devlistListAdapter.clear();
                    devlistListAdapter.notifyDataSetChanged();
                    list_pos = 0;
                }
            });

            Button button_getc0 = (Button) findViewById(R.id.button_getc0);
            button_getc0.setEnabled(mChannelServiceBound);
            button_getc0.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    //addNewChannel(mCreateChannelAsMaster);
                    if (!text_c0.getText().toString().equals("")) {
                        c0 = Integer.parseInt(text_c0.getText().toString());
                    }
                    if (!text_delay.getText().toString().equals("")) {
                        delay = Integer.parseInt(text_delay.getText().toString());
                    }
                    if (!text_cavg.getText().toString().equals("")) {
                        c_avg = Integer.parseInt(text_cavg.getText().toString());
                    }
                    if (!text_cytime.getText().toString().equals("")) {
                        cytime = Integer.parseInt(text_cytime.getText().toString());
                    }
                    if (!text_rdc.getText().toString().equals("")) {
                        rdc_sel = Integer.parseInt(text_rdc.getText().toString());
                    }
                }
            });
            text_c0.setText("1200");
            text_delay.setText("1");
            text_cavg.setText("1000");
            text_cytime.setText("4");
            text_rdc.setText("6");


            Button button_del = (Button) findViewById(R.id.button_clear);
            button_del.setEnabled(mChannelServiceBound);
            button_del.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    //addNewChannel(mCreateChannelAsMaster);
                    //writeToFile(displayText);
                    //clearAllChannels();

                    int i = 0;
                    for(i=1;i<60;i++)
                    {
                        data[i][4] = 0;
                        data[i][1] = 0;
                        data[i][2] = 0;
                        data[i][3] = 0;
                        data[i][6] = 0;
                        data[i][7] = 0;
                    }
                    mChannelListAdapter.clear();
                    devlistListAdapter.clear();
                    PrintWriter writer;
                    try {
                        writer = new PrintWriter("/sdcard/data1.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data2.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data3.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data4.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data5.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data6.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data7.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data8.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data9.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data10.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data11.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data12.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data13.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data14.txt");
                        writer.print("");
                        writer.close();
                        writer = new PrintWriter("/sdcard/data15.txt");
                        writer.print("");
                        writer.close();
                    } catch (FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });


            Button button_email = (Button) findViewById(R.id.button_email);
            button_email.setEnabled(mChannelServiceBound);
            button_email.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                    i.setType("text/plain");
                    //i.putExtra(Intent.EXTRA_EMAIL, new String[]{"j346266119@gmail.com"});
                    i.putExtra(Intent.EXTRA_SUBJECT, "Sensor Data upload");
                    i.putExtra(Intent.EXTRA_TEXT, "Have a nice day!");
                    File root = Environment.getExternalStorageDirectory();
                    File file1 = new File("/sdcard/data1.txt");
                    File file2 = new File("/sdcard/data2.txt");
                    File file3 = new File("/sdcard/data3.txt");
                    File file4 = new File("/sdcard/data4.txt");
                    File file5 = new File("/sdcard/data5.txt");
                    File file6 = new File("/sdcard/data6.txt");
                    File file7 = new File("/sdcard/data7.txt");
                    File file8 = new File("/sdcard/data8.txt");
                    File file9 = new File("/sdcard/data9.txt");
                    File file10 = new File("/sdcard/data10.txt");
                    File file11 = new File("/sdcard/data11.txt");
                    File file12 = new File("/sdcard/data12.txt");
                    File file13 = new File("/sdcard/data13.txt");
                    File file14 = new File("/sdcard/data14.txt");
                    File file15 = new File("/sdcard/data15.txt");

                    if (!file1.exists() || !file1.canRead()) {
                        Toast.makeText(ChannelList.this, "Attachment Error", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    ArrayList<Uri> uris = new ArrayList<Uri>();

                    //Uri uri = Uri.fromFile(file);
                    uris.add(Uri.fromFile(file1));
                    uris.add(Uri.fromFile(file2));
                    uris.add(Uri.fromFile(file3));
                    uris.add(Uri.fromFile(file4));
                    uris.add(Uri.fromFile(file5));
                    uris.add(Uri.fromFile(file6));
                    uris.add(Uri.fromFile(file7));
                    uris.add(Uri.fromFile(file8));
                    uris.add(Uri.fromFile(file9));
                    uris.add(Uri.fromFile(file10));
                    uris.add(Uri.fromFile(file11));
                    uris.add(Uri.fromFile(file12));
                    uris.add(Uri.fromFile(file13));
                    uris.add(Uri.fromFile(file14));
                    uris.add(Uri.fromFile(file15));

                    i.putExtra(Intent.EXTRA_STREAM, uris);
                    try {
                        startActivity(Intent.createChooser(i, "Output Data"));
                    } catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(ChannelList.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            Log.v(TAG, "...initButtons");
        }

        private void initPrefs() {
            Log.v(TAG, "initPrefs...");

            // Retrieves the app's current state of channel transmission mode
            // from preferences to handle app resuming.
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);

            mCreateChannelAsMaster = preferences.getBoolean(PREF_TX_BUTTON_CHECKED_KEY, true);

            Log.v(TAG, "...initPrefs");
        }

        private void savePrefs() {
            Log.v(TAG, "savePrefs...");

            // Saves the app's current state of channel transmission mode to preferences
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            editor.putBoolean(PREF_TX_BUTTON_CHECKED_KEY, mCreateChannelAsMaster);

            editor.commit();

            Log.v(TAG, "...savePrefs");
        }

        private void doBindChannelService() {
            Log.v(TAG, "doBindChannelService...");

            // Binds to ChannelService. ChannelService binds and manages connection between the
            // app and the ANT Radio Service
            Intent bindIntent = new Intent(this, ChannelService.class);
            startService(bindIntent);
            mChannelServiceBound = bindService(bindIntent, mChannelServiceConnection, Context.BIND_AUTO_CREATE);

            if (!mChannelServiceBound)   //If the bind returns false, run the unbind method to update the GUI
                doUnbindChannelService();

            Log.i(TAG, "  Channel Service binding = " + mChannelServiceBound);

            Log.v(TAG, "...doBindChannelService");
        }

        private void doUnbindChannelService() {
            Log.v(TAG, "doUnbindChannelService...");

            if (mChannelServiceBound) {
                unbindService(mChannelServiceConnection);

                mChannelServiceBound = false;
            }

            ((Button) findViewById(R.id.button_ClearChannels)).setEnabled(false);
            ((Button) findViewById(R.id.button_AddChannel)).setEnabled(false);
            // ((Button)findViewById(R.id.toggleButton_MasterSlave)).setEnabled(false);

            Log.v(TAG, "...doUnbindChannelService");
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Log.v(TAG, "onCreate...");

            mChannelServiceBound = false;

            setContentView(R.layout.activity_channel_list);

            initPrefs();

            devlistListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, devlist);
            ListView listView_devlist = (ListView) findViewById(R.id.listView_devlist);
            listView_devlist.setAdapter(devlistListAdapter);
            listView_devlist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    list_pos = position;
                }
            });

            listDataHeader = createGroupList();
            listDatachild = createChildList();

            ExpandableListView doubi = (ExpandableListView) findViewById(R.id.expandableListView);
            expListAdapter =
                    new SimpleExpandableListAdapter(
                            this,
                            listDataHeader,              // Creating group List.
                            R.layout.group_row,             // Group item layout XML.
                            new String[]{"Group Item"},  // the key of group item.
                            new int[]{R.id.row_name},    // ID of each group item.-Data under the key goes into this TextView.
                            listDatachild,              // childData describes second-level entries.
                            R.layout.child_row,             // Layout for sub-level entries(second level).
                            new String[]{"Sub Item"},      // Keys in childData maps to display.
                            new int[]{R.id.grp_child}     // Data under the keys above go into these TextViews.
                    );
            doubi.setAdapter(expListAdapter);

            spinner1 = (Spinner) findViewById(R.id.spinner);
            List<String> list = new ArrayList<String>();
            for(int i = 1; i<= 60;i++)
                {
                    list.add(Integer.toString(i));
                }
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,list);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner1.setAdapter(dataAdapter);


                spinner2 = (Spinner) findViewById(R.id.spinner_c);
                List<String> list1 = new ArrayList<String>();
                for(int i = 1; i<= 3;i++)
                {
                    list1.add(Integer.toString(i));
                }
                ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,list1);
                dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner2.setAdapter(dataAdapter2);


                mChannelListAdapter = new ArrayAdapter<String>(this,R.layout.mylist , android.R.id.text1, mChannelDisplayList);//android.R.layout.simple_list_item_1
                ListView listView_channelList = (ListView) findViewById(R.id.listView_channelList);
                listView_channelList.setAdapter(mChannelListAdapter);

                if (!mChannelServiceBound) doBindChannelService();

                initButtons();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.v(TAG, "...onCreate");
            }

        public void onBack() {
            finish();
        }

        @Override
        public void onDestroy() {
            Log.v(TAG, "onDestroy...");

            doUnbindChannelService();

            if (isFinishing()) {
                stopService(new Intent(this, ChannelService.class));
            }

            mChannelServiceConnection = null;

            savePrefs();

            Log.v(TAG, "...onDestroy");

            super.onDestroy();
        }

        private void convertData(byte[] capbyte) {
            int devn = (int) capbyte[0];
            int datanum = (int) capbyte[1];
            byte[] temp1 = new byte[8];
            byte[] temp2 = new byte[8];

            double t1 = 0;

            //Log.v(TAG, "before" + "[" +capbyte[0]+ "]"+ "[" +capbyte[1]+ "]"+ "[" +capbyte[2]+ "]"+ "[" +capbyte[3]+ "]"+ "[" +capbyte[4]+ "]"+ "[" +capbyte[5]+ "]"+ "[" +capbyte[6]+ "]"+ "[" +capbyte[7]+ "]");
            //converting data
            temp1[0] = 0;
            temp1[1] = capbyte[2];
            temp1[2] = capbyte[3];
            temp1[3] = capbyte[4];
            temp1[4] = 0;
            temp1[5] = 0;
            temp1[6] = 0;
            temp1[7] = 0;

            temp2[0] = 0;
            temp2[1] = capbyte[5];
            temp2[2] = capbyte[6];
            temp2[3] = capbyte[7];
            temp2[4] = 0;
            temp2[5] = 0;
            temp2[6] = 0;
            temp2[7] = 0;

            if (datanum == 1 && data[devn][2] == 0 && data[devn][3] == 0) {
                data[devn][0] = (float) (java.nio.ByteBuffer.wrap(temp1).getInt());
                if((float) java.nio.ByteBuffer.wrap(temp2).getInt() / 1000 > 300) {
                    data[devn][1] = (float) java.nio.ByteBuffer.wrap(temp2).getInt() / 2097152 * c0 ; //* c0;
                }else {
                    data[devn][1] = (float) java.nio.ByteBuffer.wrap(temp2).getInt() / 1000;
                }
            } else if (datanum == 1 && data[devn][2] != 0 && data[devn][3] != 0) {
                data[devn][2] = 0;
                data[devn][3] = 0;
            }

            if (datanum == 2 && data[devn][0] != 0 && data[devn][1] != 0) {

                if((float) java.nio.ByteBuffer.wrap(temp1).getInt()/1000 > 300)
                {
                    data[devn][2] = (float) java.nio.ByteBuffer.wrap(temp1).getInt() / 2097152 * c0;
                    data[devn][3] = (float) java.nio.ByteBuffer.wrap(temp2).getInt() / 2097152 * c0;
                }
                else {
                    data[devn][2] = (float) java.nio.ByteBuffer.wrap(temp1).getInt()  / 1000; //* c0;
                    data[devn][3] = (float) java.nio.ByteBuffer.wrap(temp2).getInt()  / 1000; //* c0;
                }
            }
            if (datanum == 3) {
                data[devn][6] = ((float) java.nio.ByteBuffer.wrap(temp1).getInt()) / 2097152;
                data[devn][7] = ((float) java.nio.ByteBuffer.wrap(temp2).getInt()) / 2097152;
                //data[devn][6] = data[devn][6] * (float)1.052;
                gettemp(devn);
            }
            if (datanum == 4) {
                short i = (short) ((capbyte[3] & 0xff) | (capbyte[2] << 8));
                if(i/100 > 60 ) data[devn][1] = (float)i / 10000 * 1200;
                else data[devn][1] = (float)i/100;
                i = (short) ((capbyte[5] & 0xff) | (capbyte[4] << 8));
                if(i/100 > 60 )data[devn][2] = (float)i / 10000 * 1200;
                else data[devn][2] = (float)i/100;
                i = (short) ((capbyte[7] & 0xff) | (capbyte[6] << 8));
                if(i/100 > 60 ) data[devn][3] = (float)i / 10000 * 1200;
                else data[devn][3] = (float)i/100;
            }
            if (datanum == 5) {
                data[devn][0] = (float) (java.nio.ByteBuffer.wrap(temp1).getInt());
                data[devn][1] = 9999;
                data[devn][2] = 9999;
                data[devn][3] = 9999;
            }
            //Log.v(TAG, "Data1" + "[" + capbyte[1] + "]");
        }

        // This method is called when 'Add Channel' button is clicked
        private void addNewChannel() {
            Log.v(TAG, "addNewChannel...");

            if (null != mChannelService) {
                ChannelInfo newChannelInfo;
                try {
                    // Telling the ChannelService to add a new channel. This method
                    // in ChannelService contains code required to acquire an ANT
                    // channel from ANT Radio Service.
                    newChannelInfo = mChannelService.addNewChannel();
                } catch (ChannelNotAvailableException e) {
                    // Occurs when a channel is not available. Printing out the
                    // stack trace will show why no channels are available.
                    Toast.makeText(this, "Channel Not Available", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (null != newChannelInfo) {
                    // Adding new channel info to the list
                    // addChannelToList(newChannelInfo);
                    // mChannelListAdapter.notifyDataSetChanged();
                }
            }

            Log.v(TAG, "...addNewChannel");
        }

        ;

        private void refreshList() {
            Log.v(TAG, "refreshList...");

            if (null != mChannelService) {
                ArrayList<ChannelInfo> chInfoList = mChannelService.getCurrentChannelInfoForAllChannels();

                mChannelDisplayList.clear();
                for (ChannelInfo i : chInfoList) {
                    //addChannelToList(i);
                }
                // mChannelListAdapter.notifyDataSetChanged();
            }

            Log.v(TAG, "...refreshList");
        }

        ;

        private void clearAllChannels() {
            Log.v(TAG, "clearAllChannels...");

            if (null != mChannelService) {
                // Telling ChannelService to close all the channels
                mChannelService.clearAllChannels();

                mChannelDisplayList.clear();
                mIdChannelListIndexMap.clear();
                mChannelListAdapter.notifyDataSetChanged();
            }

            Log.v(TAG, "...clearAllChannels");
        }

        private void writedata(int devn, String text) {
            String fileanme = "";
            if (devn == 1) fileanme = "/sdcard/data1.txt";
            else if (devn == 2) fileanme = "/sdcard/data2.txt";
            else if (devn == 3) fileanme = "/sdcard/data3.txt";
            else if (devn == 4) fileanme = "/sdcard/data4.txt";
            else if (devn == 5) fileanme = "/sdcard/data5.txt";
            else if (devn == 6) fileanme = "/sdcard/data6.txt";
            else if (devn == 7) fileanme = "/sdcard/data7.txt";
            else if (devn == 8) fileanme = "/sdcard/data8.txt";
            else if (devn == 9) fileanme = "/sdcard/data9.txt";
            else if (devn == 10) fileanme = "/sdcard/data10.txt";
            else if (devn == 11) fileanme = "/sdcard/data11.txt";
            else if (devn == 12) fileanme = "/sdcard/data12.txt";
            else if (devn == 13) fileanme = "/sdcard/data13.txt";
            else if (devn == 14) fileanme = "/sdcard/data14.txt";
            else if (devn == 15) fileanme = "/sdcard/data15.txt";
            try {
                File myFile = new File(fileanme);
                myFile.createNewFile();
                FileOutputStream fOut = new FileOutputStream(myFile, true);
                OutputStreamWriter myOutWriter =
                        new OutputStreamWriter(fOut);
                myOutWriter.append(String.format(text + "\n"));
                myOutWriter.flush();
                myOutWriter.close();
                fOut.close();
                //Toast.makeText(getBaseContext(),
                //       "Done writing SD card",
                //       Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                //   Toast.makeText(getBaseContext(), e.getMessage(),
                //          Toast.LENGTH_SHORT).show();
            }
        }
    /*
        private void inigraph() {

            float min = (c0 * 3);
            float max = 0;

            String url = "http://chart.apis.google.com/chart?chs=640x240" +
                    "&chd=t:" +
                    String.format("%.5f", c1_data[0]);

            if (t1 > 20) {

                for (int i = 1; i <= t1; i++) {
                    c1_data[i - 1] = c1_data[i];
                }
                t1 = 20;
            }

            for (int i = 1; i < t1; i++) {
                url = url + String.format(",%.5f", c1_data[i]);
                if (c1_data[i] > max) {
                    max = c1_data[i];
                }
                if (c1_data[i] < min) {
                    min = c1_data[i];
                }
            }
            url = url + "&chm=N*5,000000,0,-1,11" +
                    "&cht=lc" +
                    "&chds=" +
                    String.format("%.5f,%.5f", min, max);
            //"&chxt=x,y,r" +
            //"&chxl=2:|min|average|max&chxp=2,10,35,75";
            WebView mCharViewc1;
            mCharViewc1 = (WebView) findViewById(R.id.webView);
            mCharViewc1.loadUrl(url);
        }
    */

        private void gettemp(int devn) {
            double t1 = 0;
            if (data[devn][6] >= 1) {
                if ((float) ((-aa + Math.sqrt(Math.pow(aa, 2) - 4 * bb * (1 - data[devn][6]))) / (2 * bb)) >= 155) {
                    data[devn][5] = (float) ((-aa - Math.sqrt(Math.pow(aa, 2) - 4 * bb * (1 - data[devn][6]))) / (2 * bb));
                } else {
                    data[devn][5] = (float) ((-aa + Math.sqrt(Math.pow(aa, 2) - 4 * bb * (1 - data[devn][6]))) / (2 * bb));
                }
            } else {
                if (data[devn][6] >= 0.78319 && data[devn][6] <= 0.80306) {//-55 -50
                    t1 = -55;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;
                } else if (data[devn][6] <= 0.82290) {//-45
                    t1 = -50;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;
                } else if (data[devn][6] <= 0.84271) {//-40
                    t1 = -45;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.86248) {//-35
                    t1 = -40;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.88222) {//-30
                    t1 = -35;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.90192) {//-25
                    t1 = -30;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.92160) {//-20
                    t1 = -25;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.94124) {//-15
                    t1 = -20;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.96086) {//-10
                    t1 = -15;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 0.98044) {//-5
                    t1 = -10;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;

                } else if (data[devn][6] <= 1) {//0
                    t1 = -10;
                    while (data[devn][6] >= (1 + aa * t1 + bb * Math.pow(t1, 2) + cc * Math.pow(t1, 4) - cc * 100 * Math.pow(t1, 3))) {
                        t1 = t1 + 0.01;
                    }
                    data[devn][5] = (float) t1;
                }
            }
        }

        private class MyRunnable implements Runnable {
            private String display;

            public MyRunnable(String _data) {
                this.display = _data;
            }
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mChannelDisplayList.add(display);
                        devlistListAdapter.notifyDataSetChanged();
                        mChannelListAdapter.notifyDataSetChanged();
                    }
                });
            }
        }

        public static int unsignedByteToInt(byte b) {
            return (int) b & 0xFF;
        }


    }



