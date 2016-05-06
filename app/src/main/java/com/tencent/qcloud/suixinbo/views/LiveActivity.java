package com.tencent.qcloud.suixinbo.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.av.sdk.AVView;
import com.tencent.qcloud.suixinbo.R;
import com.tencent.qcloud.suixinbo.adapters.ChatMsgListAdapter;
import com.tencent.qcloud.suixinbo.avcontrollers.AvConstants;
import com.tencent.qcloud.suixinbo.avcontrollers.QavsdkControl;
import com.tencent.qcloud.suixinbo.model.ChatEntity;
import com.tencent.qcloud.suixinbo.model.LiveInfoJson;
import com.tencent.qcloud.suixinbo.model.MyCurrentLiveInfo;
import com.tencent.qcloud.suixinbo.model.MySelfInfo;
import com.tencent.qcloud.suixinbo.presenters.EnterLiveHelper;
import com.tencent.qcloud.suixinbo.presenters.LiveHelper;
import com.tencent.qcloud.suixinbo.presenters.OKhttpHelper;
import com.tencent.qcloud.suixinbo.presenters.viewinface.EnterQuiteRoomView;
import com.tencent.qcloud.suixinbo.presenters.viewinface.LiveView;
import com.tencent.qcloud.suixinbo.utils.Constants;
import com.tencent.qcloud.suixinbo.views.customviews.HeartLayout;
import com.tencent.qcloud.suixinbo.views.customviews.InputTextMsgDialog;
import com.tencent.qcloud.suixinbo.views.customviews.MembersDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Live直播类
 */
public class LiveActivity extends Activity implements EnterQuiteRoomView, LiveView, View.OnClickListener {
    private EnterLiveHelper mEnterRoomProsscessHelper;
    private LiveHelper mLiveHelper;
    private static final String TAG = LiveActivity.class.getSimpleName();

    private ArrayList<ChatEntity> mArrayListChatEntity;
    private ChatMsgListAdapter mChatMsgListAdapter;
    private static final int MINFRESHINTERVAL = 500;
    private boolean mBoolRefreshLock = false;
    private boolean mBoolNeedRefresh = false;
    private final Timer mTimer = new Timer();
    private ArrayList<ChatEntity> mTmpChatList = new ArrayList<ChatEntity>();//缓冲队列
    private TimerTask mTimerTask = null;
    private static final int REFRESH_LISTVIEW = 5;
    private Dialog mMemberDialog;
    private HeartLayout mHeartLayout;
    private TextView mLikeTv;
    private HeartBeatTask mHeartBeatTask;//心跳
    private Timer mHearBeatTimer, mVideoTimer;
    private int thumbUp = 0;
    private String selectVideoId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//去掉信息栏
        setContentView(R.layout.activity_live);

        //进出房间的协助类
        mEnterRoomProsscessHelper = new EnterLiveHelper(this, this);
        //房间内的交互协助类
        mLiveHelper = new LiveHelper(this, this);

        initView();
        registerReceiver();

        //进入房间流程
        mEnterRoomProsscessHelper.startEnterRoom();

//        QavsdkControl.getInstance().setCameraPreviewChangeCallback();

    }

    private static class MyHandler extends Handler {
        private final WeakReference<LiveActivity> mActivity;

        public MyHandler(LiveActivity activity) {
            mActivity = new WeakReference<LiveActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (null == mActivity.get()) {
                return;
            }
            mActivity.get().processInnerMsg(msg);
        }
    }


    private void processInnerMsg(Message msg) {
        switch (msg.what) {
            case REFRESH_LISTVIEW:
                doRefreshListView();
                break;
        }
        return;
    }


    private final MyHandler mHandler = new MyHandler(this);


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //AvSurfaceView 初始化成功
            if (action.equals(AvConstants.ACTION_SURFACE_CREATED)) {
                //打开摄像头
                if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
                    mLiveHelper.openCameraAndMic();
                } else {
                    //成员请求主播画面
//                    String host = MyCurrentLiveInfo.getHostID();
//                    int requestCount = MyCurrentLiveInfo.getCurrentRequestCount();
//                    mLiveHelper.RequestViewList(host, requestCount, requestCount + 1, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
//                    requestCount = requestCount + 1;
//                    MyCurrentLiveInfo.setCurrentRequestCount(requestCount);
                }

            }
            //主播数据OK
