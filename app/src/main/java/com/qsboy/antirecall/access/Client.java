/*
 * Copyright © 2016 - 2018 by GitHub.com/JasonQS
 * anti-recall.qsboy.com
 * All Rights Reserved
 */

package com.qsboy.antirecall.access;

import android.content.Context;
import android.graphics.Rect;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.qsboy.antirecall.db.Dao;
import com.qsboy.antirecall.db.Messages;
import com.qsboy.utils.ImageHelper;
import com.qsboy.utils.NodesInfo;
import com.qsboy.utils.XToast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.PrimitiveIterator;

import static com.qsboy.utils.ImageHelper.searchImageFile;

public abstract class Client {

    AccessibilityNodeInfo titleNode;
    AccessibilityNodeInfo chatGroupViewNode;
    AccessibilityNodeInfo groupNode;
    AccessibilityNodeInfo redPegNode;
    AccessibilityNodeInfo otherMsgNode;
    AccessibilityNodeInfo inputNode;
    AccessibilityNodeInfo sendBtnNode;

    static String added = "";
    String TAG = "Client";
    String title = "";
    String subName = "";
    String message = "";
    String pSubName = "";
    String pMessage = "";
    String RECALL = "撤回了一条消息";
    String client;
    boolean isRecalledMsg;
    boolean isOtherMsg;
    boolean isWX;

    private Dao dao;
    private Context context;

    public Client(Context context) {
        dao = Dao.getInstance(context);
        this.context = context;
    }

    protected abstract boolean init(AccessibilityNodeInfo root);

    protected abstract void parser(AccessibilityNodeInfo group);

    void findRecalls(AccessibilityNodeInfo root, AccessibilityEvent event) {
        new Recalls().findRecalls(root, event);
    }

    private class Recalls {

        private List<Messages> contextList = new ArrayList<>();
        private String nextMessage;
        private String prevMessage;
        private String nextSubName;
        private String prevSubName;
        private int prevPos;
        private int nextPos;
        private int unknownRecalls;
        private ArrayList<String> subNameArray = new ArrayList<>();

        void findRecalls(AccessibilityNodeInfo root, AccessibilityEvent event) {
            // TODO: 通知栏收到的表情 聊天框收到的表情 乱码 根据 utf 位置判断
            // TODO: 没找到 再根据 subName 找最后一个
            CharSequence cs = event.getSource().getText();
            if (cs == null)
                return;
            String string = cs.toString();
            if (!string.contains(RECALL))
                return;

            if (!init(root))
                return;
            NodesInfo.show(root, TAG);

            initContext(event);

            Log.w(TAG, "findRecalls: unknownRecalls: " + unknownRecalls + " prevMsg: " + prevMessage + " nextMsg: " + nextMessage);
            if (prevMessage == null && nextMessage == null) {
                XToast.build(context, "不能全屏撤回哦").show();
                return;
            }

            // TODO: 如果前后都没找到 就输出最后一个subName的消息
            // TODO: 查找到的要和subName做比较 如果不对要继续找
            // TODO: 前后确认

            Messages messages;
            ArrayList<Integer> prevList;
            ArrayList<Integer> nextList;
            // 有上下文
            if (prevMessage != null && nextMessage != null) {
                prevList = dao.queryByMessage(title, isWX, prevSubName, prevMessage);
                nextList = dao.queryByMessage(title, isWX, nextSubName, nextMessage);
                Log.d(TAG, "findRecalls: prevList: " + prevList);
                Log.d(TAG, "findRecalls: nextList: " + nextList);
                for (Integer p : prevList) {
                    for (Integer n : nextList) {
                        if (n - p == 2) {
                            prevPos = p;
                            nextPos = n;
                            Log.d(TAG, "findRecalls: prevPos: " + prevPos);
                            findRecallByPrev();
                            return;
                        }
                    }
                }
                notFound();
            } else
                // 有上文
                if (prevMessage != null) {
                    prevList = dao.queryByMessage(title, isWX, prevSubName, prevMessage);
                    prevPos = prevList.get(0);
                    Log.d(TAG, "findRecalls: prevPos: " + prevPos);
                    findRecallByPrev();
                } else {
                    // 有下文
                    nextList = dao.queryByMessage(title, isWX, nextSubName, nextMessage);
                    nextPos = nextList.get(0);
                    Log.d(TAG, "findRecalls: nextPos: " + nextPos);
                    findRecallByNext();
                }
        }

        private void findRecallByPrev() {
            Messages messages;
            int maxID = dao.getMaxID(title, isWX);
            for (int i = 0; i < unknownRecalls; i++) {
                while (true) {
                    prevPos++;
                    if (prevPos >= maxID) {
                        notFound();
                        return;
                    }
                    //删除了中间某条消息
                    if ((messages = dao.queryById(title, isWX, prevPos + i)) == null)
                        continue;
                    String sn = subNameArray.get(i);
                    Log.i(TAG, "findRecalls: sub name: " + sn);
                    // 确认是这个人撤回的
                    if (!messages.getSubName().equals(sn))
                        continue;
                    break;
                }
                addRecall(messages);
            }
        }

        private void findRecallByNext() {
            Messages messages;
            for (int i = unknownRecalls - 1; i >= 0; i--) {
                while (true) {
                    nextPos--;
                    if (nextPos == 0) {
                        notFound();
                        return;
                    }
                    if ((messages = dao.queryById(title, isWX, nextPos - i)) == null)
                        continue;
                    String sn = subNameArray.get(i);
                    Log.i(TAG, "findRecalls: sub name: " + sn);
                    if (!messages.getSubName().equals(subNameArray.get(i)))
                        continue;
                    break;
                }
                addRecall(messages);
            }
        }

