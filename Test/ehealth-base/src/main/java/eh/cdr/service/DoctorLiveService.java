package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.cdr.dao.DoctorLiveDAO;
import eh.entity.base.Doctor;
import eh.entity.cdr.DoctorLive;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 医生直播间服务类
 * Created by liuya on 2017-7-6.
 */
public class DoctorLiveService {
    private static final Logger logger = LoggerFactory.getLogger(DoctorLiveService.class);


    //根据电话号码获取医生直播间信息
    @RpcService
    public DoctorLive getLivingByMobile(String mobile){
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByMobile(mobile);
        if(null == doctor){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctor is not exist");
        }
        DoctorLive doctorLive = doctorLiveDAO.getByDoctorId(doctor.getDoctorId());
        return doctorLive;
    }

    //新增直播间
    @RpcService
    public Integer saveDoctorLive(DoctorLive doctorLive){
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        DoctorLive dl = validateDoctorLive(doctorLive);
        if(null != doctorLiveDAO.getByDoctorId(dl.getDoctorId())){
            throw new DAOException("doctorLive is exist");
        }
        return doctorLiveDAO.saveOrUpdateDoctorLive(dl, false);
    }

    //更新医生直播间信息
    @RpcService
    public Integer updateDoctorLive(DoctorLive doctorLive){
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        validateDoctorLive(doctorLive);
        //若未新增过直播间则增加提示信息
        DoctorLive dl = doctorLiveDAO.getByDoctorId(doctorLive.getDoctorId());
        if(null == dl){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorLive is not exist");
        }
        doctorLive.setLiveId(dl.getLiveId());
        return doctorLiveDAO.saveOrUpdateDoctorLive(doctorLive, true);
    }

    //校验数据
    public DoctorLive validateDoctorLive(DoctorLive doctorLive){
        if(null == doctorLive){
            throw  new DAOException(DAOException.VALUE_NEEDED, "doctorLive is null");
        }
        if(StringUtils.isEmpty(doctorLive.getURL())){
            throw new DAOException(DAOException.VALUE_NEEDED, "URL is empty");
        }
        if(null == doctorLive.getDoctorId()){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is empty");
        }
        if(null == doctorLive.getStartDate()){
            throw new DAOException(DAOException.VALUE_NEEDED, "startDate is empty");
        }
        if(null == doctorLive.getEndDate()){
            throw new DAOException(DAOException.VALUE_NEEDED, "endDate is empty");
        }
        return doctorLive;
    }

    @RpcService
    public void updateURLByDoctorId(Integer doctorId, String URL){
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        if(null == doctorId){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is null");
        }
        DoctorLive doctorLive = doctorLiveDAO.getByDoctorId(doctorId);
        if(null == doctorLive){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorLive is not exist");
        }
        if(StringUtils.isEmpty(URL)){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorLiveURL is null");
        }
        doctorLiveDAO.updateURLByDoctorId(doctorId, URL);
    }

}
