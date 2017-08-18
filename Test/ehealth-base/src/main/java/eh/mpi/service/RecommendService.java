package eh.mpi.service;

import ctd.account.session.SessionItemManager;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.mpi.Patient;
import eh.entity.mpi.Recommend;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RecommendDAO;
import eh.msg.dao.SessionDetailDAO;
import eh.push.SmsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;


public class RecommendService {
    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);
    private RecommendDAO recommendDao = DAOFactory.getDAO(RecommendDAO.class);

    /**
     * sms发送消息时使用
     */
    @RpcService
    public Recommend getByRecommendId(Integer recommendId) {
        return recommendDao.get(recommendId);
    }

    /**
     * sms发送消息时使用
     */
    @RpcService
    public List<Recommend> findRecommendByDoctorIdAndRecommendType(Integer doctorId, Integer recommendType) {
        return recommendDao.findRecommendByDoctorIdAndRecommendType(doctorId, recommendType);
    }

    /**
     * sms发送消息时使用
     */
    @RpcService
    public void updateRecommendItWorksColumnByDoctorIdAndRecommendType(Integer doctorId, Integer recommendType) {
        recommendDao.updateRecommendItWorksColumnByDoctorIdAndRecommendType(doctorId, recommendType);
    }


    /**
     * 推荐开通推送
     *
     * @param type     开通业务类型--0特需预约1图文咨询2电话咨询3寻医问药
     * @param mpiId    患者主键
     * @param doctorId 被推荐医生Id
     * @desc 标题：推荐开通 正文：患者张三向您发送了一个请求，希望您可以开通特需预约业务。</br> 注：特需预约是向患者提供有偿的加号预约服务。
     * @author zhangx
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public void recommendOpenSet(int type, String mpiId, Integer doctorId) {
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        SessionDetailDAO detailDao = DAOFactory.getDAO(SessionDetailDAO.class);

        Long num = recommendDao.getRecommendNum(mpiId, doctorId, type);
        if (num > 0) {
            throw new DAOException(609, "您已经提醒过医生开通该业务");
        }

        Doctor doc = docDao.getByDoctorId(doctorId);
        Patient pat = patDao.get(mpiId);

        if (doc == null || pat == null) {
            throw new DAOException(600, "不存在患者[" + mpiId + "]或医生[" + doctorId
                    + "]");
        }

        // 保存记录
        Recommend commend = new Recommend();
        commend.setMpiId(mpiId);
        commend.setDoctorId(doctorId);
        commend.setRecommendType(type);
        try {
            commend.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        } catch (Exception e) {
            log.error("class[{}] method[{}] set deviceId error! errorMessage[{}]", this.getClass().getSimpleName(), "recommendOpenSet", e.getMessage());
        }
        commend.setCreateDate(new Date());
        Recommend saveCommend = recommendDao.save(commend);


        Integer clientId = null;
        int organId=doc.getOrgan()==null?0:doc.getOrgan().intValue();
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(saveCommend.getRecommendId(), organId, "RecommendOpenSet", "RecommendOpenSet", clientId);
    }
}
