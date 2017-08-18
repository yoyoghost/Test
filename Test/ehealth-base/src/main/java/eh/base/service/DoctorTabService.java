package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorTabDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorTab;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-14 14:28
 **/
public class DoctorTabService {

    @RpcService
    public  List<DoctorTab> findByDoctorId(Integer doctorId){
        if(doctorId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is require");
        }
        return DAOFactory.getDAO(DoctorTabDAO.class).findByDoctorId(doctorId);
    }


    @RpcService
    public  DoctorTab saveOrUpdateTab(DoctorTab tab){
        if(tab==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab is require");
        }
        Integer doctorId = tab.getDoctorId();
        if(doctorId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.doctorId is require");
        }
        Doctor doctor= DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
        if(doctor==null){
            throw new DAOException("医生不存在");
        }
        Integer paramType = tab.getParamType();
        if(paramType==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.paramType is require");
        }
        String paramTypeText = null;
        try {
            paramTypeText = DictionaryController.instance().get("eh.base.dictionary.ParamType").getText(paramType);
        } catch (ControllerException e) {
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.paramType is not exist");
        }
        String paramValue= tab.getParamValue();
        if(StringUtils.isEmpty(paramValue)){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.paramValue is require");
        }
        DoctorTabDAO doctorTabDAO = DAOFactory.getDAO(DoctorTabDAO.class);
        DoctorTab old = doctorTabDAO.getDoctorTabByDoctorIdAndParamType(doctorId,paramType);
        if(old==null){//create
            tab = doctorTabDAO.save(tab);
            BusActionLogService.recordBusinessLog("医生附加信息",tab.getId()+"","DoctorTab",doctor.getName()+"("+doctorId+")医生添加附加信息【"+paramTypeText+"】,值为"+paramValue);
        }else {//update
            String oldValue = old.getParamValue();
            if(!oldValue.equals(paramValue)){
                BusActionLogService.recordBusinessLog("医生附加信息",tab.getId()+"","DoctorTab",doctor.getName()+"("+doctorId+")医生更新附加信息【"+paramTypeText+"】,原值为:"+oldValue+"新值为:"+paramValue);
                old.setParamValue(paramValue);
                tab = doctorTabDAO.update(tab);
            }
        }
        return tab;
    }

    @RpcService
    public  void deleteTab(Integer id){
        if(id==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab is require");
        }
        DoctorTabDAO doctorTabDAO = DAOFactory.getDAO(DoctorTabDAO.class);
        DoctorTab tab = doctorTabDAO.get(id);
        if(tab==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"id is not exist");
        }

        Integer doctorId = tab.getDoctorId();
        if(doctorId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.doctorId is require");
        }
        Doctor doctor= DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
        if(doctor==null){
            throw new DAOException("医生不存在");
        }
        Integer paramType = tab.getParamType();
        if(paramType==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.paramType is require");
        }
        String paramTypeText = null;
        try {
            paramTypeText = DictionaryController.instance().get("eh.base.dictionary.ParamType").getText(paramType);
        } catch (ControllerException e) {
            throw new DAOException(DAOException.VALUE_NEEDED,"tab.paramType is require");
        }

        doctorTabDAO.remove(id);
        BusActionLogService.recordBusinessLog("医生附加信息",tab.getId()+"","DoctorTab",doctor.getName()+"("+doctorId+")医生删除附加信息【"+paramTypeText+"】");

    }





}

