package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.constant.MeetClinicConstant;
import eh.entity.bus.MeetClinicResult;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

public abstract class SaveMeetReportDAO extends
        HibernateSupportDelegateDAO<MeetClinicResult> {
    public static final Logger log = Logger.getLogger(SaveMeetReportDAO.class);

    public SaveMeetReportDAO() {
        super();
        this.setEntityName(MeetClinicResult.class.getName());
        this.setKeyField("meetClinicResultId");
    }

    public MeetClinicResult save(MeetClinicResult mr) {
        if (mr.getMeetCenter() == null) {
            mr.setMeetCenter(false);
        }
        if (mr.getMeetCenterStatus() == null) {
            mr.setMeetCenterStatus(0);
        }
        return super.save(mr);
    }

    @RpcService
    @DAOMethod
    public abstract MeetClinicResult getByMeetClinicResultId(
            int meetClinicResultid);

    /**
     * 保存会诊意见服务
     *
     * @param meetClinicResult
     * @throws DAOException
     * @author LF
     */
    @RpcService
    public void saveMeetReportNew(final MeetClinicResult meetClinicResult) {
        log.info("保存会诊意见服务(saveMeetReportNew):meetClinicResult="
                + JSONUtils.toString(meetClinicResult));
        if (meetClinicResult.getMeetClinicResultId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetClinicResultId is required =====");
        }
        if (meetClinicResult.getExeOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== exeOrgan is required =====");
        }
        if (meetClinicResult.getExeDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== exeDepart is required =====");
        }
        if (meetClinicResult.getExeDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== exeDoctor is required =====");
        }
        if (meetClinicResult.getMeetReport() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetReport is required =====");
        }
        MeetClinicResult meetClinicResult2 = getByMeetClinicResultId(meetClinicResult
                .getMeetClinicResultId());
        Integer effeStatus = meetClinicResult2.getEffectiveStatus();
        if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您已被移出该会诊~");
        }
        if (meetClinicResult2.getExeStatus() == 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该会诊未开始，不能提交会诊报告！");
        }
        if (meetClinicResult2.getExeStatus() == 9) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，对方医生已取消该会诊申请");
        }
        if (meetClinicResult2.getExeStatus() == 2
                || meetClinicResult2.getExeStatus() == 8) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该会诊已结束，不能提交会诊报告！");
        }
        if (meetClinicResult2.getExeDoctor() != null && !meetClinicResult2.getExeDoctor().equals(meetClinicResult.getExeDoctor())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "啊哦！您慢了一步，已有团队其他成员参与...");
        }
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        String sql = "UPDATE MeetClinicResult SET exeOrgan =:exeOrgan , exeDepart =:exeDepart , exeDoctor =:exeDoctor , meetReport =:meetReport WHERE MeetClinicResultID =:meetClinicResultId";
                        Query q = ss.createQuery(sql);
                        q.setParameter("meetClinicResultId",
                                meetClinicResult.getMeetClinicResultId());
                        q.setParameter("exeOrgan",
                                meetClinicResult.getExeOrgan());
                        q.setParameter("exeDepart",
                                meetClinicResult.getExeDepart());
                        q.setParameter("exeDoctor",
                                meetClinicResult.getExeDoctor());
                        q.setParameter("meetReport",
                                meetClinicResult.getMeetReport());
                        q.executeUpdate();
                    }
                });
    }
}
