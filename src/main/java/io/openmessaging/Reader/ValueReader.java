package io.openmessaging.Reader;

import io.openmessaging.Constants;
import io.openmessaging.Context.ValueContext;
import io.openmessaging.Message;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Created by huzhebin on 2019/07/23.
 */
public class ValueReader {

    /**
     * 文件通道
     */
    private FileChannel fileChannel;

    /**
     * 堆外内存
     */
    private ByteBuffer buffer1 = ByteBuffer.allocateDirect(Constants.VALUE_CAP);

    private ByteBuffer buffer2 = ByteBuffer.allocateDirect(Constants.VALUE_CAP);

    private ByteBuffer buffer;

    private Future future;

    private ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 消息总数
     */
    private int messageNum = 0;

    private volatile boolean inited = false;

    public ValueReader() {
        try {
            fileChannel = new RandomAccessFile(Constants.URL + "100.value", "rw").getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace(System.out);
        }
        buffer = buffer1;

    }

    public void put(Message message) {
        ByteBuffer tmpBuffer = buffer;
        if (!buffer.hasRemaining()) {
            buffer.flip();
            try {
                if (future == null) {
                    future = executorService.submit(() -> fileChannel.write(tmpBuffer));
                } else {
                    if(!future.isDone()){
                        System.out.println("value block");
                    }
                    future.get();
                    future = executorService.submit(() -> fileChannel.write(tmpBuffer));
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            if (buffer == buffer1) {
                buffer = buffer2;
            } else {
                buffer = buffer1;
            }
            buffer.clear();
        }
        buffer.putLong(message.getA());
        messageNum++;
    }

    public void init() {
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

    public long get(int index, ValueContext valueContext) {
        if (!inited) {
            synchronized (this) {
                if (!inited) {
                    init();
                    inited = true;
                }
            }
        }
        if (index >= valueContext.bufferMinIndex && index < valueContext.bufferMaxIndex) {
            valueContext.buffer.position((index - valueContext.bufferMinIndex) * Constants.VALUE_SIZE);
        } else {
            valueContext.buffer.clear();
            try {
                fileChannel.read(valueContext.buffer, ((long) index) * Constants.VALUE_SIZE);
                valueContext.bufferMinIndex = index;
                valueContext.bufferMaxIndex = Math.min(index + Constants.VALUE_NUM, messageNum);
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
            valueContext.buffer.flip();
        }
        return valueContext.buffer.getLong();
    }

    public long avg(int offsetA, int offsetB, long aMin, long aMax, ValueContext valueContext) {
        long sum = 0;
        int count = 0;
        long value;
        //找到合适的buffer
        updateContext(offsetA, offsetB, valueContext);
        valueContext.buffer.clear();
        try {
            fileChannel.read(valueContext.buffer, ((long) offsetA) * Constants.VALUE_SIZE);
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
        valueContext.buffer.flip();
        while (offsetA < offsetB) {
            value = valueContext.buffer.getLong();
            if (value <= aMax && value >= aMin) {
                sum += value;
                count++;
            }
            offsetA++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private void updateContext(int offsetA, int offsetB, ValueContext valueContext) {
        int i = (offsetB - offsetA) / Constants.VALUE_NUM;
        valueContext.buffer = valueContext.bufferList.get(i);
        valueContext.bufferMinIndex = offsetA;
        valueContext.bufferMaxIndex = Math.min(offsetA + (Constants.VALUE_NUM * (i + 1)), messageNum);
    }
}