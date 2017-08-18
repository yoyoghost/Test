package eh.hisdata;

import ctd.controller.exception.ControllerException;
import ctd.schema.Schema;
import ctd.schema.SchemaController;
import ctd.schema.SchemaItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zxq on 2017-7-12.
 */
public class HisDataService {

    public static void main(String[] args) {
        String fieldName = "patientName";
        String firstZ = fieldName.substring(0, 1).toUpperCase();
        String sub = fieldName.substring(1, fieldName.length());
        System.out.println("get" + firstZ + sub);
    }

    public void setValue(String key, Object value, Object o) {
        Class tt = o.getClass();
        try {
            Field field = tt.getDeclaredField(key);
            Class<?> type = field.getType();
            Method method_get = tt.getMethod(getMethodNameByFieldName(key, "get"));
            method_get.invoke(o);
            Method method_set = tt.getMethod(getMethodNameByFieldName(key, "set"), type);
            method_set.invoke(o, value);
            System.out.println(o);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, String>> getFields(Object res) {
        Class tt = res.getClass();
        Field[] fields = tt.getDeclaredFields();
        List<Map<String, String>> resList = new ArrayList<>();
        try {
            Schema schema = SchemaController.instance().get(tt.getName());
            for (Field f : fields) {
                Map<String, String> s = new HashMap<>();
                String fieldName = f.getName();
                s.put("key", fieldName);
                Class<?> type = f.getType();
                s.put("type", type.getName());
                SchemaItem file = schema.getItem(fieldName);
                if (file == null) {
                    s.put("alias", "");
                    continue;
                }
                String alias = file.getAlias();
                s.put("alias", alias);
                System.out.println(s);
                resList.add(s);
            }
            return resList;
        } catch (ControllerException e) {
            e.printStackTrace();
        }
        return resList;
    }

    private String getMethodNameByFieldName(String fieldName, String diff) {
        String firstZ = fieldName.substring(0, 1).toUpperCase();
        String sub = fieldName.substring(1, fieldName.length());
        return diff + firstZ + sub;
    }
}
