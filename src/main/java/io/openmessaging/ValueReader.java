package io.openmessaging;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by huzhebin on 2019/07/23.
 */
public class ValueReader {
    /**
     * 编号
     */
    private int num;

    /**
     * 文件通道
     */
    private FileChannel fileChannel;

    /**
     * 堆外内存
     */
    private ByteBuffer buffer = ByteBuffer.allocateDirect(Constants.VALUE_PAGE_SIZE);

    /**
     * 消息总数
     */
    private int messageNum = 0;

    /**
     * 缓存中最大消息
     */
    private long bufferMaxIndex = -1L;

    /**
     * 缓存中最小消息
     */
    private long bufferMinIndex = -1L;

    private volatile boolean inited = false;

    private List<ValueTag> valueTagList = new ArrayList<>();

    private int tag = -1;

    private byte aByte = 0;

    ValuePage valuePage = new ValuePage();

    int pageIndex = 0;

    int i = 0;

    int k = 0;

    int offsetA = -1;

    int offsetB = -1;

    LRUCache<Integer, ValuePage> pageCache = new LRUCache<>(Constants.VALUE_CACHE_SIZE / Constants.VALUE_PAGE_SIZE);

    public ValueReader(int num) {
        this.num = num;
        RandomAccessFile memoryMappedFile = null;
        try {
            memoryMappedFile = new RandomAccessFile(Constants.URL + num + ".value", "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace(System.out);
        }
        fileChannel = memoryMappedFile.getChannel();
    }

    public void put(Message message) {
        int value = (int) (message.getA() - message.getT());
        if (buffer.hasRemaining()) {
            buffer.flip();
            try {
                fileChannel.write(buffer);
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
            buffer.clear();
        }

        if (tag == -1 || value - tag > Byte.MAX_VALUE || value - tag < Byte.MIN_VALUE) {
            tag = value;
            valueTagList.add(new ValueTag(tag, messageNum));
        }

        if (i == Constants.VALUE_PAGE_SIZE) {
            pageCache.put(k, valuePage);
            k++;
            valuePage = new ValuePage();
            i = 0;
        }
        if (k < Constants.VALUE_CACHE_SIZE / Constants.VALUE_PAGE_SIZE) {
            valuePage.bytes[i] = (byte) (value - tag);
            i++;
        }
        buffer.put((byte) (value - tag));

        messageNum++;
    }

    public void init() {
        pageIndex = -1;
        valuePage = null;
        int remain = buffer.remaining();
        if (remain > 0) {
            buffer.flip();
            try {
                fileChannel.write(buffer);
                buffer.clear();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    public int getValue(int offset) {

        if (!inited) {
            synchronized (this) {
                if (!inited) {
                    init();
                    inited = true;
                }
            }
        }
        if (offset < offsetA || offset >= offsetB) {
            int thisIndex = Collections.binarySearch(valueTagList, new ValueTag(0, offset));
            if (thisIndex < 0) {
                thisIndex = Math.max(0, -(thisIndex + 2));
            }
            tag = valueTagList.get(thisIndex).getValue();
            offsetA = valueTagList.get(thisIndex).getOffset();
            if (thisIndex < valueTagList.size() - 1) {
                offsetB = valueTagList.get(thisIndex + 1).getOffset();
            } else {
                offsetB = messageNum;
            }
        }

        if (pageIndex == offset / Constants.VALUE_PAGE_SIZE) {
            return tag + valuePage.bytes[offset % Constants.VALUE_PAGE_SIZE];
        }

        pageIndex = offset / Constants.VALUE_PAGE_SIZE;

        valuePage = pageCache.get(pageIndex);

        if (valuePage == null) {
            try {
                buffer.clear();
                fileChannel.read(buffer, pageIndex * Constants.VALUE_PAGE_SIZE);
                buffer.flip();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
            valuePage = pageCache.getOldest();
            if (valuePage == null) {
                valuePage = new ValuePage();
            }
            buffer.get(valuePage.bytes, 0, buffer.limit());
            pageCache.put(pageIndex, valuePage);
        }

        return tag + valuePage.bytes[offset % Constants.VALUE_PAGE_SIZE];

        //        if (index >= bufferMinIndex && index < bufferMaxIndex) {
        //            buffer.position((int) (index - bufferMinIndex) * Constants.VALUE_SIZE);
        //        } else {
        //            buffer.clear();
        //            try {
        //                fileChannel.read(buffer, index * Constants.VALUE_SIZE);
        //                bufferMinIndex = index;
        //                bufferMaxIndex = Math.min(index + Constants.VALUE_NUM, messageNum);
        //            } catch (IOException e) {
        //                e.printStackTrace(System.out);
        //            }
        //            buffer.flip();
        //        }
        //
        //        return buffer.getInt();
    }

}