        private void notFound() {
            XToast.build(context, "没有找到撤回的消息呢\n运行软件之前的消息是看不到的哦").show();
        }

        private void addRecall(Messages messages) {
            Log.i(TAG, "addRecall: " + messages.getMessage());
            if ("[图片]".equals(messages.getMessage())) {
                messages.setImages(searchImageFile(context, messages.getTime(), client));
                String image;
                if (messages.getImages().contains(" "))
                    image = messages.getImages().split("")[0];
                else image = messages.getImages();
                XToast.build(context, messages.getSubName() + ": [图片]" + image).show();
            } else {
                XToast.build(context, messages.getSubName() + ": " + messages.getMessage()).show();
            }
            if (dao.existRecall(messages, prevSubName, prevMessage, nextSubName, nextMessage))
                return;
            dao.addRecall(messages, prevSubName, prevMessage, nextSubName, nextMessage);

        }

        private void initContext(AccessibilityEvent event) {
            prevMessage = null;
            nextMessage = null;
            prevSubName = null;
            nextSubName = null;
            subNameArray.clear();
            int topPos = 0;
            int botPos = chatGroupViewNode.getChildCount();

            Rect clickRect = new Rect();
            Rect nodeRect = new Rect();
            int pos = 0;

            event.getSource().getBoundsInScreen(clickRect);

            for (int i = 0; i < chatGroupViewNode.getChildCount(); i++) {
                AccessibilityNodeInfo group = chatGroupViewNode.getChild(i);
                parser(group);
                chatGroupViewNode.getChild(i).getBoundsInScreen(nodeRect);
                //当前点击的地方
                if (nodeRect.contains(clickRect))
                    pos = i;
                // 获取上下文
                // pos未赋值
                if (pos == 0) {
                    if (message.contains(RECALL)) {
                        Log.i(TAG, "initContext: p sub name: " + subName);
                        subNameArray.add(subName);
                    } else {
                        // 保证撤回消息是连续的
                        subNameArray.clear();
                        prevMessage = message;
                        prevSubName = subName;
                        topPos = i;
                    }
                } else {
                    if (message.contains(RECALL)) {
                        Log.i(TAG, "initContext: n sub name: " + subName);
                        subNameArray.add(subName);
                    } else {
                        nextMessage = message;
                        nextSubName = subName;
                        botPos = i;
                        break;
                    }
                }
            }
            unknownRecalls = botPos - topPos - 1;
        }
    }

    public void onContentChanged(AccessibilityNodeInfo root) {
        if (!init(root))
            return;
        NodesInfo.show(root, TAG);
        if (isOtherMsg) {
            onOtherMsg();
            return;
        }

        AccessibilityNodeInfo group = chatGroupViewNode.getChild(chatGroupViewNode.getChildCount() - 2);
        if (group == null)
            return;
        parser(group);
        // 记录上一条 防止重复加
        pMessage = message;
        pSubName = subName;

        group = chatGroupViewNode.getChild(chatGroupViewNode.getChildCount() - 1);
        if (group == null)
            return;
        parser(group);

        addMsg(false);

    }

    public void onNotificationChanged(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        if (texts.isEmpty() || texts.size() == 0)
            return;
        for (CharSequence text : texts) {
            if (text == null)
                return;
            String string = text.toString();
            Log.w(TAG, "Notification text: " + string);
            if (string.equals("你的帐号在电脑登录"))
                return;

            int i = string.indexOf(':');
            if (i < 1) {
                Log.d(TAG, "Notification does not contains ':'");
                return;
            }
            title = string.substring(0, i);
            message = string.substring(i + 2);
            subName = title;
            //是群消息
            // TODO: 特别关心
            // TODO: 当前是 QQ 群 微信群待测试
            int j = title.indexOf('(');
            if (j > 0 && title.charAt(i - 1) == ')') {
                message = string.substring(i + 1);
                subName = title.substring(0, j);
                title = title.substring(j + 1, i - 1);
            }

            addMsg(true);
        }
    }

    /**
     * 判断是否是在其他人的聊天界面收到了消息
     */
    private void onOtherMsg() {
        String string = otherMsgNode.getText().toString();
        int i = string.indexOf(":");
        int j = string.lastIndexOf("-");
        //如果在联系人列表里出现过的,那么就是在其他人的聊天界面
        message = string.substring(i + 1);
        //包含"-" 可能是群
        if (j > 0) {
            title = string.substring(0, j);
            subName = string.substring(j + 1, i);
            addMsg(false);
            // TODO 也可能不是群
//            if (tables.contains(DBHelper.Table_Prefix_QQ_And_Tim + string.substring(0, i))) {
//                addMsg(false);
//                return;
//            }
        } else {
            title = string.substring(0, i);
            addMsg(false);
        }
    }

    public void addMsg(boolean force) {
        String temp = title + "-" + subName + ": " + message;
        if (!force) {
            if (added.equals(temp))
                return;
            Log.d(TAG, "addRecall: message: " + message + "\t prevMessage: " + pMessage);
            if (dao.existMessage(title, isWX, message, pMessage, subName, pSubName)) {
                Log.d(TAG, "addMsg: already exits");
                return;
            }
        }
        added = temp;
        Log.e(TAG, "Add message: " + added);
        dao.addMessage(title, subName, isWX, message);
    }

}
