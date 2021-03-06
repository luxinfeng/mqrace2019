package io.openmessaging;

/**
 * Created by huzhebin on 2019/07/23.
 */
public class Constants {
    // writer缓冲区
    public final static int WRITER_CAP = 16 * 1024;

    // data写入缓冲区
    public final static int DATA_CAP = 17 * 128 * 1024;

    // value写入缓冲区
    public final static int VALUE_CAP = 16 * 128 * 1024;

    // data 的大小
    public final static int DATA_SIZE = 34;

    public final static int DATA_NUM = 4 * 1024;

    // value 的大小
    public final static int VALUE_SIZE = 8;


    // value 切片数量
    public final static int VALUE_BLOCKS = 7;

    // 初始流量
    public final static int INITIAL_FLOW = 1000000;

    //public final static String URL = "D:\\data\\";

    public final static String URL = "/alidata1/race2019/data/";
}
