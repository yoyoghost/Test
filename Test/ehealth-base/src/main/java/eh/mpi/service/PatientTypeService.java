package eh.mpi.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.mpi.PatientType;
import eh.mpi.dao.PatientTypeDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author jianghc
 * @create 2017-02-09 17:11
 **/
public class PatientTypeService {
    private static final Log logger = LogFactory.getLog(PatientService.class);

    private PatientTypeDAO patientTypeDAO;

    private final static String dicName = "eh.mpi.dictionary.PatientType";

    public PatientTypeService() {
        patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
    }

    @RpcService
    public PatientType addOrUpdateOnepatientType(PatientType type) {
        if (type == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientType is require");
        }
        String key = type.getKey() == null ? "" : type.getKey();
        String text = type.getText() == null ? "" : type.getText();
        if (StringUtils.isEmpty(key.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientType.key is require");
        }
        if (StringUtils.isEmpty(text.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientType.text is require");
        }
        if (type.getInputCardNo() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientType.inputCardNo is require");
        }
        String addreArea = type.getAddrArea() == null ? "" : type.getAddrArea();
        type.setKey(key);
        type.setText(text);
        type.setAddrArea(addreArea);
        PatientType oldType = patientTypeDAO.get(key);
        if (oldType == null) {//新增
            type = patientTypeDAO.save(type);
            BusActionLogService.recordBusinessLog("医保类型管理", type.getKey(), "PatientType", "新增：" + type.toString());
        } else {//更新
            type = patientTypeDAO.update(type);
            BusActionLogService.recordBusinessLog("医保类型管理", type.getKey(), "PatientType", "更新:原" + oldType.toString() + "更新为" + type.toString());
        }
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            logger.error("patientType 字典缓存刷新失败："+e.getMessage());
        }
        return type;
    }

    @RpcService
    public void deleteByKey(String key) {
        if (key == null || StringUtils.isEmpty(key.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "key is require");
        }
        PatientType type = patientTypeDAO.get(key);
        if(type==null){
            throw new DAOException( "this key is not exist");
        }
        patientTypeDAO.remove(key);
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            logger.error("patientType 字典缓存刷新失败："+e.getMessage());
        }
        BusActionLogService.recordBusinessLog("医保类型管理", type.getKey(), "PatientType", "删除:"+type.toString());
    }

    @RpcService
    public QueryResult<PatientType> queryByStartAndLimit(final int start, final int limit) {
        return patientTypeDAO.queryByStartAndLimit(start,limit);
    }

    @RpcService
    public PatientType getByKey(String key){
        if (key == null || StringUtils.isEmpty(key.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "key is require");
        }
        return patientTypeDAO.get(key);
    }




}

