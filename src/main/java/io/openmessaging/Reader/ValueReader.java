package io.openmessaging.Reader;

import io.openmessaging.Context;
import io.openmessaging.Message;
import io.openmessaging.ValueTags;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by huzhebin on 2019/08/07.
 */
public class ValueReader {
    private long max = 0;

    private byte[] cache = new byte[Integer.MAX_VALUE - 2];

    private ValueTags valueTags = new ValueTags(6000000);

    private int msgNum = 0;

    private volatile boolean init = false;

    private int tag = -1;

    private int add = 0;

//    AtomicLong three = new AtomicLong();
//
//    AtomicLong four = new AtomicLong();
//
//    AtomicLong c = new AtomicLong();
//
//    AtomicInteger c1 = new AtomicInteger();
//
//    AtomicInteger c2 = new AtomicInteger();
//
//    AtomicInteger c3 = new AtomicInteger();
//
//    AtomicInteger c4 = new AtomicInteger();
//
//    AtomicInteger c5 = new AtomicInteger();

    public void put(Message message) {
        int value = (int) message.getA();
        if (tag == -1 || value > tag + 255 || value < tag) {
            if (add > max) {
                max = add;
            }
            if (add != 0) {
                valueTags.add(add);
                add = 0;
            }

            tag = value;
            valueTags.add(value, msgNum);
        }
        add = add + value - tag;
        cache[msgNum] = (byte) (value - tag);
        msgNum++;
    }

    public void init() {
        if (add != 0) {
            valueTags.add(add);
            add = 0;
            valueTags.inited();
        }
        System.out.println("max:" + max + " valueTags size:" + valueTags.size());
        init = true;
    }

    public int get(int offset, Context context) {
        if (!init) {
            synchronized (this) {
                if (!init) {
                    init();
                }
            }
        }

        if (offset < context.offsetA || offset >= context.offsetB) {
            int tagIndex = valueTags.offsetIndex(offset);
            context.tag = valueTags.getTag(tagIndex);
            context.offsetA = valueTags.getOffset(tagIndex);
            if (tagIndex == valueTags.size() - 1) {
                context.offsetB = msgNum;
            } else {
                context.offsetB = valueTags.getOffset(tagIndex + 1);
            }
        }
        return context.tag + (cache[offset] & 0xff);
    }

    long avg(int offsetA, int offsetB, long aMin, long aMax, Context context) {
        long total = 0;
        int count = 0;
        //long start = System.nanoTime();
        if (offsetA < context.offsetA || offsetA >= context.offsetB) {
            context.tagIndex = valueTags.offsetIndex(offsetA);
            context.tag = valueTags.getTag(context.tagIndex);
            context.offsetA = valueTags.getOffset(context.tagIndex);
            if (context.tagIndex == valueTags.size() - 1) {
                context.offsetB = msgNum;
            } else {
                context.offsetB = valueTags.getOffset(context.tagIndex + 1);
            }
            if (valueTags.getMin(context.tagIndex) > aMax) {
                return 0;
            }
        }
        int value;
        //long mid = System.nanoTime();
        while (offsetA < offsetB) {
            //c.getAndIncrement();
            if (offsetA >= context.offsetB) {
                context.tagIndex++;
                if (valueTags.getMin(context.tagIndex) > aMax) {
                    context.tagIndex--;
                    return count == 0 ? 0 : total / count;
                }
                context.tag = valueTags.getTag(context.tagIndex);
                context.offsetA = valueTags.getOffset(context.tagIndex);
                if (context.tagIndex == valueTags.size() - 1) {
                    context.offsetB = msgNum;
                } else {
                    context.offsetB = valueTags.getOffset(context.tagIndex + 1);
                }
            }
            //            if (context.offsetA == offsetA) {
            //                c1.getAndIncrement();
            //                if (context.tag + 255 <= aMax) {
            //                    c2.getAndIncrement();
            //                    if (context.tag >= aMin) {
            //                        c3.getAndIncrement();
            //                        if (context.offsetB < offsetB) {
            //                            c4.getAndIncrement();
            //                            int num = context.offsetB - context.offsetA;
            //                            total += num * (long) context.tag + valueTags.getAdd(context.tagIndex);
            //                            count += num;
            //                            offsetA = context.offsetB;
            //                            context.tagIndex++;
            //                            if (valueTags.getMin(context.tagIndex) > aMax) {
            //                                return count == 0 ? 0 : total / count;
            //                            }
            //                            context.tag = valueTags.getTag(context.tagIndex);
            //                            context.offsetA = valueTags.getOffset(context.tagIndex);
            //                            if (context.tagIndex == valueTags.size() - 1) {
            //                                context.offsetB = msgNum;
            //                            } else {
            //                                context.offsetB = valueTags.getOffset(context.tagIndex + 1);
            //                            }
            //                            continue;
            //                        }
            //                    }
            //                }
            //            }
            if (context.offsetA == offsetA && context.tag + 255 <= aMax && context.tag >= aMin && context.offsetB < offsetB) {
                //c1.getAndIncrement();
                int num = context.offsetB - context.offsetA;
                total += num * (long) context.tag + valueTags.getAdd(context.tagIndex);
                count += num;
                offsetA = context.offsetB;
                context.tagIndex++;
                if (valueTags.getMin(context.tagIndex) > aMax) {
                    context.tagIndex--;
                    return count == 0 ? 0 : total / count;
                }
                context.tag = valueTags.getTag(context.tagIndex);
                context.offsetA = valueTags.getOffset(context.tagIndex);
                if (context.tagIndex == valueTags.size() - 1) {
                    context.offsetB = msgNum;
                } else {
                    context.offsetB = valueTags.getOffset(context.tagIndex + 1);
                }
                continue;
            }
            if (context.tag + 255 < aMin) {
                offsetA = context.offsetB;
                context.tagIndex++;
                context.tag = valueTags.getTag(context.tagIndex);
                context.offsetA = valueTags.getOffset(context.tagIndex);
                if (context.tagIndex == valueTags.size() - 1) {
                    context.offsetB = msgNum;
                } else {
                    context.offsetB = valueTags.getOffset(context.tagIndex + 1);
                }
                continue;
            }
            value = context.tag + (cache[offsetA] & 0xff);
            if (value >= aMin && value <= aMax) {
                total += value;
                count++;
            }
            offsetA++;
        }
//        long end = System.nanoTime();
//        System.out.println("three:" + three.addAndGet(mid - start) + " four:" + four.addAndGet(end - mid));
        //System.out.println("c:" + c.intValue());
        //System.out.println("count:" + count + " c:" + c.longValue() + " c1:" + c1.intValue() + " c2:" + c2.intValue() + " c3:" + c3.intValue() + " c4:" + c4.intValue() + " c5:" + c5.intValue() + " c/c4:" + (c4.intValue() == 0 ? 0 : c.longValue() / c4.intValue()) + " aMin:" + aMin + " aMax:" + aMax);
        return count == 0 ? 0 : total / count;
    }
}
