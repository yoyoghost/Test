package eh.bus.service.emergency;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import ctd.util.AppContextHolder;
import eh.entity.bus.Emergency;
import eh.utils.params.support.DBParamLoader;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Administrator on 2017/6/12 0012.
 */
public class EmergencyPriceCalculator {
    private static final String HOUR_PATTERN = "HH";
    private static List<TimeSlot> timeSlotList = Lists.newArrayList();

    /**
     * 应 Mr.zhou要求，从数据库读取，so需要每次访问都要初始化，将静态代码段改为方法
     * 红色：时间段：
     * 23:01-6:00  1000元
     * 6:01-8:00    300元
     * 8:01-17:00  50元
     * 17：01-22:59   300元
     */
    private static void init() {
        timeSlotList.clear();
        DBParamLoader paramLoader = AppContextHolder.getBean("paramLoader", DBParamLoader.class);
        String emergencyPrice = paramLoader.getParam("TMP_EMERGENCY_PRICE");
        List<DbUnit> unitList = JSONObject.parseArray(emergencyPrice, DbUnit.class);
        for(DbUnit unit : unitList){
            timeSlotList.add(new TimeSlot(unit));
        }
    }

    public static Double getPrice(){
        init();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(HOUR_PATTERN);
        int currentHour = Integer.valueOf(sdf.format(date));
        for(TimeSlot ts : timeSlotList){
            if(ts.hit(currentHour)){
                return ts.getPrice();
            }
        }
        return 0.0;
    }


    private static class TimeSlot{
        private Set<Integer> sliceSet;
        private Double price;

        public TimeSlot(DbUnit unit){
            this(unit.getStart(), unit.getEnd(), unit.getPrice());
        }

        public TimeSlot(int start, int end, double price) {
            sliceSet = new HashSet<>();
            if(start>end){
                for(int i=start; i<24; i++){
                    sliceSet.add(i);
                }
                for(int i=0; i<end; i++){
                    sliceSet.add(i);
                }
            }else {
                for (int i = start; i <end; i++) {
                    sliceSet.add(i);
                }
            }
            this.price = price;
        }

        public boolean hit(int currentHour){
            return sliceSet.contains(currentHour);
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }
    }

    private static class DbUnit{
        private int start;
        private int end;
        private double price;

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }
    }
}
