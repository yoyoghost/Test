package eh.util;

import java.lang.reflect.Method;

import javax.persistence.Table;
/**
 * Created by andywang on 2017/6/19.
 */
public class HibernateToolsUtil {


    /**
     * 获得表名
     *
     * @param clazz 映射到数据库的po类
     * @return String
     */
    @SuppressWarnings("unchecked")
    public static String getTableName(Class clazz) {
        Table annotation = (Table)clazz.getAnnotation(Table.class);
        if(annotation != null){
            return annotation.name();
        }

        return null;
    }


}
