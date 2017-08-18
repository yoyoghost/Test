package eh.bus.asyndobuss.bean;

/**
 * 业务事件
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class BussEvent {

    protected Integer bussType;

    protected Integer bussId;

    public BussEvent(){}

    public Integer getBussType() {
        return bussType;
    }

    public void setBussType(Integer bussType) {
        this.bussType = bussType;
    }

    public Integer getBussId() {
        return bussId;
    }

    public void setBussId(Integer bussId) {
        this.bussId = bussId;
    }
}
