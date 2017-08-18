package eh.bus.asyndobuss.bean;

import java.util.Map;

/**
 * 业务创建事件
 *
 * 会诊业务说明：在BussCreateEvent事件中，object指的是会诊申请单的对象。在其他事件中bussId指的是会诊result对象中的ID。
 *
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class BussCreateEvent extends BussEvent{

    protected Object object;

    protected Map<String,Object> otherInfo;

    /**
     * 构造函数，个人业务
     * @param object
     * @param bussType
     */
    public BussCreateEvent(Object object, int bussType){
        this(object,bussType,null);
    }

    public BussCreateEvent(Object object, int bussType, Map<String,Object> otherInfo){
        setObject(object);
        setBussType(bussType);
        setOtherInfo(otherInfo);
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public Map<String, Object> getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(Map<String, Object> otherInfo) {
        this.otherInfo = otherInfo;
    }
}
