package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointControlDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.bus.AppointControl;

/**
 * @author jianghc
 * @create 2017-03-31 15:45
 **/
public class AppointControlService {

    private AppointControlDAO appointControlDAO;

    public AppointControlService() {
        appointControlDAO = DAOFactory.getDAO(AppointControlDAO.class);
    }

    @RpcService
    public QueryResult<AppointControl> queryAppointControl(Integer objType,Integer objId,int start,int limit) {
       return appointControlDAO.queryAppointControl(objType,objId,start,limit);
    }
    @RpcService
    public AppointControl saveOrUpdateOne(AppointControl appointControl){
        if(appointControl==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"appointControl is require");
        }
        if(appointControl.getObjType()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"ObjType is require");
        }
        if(appointControl.getObjId()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"ObjId is require");
        }
        if(appointControl.getObjName()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"ObjName is require");
        }
        AppointControl target = appointControlDAO.getByObjTypeAndObjId(appointControl.getObjType(),appointControl.getObjId());
        if(target==null){
            appointControl.setCycle(0);
            appointControl = appointControlDAO.save(appointControl);
            BusActionLogService.recordBusinessLog("机构预约管理",appointControl.getId().toString()
                    ,"AppointControl","新增："+appointControl.toString());
        }else{
            //BeanUtils.map(appointControl,target);
            appointControl.setId(target.getId());
            appointControl= appointControlDAO.update(appointControl);
            BusActionLogService.recordBusinessLog("机构预约管理",appointControl.getId().toString()
                    ,"AppointControl","更新："+target.toString()+"更新为"+appointControl.toString());
        }
        return appointControl;
    }


    @RpcService
    public void removeOne(Integer id){
        if(id==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"id is require");
        }
        AppointControl appointControl =appointControlDAO.get(id);
        if(appointControl==null){
            throw new DAOException("id is not exist");
        }
        BusActionLogService.recordBusinessLog("机构预约管理",appointControl.getId().toString()
                ,"AppointControl","删除："+appointControl.toString());
        appointControlDAO.remove(id);
    }

    @RpcService
    public Boolean checkAppoint(Integer id){
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        return appointControlDAO.checkAppoint(appointSourceDAO.get(id));
    }
}
