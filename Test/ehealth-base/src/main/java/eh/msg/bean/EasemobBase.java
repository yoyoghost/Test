package eh.msg.bean;

import java.io.Serializable;

/**
 * Created by shenhj on 2017/7/10.
 */
public abstract class EasemobBase implements Serializable {

    protected EasemobBusType type;

    public EasemobBase(EasemobBusType type){
        this.type = type;
    }

    public EasemobBusType getType() {
        return type;
    }

    public void setType(EasemobBusType type) {
        this.type = type;
    }
}

enum EasemobBusType {

    REGISTE(1,"registe"),
    LOGIN(2,"login"),
    UNREGISTER(3,"unregister"),
    UNLOGIN(4,"unlogin");

    private int code;
    private String value;

    private EasemobBusType(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}