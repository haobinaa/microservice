package com.limit.guava;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author HaoBin
 * @Create 2020/3/30 20:56
 * @Description: 短网址算法实现
 **/
public class ShortAddress {

    public static void main(String[] args) {
        String url = "baidu.com";
        String url1 = "2.com";
        System.out.println(shorten(url));
        System.out.println(shorten(url1));
    }

    // 字符可能性结构
    static final char[] DIGITS =
        { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
            'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
            'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };


    // 生成短网址
    public static String shorten(String longUrl) {
        // hashcode 绝对值
        long myseq = Math.abs(longUrl.hashCode());
        return to62RadixString(myseq);
    }


    // 对 62 取 mod 组成索引， 拼接返回字符串
    private static String to62RadixString(long seq) {
        StringBuilder sBuilder = new StringBuilder();
        while (true) {
            // 对 62 mod
            int remainder = (int) (seq % 62);
            sBuilder.append(DIGITS[remainder]);
            // 下一个62
            seq = seq / 62;
            if (seq == 0) {
                break;
            }
        }
        return sBuilder.toString();
    }

}
