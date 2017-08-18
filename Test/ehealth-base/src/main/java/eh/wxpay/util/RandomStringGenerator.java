package eh.wxpay.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * User: rizenguo
 * Date: 2014/10/29
 * Time: 14:18
 */
public class RandomStringGenerator {

    /**
     * 获取一定长度的随机字符串
     * @param length 指定字符串长度
     * @return 一定长度的字符串
     */
    public static String getRandomStringByLength(int length) {
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }
    /**
     * 获取一定长度的随机字符串
     * @param length 指定字符串长度
     * @return 一定长度的字符串
     */
    public static String getRandomNumByLength(int length) {
        String base = "0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }
    /**
     * @function 生成商户订单号/退款单号
     * @author zhangjr
     * @param prefix 商户订单号/退款单号前缀
     * @date 2015-12-17
     * @return String 
     */
    public static String getOrderNo(String prefix){
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    	Date date = new Date();
    	return prefix + sdf.format(date) + getRandomStringByLength(4);
    }
    
}
