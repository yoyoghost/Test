package eh.bus.asyndobuss.bean;

/**
 * 业务接收事件
 *
 * 会诊业务说明：在BussCreateEvent事件中，object指的是会诊申请单的对象。在其他事件中bussId指的是会诊result对象中的ID。
 *
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class BussAcceptEvent extends BussEvent{

    private Integer acceptDoctorId;

    public BussAcceptEvent(Integer bussId,Integer bussType,Integer acceptDoctorId){
        setBussId(bussId);
        setBussType(bussType);
        setAcceptDoctorId(acceptDoctorId);
    }

    public Integer getAcceptDoctorId() {
        return acceptDoctorId;
    }

    public void setAcceptDoctorId(Integer acceptDoctorId) {
        this.acceptDoctorId = acceptDoctorId;
    }
}
