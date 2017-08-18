package eh.bus.asyndobuss.bean;

/**
 * 业务完成事件
 *
 * 会诊业务说明：在BussCreateEvent事件中，object指的是会诊申请单的对象。在其他事件中bussId指的是会诊result对象中的ID。
 *
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class BussFinishEvent extends BussEvent{

    //会诊业务申请单ID
    private Integer meetClinicId;

    public BussFinishEvent(Integer bussId,Integer bussType){
        setBussId(bussId);
        setBussType(bussType);
    }

    public BussFinishEvent(Integer bussId,Integer bussType,Integer meetClinicId){
        this(bussId,bussType);
        setMeetClinicId(meetClinicId);
    }

    public Integer getMeetClinicId() {
        return meetClinicId;
    }

    public void setMeetClinicId(Integer meetClinicId) {
        this.meetClinicId = meetClinicId;
    }
}