//            if (action.equals(AvConstants.ACTION_HOST_ENTER)) {
//                //主播上线才开始渲染视频
//                //主播上线 请求主画面
//                if (MySelfInfo.getInstance().getIdStatus() == Constants.MEMBER) {
//
//                    //成员请求主播画面
//                    String host = MyCurrentLiveInfo.getHostID();
//                    int requestCount = MyCurrentLiveInfo.getCurrentRequestCount();
//                    mLiveHelper.RequestViewList(host, requestCount, requestCount + 1, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
//                    requestCount = requestCount + 1;
//                    MyCurrentLiveInfo.setCurrentRequestCount(requestCount);
////                    mEnterRoomProsscessHelper.initAvUILayer(avView);
//
////                        String host = MyCurrentLiveInfo.getHostID();
////                        mLiveHelper.RequestViewList(host, 0, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
//                }
//            }
            if (action.equals(AvConstants.ACTION_CAMERA_OPEN_IN_LIVE)) {//有人打开摄像头

                ArrayList<String> ids = intent.getStringArrayListExtra("ids");

                //如果是自己本地直接渲染

                for (String id : ids) {
                    if (id.equals(MySelfInfo.getInstance().getId())) {
                        showVideoView(true, id);
                        return;
//                        ids.remove(id);
                    }
                }
                //其他人一并获取
                int requestCount = MyCurrentLiveInfo.getCurrentRequestCount();
                mLiveHelper.RequestViewList(ids);
                requestCount = requestCount + ids.size();
                MyCurrentLiveInfo.setCurrentRequestCount(requestCount);
//                }
            }

            if (action.equals(AvConstants.ACTION_SHOW_VIDEO_MEMBER_INFO)) {//点击成员
                selectVideoId = intent.getStringExtra(AvConstants.EXTRA_IDENTIFIER);
                mHostbottomLy.setVisibility(View.INVISIBLE);
                mVideoMemberCtrlBt.setVisibility(View.VISIBLE);

            }
            if (action.equals(AvConstants.ACTION_HOST_LEAVE)) {//主播结束
                quiteLivePassively();
            }


        }
    };

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AvConstants.ACTION_SURFACE_CREATED);
        intentFilter.addAction(AvConstants.ACTION_HOST_ENTER);
        intentFilter.addAction(AvConstants.ACTION_CAMERA_OPEN_IN_LIVE);
        intentFilter.addAction(AvConstants.ACTION_SHOW_VIDEO_MEMBER_INFO);
        intentFilter.addAction(AvConstants.ACTION_HOST_LEAVE);
        registerReceiver(mBroadcastReceiver, intentFilter);

    }

    private void unregisterReceiver() {
        unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * 初始化UI
     */
    private View avView;
    private TextView BtnBack, BtnInput, Btnflash, BtnSwitch, BtnBeauty, BtnMic, BtnScreen, BtnHeart, BtnNormal, mVideoChat, BtnCtrlVideo, BtnCtrlMic, BtnHungup;
    private ListView mListViewMsgItems;
    private LinearLayout mHostbottomLy, mMemberbottomLy, mVideoMemberCtrlBt;
    private FrameLayout mFullControllerUi, mBackgound;

    private void initView() {

        mHostbottomLy = (LinearLayout) findViewById(R.id.host_bottom_layout);
        mMemberbottomLy = (LinearLayout) findViewById(R.id.member_bottom_layout);
        mVideoChat = (TextView) findViewById(R.id.video_interact);
        mHeartLayout = (HeartLayout) findViewById(R.id.heart_layout);
        mVideoMemberCtrlBt = (LinearLayout) findViewById(R.id.video_member_bottom_layout);
        mVideoMemberCtrlBt.setVisibility(View.INVISIBLE);
        if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
            mHostbottomLy.setVisibility(View.VISIBLE);
            mMemberbottomLy.setVisibility(View.GONE);
            Btnflash = (TextView) findViewById(R.id.flash_btn);
            BtnSwitch = (TextView) findViewById(R.id.switch_cam);
            BtnBeauty = (TextView) findViewById(R.id.beauty_btn);
            BtnMic = (TextView) findViewById(R.id.mic_btn);
            BtnCtrlVideo = (TextView) findViewById(R.id.camera_controll);
            BtnCtrlMic = (TextView) findViewById(R.id.mic_controll);
            BtnHungup = (TextView) findViewById(R.id.close_member_video);
            BtnCtrlVideo.setOnClickListener(this);
            BtnCtrlMic.setOnClickListener(this);
            BtnHungup.setOnClickListener(this);


            BtnScreen = (TextView) findViewById(R.id.fullscreen_btn);
            mVideoChat.setVisibility(View.VISIBLE);
            Btnflash.setOnClickListener(this);
            BtnSwitch.setOnClickListener(this);
            BtnBeauty.setOnClickListener(this);
            BtnMic.setOnClickListener(this);
            BtnScreen.setOnClickListener(this);
            mVideoChat.setOnClickListener(this);


            mMemberDialog = new MembersDialog(this, R.style.dialog);

        } else {
            mMemberbottomLy.setVisibility(View.VISIBLE);
            mHostbottomLy.setVisibility(View.GONE);
            BtnInput = (TextView) findViewById(R.id.message_input);
            BtnInput.setOnClickListener(this);
            mLikeTv = (TextView) findViewById(R.id.member_send_good);
            mLikeTv.setOnClickListener(this);
            mVideoChat.setVisibility(View.GONE);
        }
        BtnNormal = (TextView) findViewById(R.id.normal_btn);
        BtnNormal.setOnClickListener(this);
        mFullControllerUi = (FrameLayout) findViewById(R.id.controll_ui);
        mBackgound = (FrameLayout) findViewById(R.id.av_screen_layout);
        mBackgound.setOnClickListener(this);
        avView = findViewById(R.id.av_video_layer_ui);
        //直接初始化SurfaceView
//        mEnterRoomProsscessHelper.initAvUILayer(avView);
        BtnBack = (TextView) findViewById(R.id.btn_back);
        BtnBack.setOnClickListener(this);

        mListViewMsgItems = (ListView) findViewById(R.id.im_msg_listview);
        mArrayListChatEntity = new ArrayList<ChatEntity>();
        mChatMsgListAdapter = new ChatMsgListAdapter(this, mListViewMsgItems, mArrayListChatEntity);
        mListViewMsgItems.setAdapter(mChatMsgListAdapter);
    }


    @Override
    protected void onResume() {
        super.onResume();
        QavsdkControl.getInstance().onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        QavsdkControl.getInstance().onPause();
    }


    /**
     * 直播心跳
     */
    private class HeartBeatTask extends TimerTask {
        @Override
        public void run() {
            String host = MyCurrentLiveInfo.getHostID();
            Log.i(TAG, "HeartBeatTask " + host);
            OKhttpHelper.getInstance().sendHeartBeat(host, 10, 10, 100);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mHearBeatTimer) {
            mHearBeatTimer.cancel();
            mHearBeatTimer = null;
        }
        thumbUp = 0;
        MyCurrentLiveInfo.setCurrentRequestCount(0);
        unregisterReceiver();
        QavsdkControl.getInstance().onDestroy();
    }


    /**
     * 点击Back键
     */
    @Override
    public void onBackPressed() {
        quiteLiveByPurpose();

    }

    /**
     * 主动退出直播
     */
    private void quiteLiveByPurpose() {
        //如果是直播，发消息
        if (MySelfInfo.getInstance().getIdStatus() == Constants.MEMBER)
            mLiveHelper.sendGroupMessage(Constants.AVIMCMD_ExitLive, "");
        mLiveHelper.unInitTIMListener();
        mEnterRoomProsscessHelper.quiteLive();
    }

    /**
     * 被动退出直播
     */
    private void quiteLivePassively() {
        mLiveHelper.unInitTIMListener();
        mEnterRoomProsscessHelper.quiteLive();
    }


    /**
     * 完成进出房间流程
     *
     * @param id_status
     * @param isSucc
     */
    @Override
    public void EnterRoomComplete(int id_status, boolean isSucc) {
        Toast.makeText(LiveActivity.this, "EnterRoomComplete " + id_status + " isSucc " + isSucc, Toast.LENGTH_SHORT).show();
        //必须得进入房间之后才能初始化UI
        mEnterRoomProsscessHelper.initAvUILayer(avView);

        if (isSucc == true) {
            //IM初始化
            mLiveHelper.initTIMListener("" + MyCurrentLiveInfo.getRoomNum());

            if (id_status == Constants.HOST) {//主播方式加入房间成功
                //开启摄像头渲染画面
                Log.i(TAG, "createlive EnterRoomComplete isSucc" + isSucc);
            } else {//以成员方式加入房间成功
                mLiveHelper.sendGroupMessage(Constants.AVIMCMD_EnterLive, "");
            }
        }
    }


    @Override
    public void QuiteRoomComplete(int id_status, boolean succ, LiveInfoJson liveinfo) {
//        Toast.makeText(LiveActivity.this, "" + liveinfo.getTitle()+"end", Toast.LENGTH_SHORT).show();
        finish();
    }


    private static int index = 0;

    /**
     * 加载视频数据
     *
     * @param isLocal 是否是本地数据
     * @param id      身份
     */
    @Override
    public void showVideoView(boolean isLocal, String id) {
        Log.i(TAG, "showVideoView " + id);
        //渲染本地Camera
        if (isLocal == true) {
            Log.i(TAG, "showVideoView host :" + MySelfInfo.getInstance().getId());
            QavsdkControl.getInstance().setSelfId(MySelfInfo.getInstance().getId());
            QavsdkControl.getInstance().setLocalHasVideo(true, MySelfInfo.getInstance().getId());
            //主播通知用户服务器
            if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
                mEnterRoomProsscessHelper.notifyServerCreateRoom();
                mHearBeatTimer = new Timer(true);
                mHeartBeatTask = new HeartBeatTask();
                mHearBeatTimer.schedule(mHeartBeatTask, 1000, 3 * 1000);
            }
        } else {
            QavsdkControl.getInstance().setRemoteHasVideo(true, id, AVView.VIDEO_SRC_TYPE_CAMERA);
        }

    }


    @Override
    public void showInviteDialog() {
        Toast.makeText(LiveActivity.this, "yes i receive a host invitation and open my Camera", Toast.LENGTH_SHORT).show();
        mLiveHelper.openCameraAndMic();
        mLiveHelper.sendC2CMessage(Constants.AVIMCMD_MUlTI_JOIN, "", MyCurrentLiveInfo.getHostID());
    }

    @Override
    public void refreshText(String text, String name) {
        if (text != null) {
            refreshTextListView(name, text, Constants.TEXT_TYPE);
        }
    }

    @Override
    public void refreshThumbUp() {
        thumbUp++;
        mHeartLayout.addFavor();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                quiteLiveByPurpose();
                break;
            case R.id.message_input:
                inputMsgDialog();
                break;
            case R.id.member_send_good:
                // 添加飘星动画
                mHeartLayout.addFavor();
                mLiveHelper.sendC2CMessage(Constants.AVIMCMD_Praise, "", MyCurrentLiveInfo.getHostID());
                break;
            case R.id.flash_btn:
                if (mLiveHelper.isFrontCamera() == true) {
                    Toast.makeText(LiveActivity.this, "this is front cam", Toast.LENGTH_SHORT).show();
                } else {
                    mLiveHelper.toggleFlashLight();
                }
                break;
            case R.id.switch_cam:
                mLiveHelper.switchCamera();
                break;
            case R.id.beauty_btn:

                break;
            case R.id.mic_btn:
                if (mLiveHelper.isMicOpen() == true) {
                    BtnMic.setBackgroundResource(R.drawable.icon_mic_close);
                    mLiveHelper.muteMic();
                } else {
                    BtnMic.setBackgroundResource(R.drawable.icon_mic_open);
                    mLiveHelper.openMic();
                }

                break;
            case R.id.fullscreen_btn:
                mFullControllerUi.setVisibility(View.INVISIBLE);
                BtnNormal.setVisibility(View.VISIBLE);
                break;
            case R.id.av_screen_layout:
                mHostbottomLy.setVisibility(View.VISIBLE);
                mVideoMemberCtrlBt.setVisibility(View.INVISIBLE);
                break;
            case R.id.normal_btn:
                mFullControllerUi.setVisibility(View.VISIBLE);
                BtnNormal.setVisibility(View.GONE);
                break;
            case R.id.video_interact:
                mMemberDialog.show();
                break;
            case R.id.camera_controll:
                break;
            case R.id.mic_controll:
                break;
            case R.id.close_member_video:
                mLiveHelper.sendC2CMessage(Constants.AVIMCMD_MULT_CANCEL_INTERACT, selectVideoId, selectVideoId);
                break;
        }
    }


    /**
     * 发消息弹出框
     */
    private void inputMsgDialog() {
        InputTextMsgDialog inputMsgDialog = new InputTextMsgDialog(this, R.style.inputdialog, mLiveHelper, this);
        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        WindowManager.LayoutParams lp = inputMsgDialog.getWindow().getAttributes();

        lp.width = (int) (display.getWidth()); //设置宽度
        inputMsgDialog.getWindow().setAttributes(lp);
        inputMsgDialog.setCancelable(true);
        inputMsgDialog.show();
    }


    /**
     * 消息刷新显示
     *
     * @param name    发送者
     * @param context 内容
     * @param type    类型 （上线线消息和 聊天消息）
     */
    public void refreshTextListView(String name, String context, int type) {
        ChatEntity entity = new ChatEntity();
        entity.setSenderName(name);
        entity.setContext(context);
        entity.setType(type);
        //mArrayListChatEntity.add(entity);
        notifyRefreshListView(entity);
        //mChatMsgListAdapter.notifyDataSetChanged();

        mListViewMsgItems.setVisibility(View.VISIBLE);
        Log.d(TAG, "refreshTextListView height " + mListViewMsgItems.getHeight());

        if (mListViewMsgItems.getCount() > 1) {
            if (true)
                mListViewMsgItems.setSelection(0);
            else
                mListViewMsgItems.setSelection(mListViewMsgItems.getCount() - 1);
        }
    }


    /**
     * 通知刷新消息ListView
     */
    private void notifyRefreshListView(ChatEntity entity) {
        mBoolNeedRefresh = true;
        mTmpChatList.add(entity);
        if (mBoolRefreshLock) {
            return;
        } else {
            doRefreshListView();
        }
    }

    /**
     * 刷新ListView并重置状态
     */
    private void doRefreshListView() {
        if (mBoolNeedRefresh) {
            mBoolRefreshLock = true;
            mBoolNeedRefresh = false;
            mArrayListChatEntity.addAll(mTmpChatList);
            mTmpChatList.clear();
            mChatMsgListAdapter.notifyDataSetChanged();

            if (null != mTimerTask) {
                mTimerTask.cancel();
            }
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    Log.v(TAG, "doRefreshListView->task enter with need:" + mBoolNeedRefresh);
                    mHandler.sendEmptyMessage(REFRESH_LISTVIEW);
                }
            };
            //mTimer.cancel();
            mTimer.schedule(mTimerTask, MINFRESHINTERVAL);
        } else {
            mBoolRefreshLock = false;
        }
    }
}
