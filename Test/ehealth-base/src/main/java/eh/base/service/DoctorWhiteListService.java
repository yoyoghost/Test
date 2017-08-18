package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorWhiteListDAO;
import eh.bus.service.ConsultSetService;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorWhiteList;
import eh.mpi.service.sign.RequestSignRecordService;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;

/**
 * @author jianghc
 * @create 2017-02-17 16:59
 **/
public class DoctorWhiteListService {
    private DoctorWhiteListDAO doctorWhiteListDAO;

    public DoctorWhiteListService() {
        this.doctorWhiteListDAO = DAOFactory.getDAO(DoctorWhiteListDAO.class);
    }

    @RpcService
    public DoctorWhiteList saveOneItem(DoctorWhiteList doctorWhiteList){
        if(doctorWhiteList == null){
            throw new DAOException(DAOException.VALUE_NEEDED,"DoctorWhiteList is require");
        }
        Integer type = doctorWhiteList.getType();
        if( type== null){
            throw new DAOException(DAOException.VALUE_NEEDED,"DoctorWhiteList.type is require");
        }
        Integer doctorId = doctorWhiteList.getDoctorId();
        if(doctorId == null){
            throw new DAOException(DAOException.VALUE_NEEDED,"DoctorWhiteList.doctorId is require");
        }
        if(doctorWhiteListDAO.getByTypeAndDoctorId(type,doctorId)!=null){
            throw new DAOException("DoctorWhiteList is exist");
        }

        doctorWhiteList = doctorWhiteListDAO.save(doctorWhiteList);
        if(doctorWhiteList.getType().intValue()==1){//专家解读,添加白名单的时候将对于医生的专家解读开关打开
            ConsultSetService consultSetService = AppContextHolder.getBean("consultSetService", ConsultSetService.class);
            consultSetService.openProfessorConsultInfo(doctorId);
        }
        BusActionLogService.recordBusinessLog("白名单管理",doctorWhiteList.getId().toString(), "DoctorWhiteList",
                "添加医生ID["+doctorId+"]至白名单[type="+type+"]");
        return doctorWhiteList;
    }
    @RpcService
    public void deleteByTypeAndDoctorId(Integer type,Integer doctorId){
        if( type== null){
            throw new DAOException(DAOException.VALUE_NEEDED,"type is require");
        }
        if(doctorId == null){
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is require");
        }
        DoctorWhiteList doctorWhiteList =doctorWhiteListDAO.getByTypeAndDoctorId(type,doctorId);
        if(doctorWhiteList==null){
            throw new DAOException("DoctorWhiteList is not exist");
        }
        if(doctorWhiteList.getType().intValue()==1){//专家解读,移除白名单的时候将对于医生的专家解读开关关闭
            ConsultSetService consultSetService = AppContextHolder.getBean("consultSetService", ConsultSetService.class);
            consultSetService.closeProfessorStatus(doctorId);
        }
        BusActionLogService.recordBusinessLog("白名单管理",doctorWhiteList.getId().toString(), "DoctorWhiteList",
                "删除医生ID["+doctorId+"]从白名单[type="+type+"]");
        doctorWhiteListDAO.remove(doctorWhiteList.getId());
    }

    @RpcService
    public QueryResult<Doctor> queryByType(Integer type,Integer start,Integer  limit){
        return doctorWhiteListDAO.queryByType(type,start,limit);
    }

    /**
     * 获取是否有白名单权限
     *
     * @param doctorId
     * @return true有白名单权限 false没有白名单权限
     * 2017-2-21 wx2.8 增加专家解读白名单权限，如果有则显示业务设置
     */
    public Boolean getDoctorWhiteListFlag(Integer type,Integer doctorId) {
        //PROFESSOR_CONSULT_STATUS 默认是否开发所有医生(true开发；false不开放)
        Boolean flag=Boolean.parseBoolean(ParamUtils.getParam(ParameterConstant.KEY_PROFESSOR_CONSULT_STATUS,"false"));
        if(flag){
            return flag;
        }
        DoctorWhiteListDAO whiteDao=DAOFactory.getDAO(DoctorWhiteListDAO.class);
        DoctorWhiteList white=whiteDao.getByTypeAndDoctorId(type,doctorId);
        if(white!=null){
            flag=true;
        }
        return flag;
    }
}
