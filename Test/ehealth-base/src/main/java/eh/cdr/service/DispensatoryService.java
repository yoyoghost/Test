package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.cdr.dao.DispensatoryDAO;
import eh.entity.cdr.Dispensatory;

/**
 * Created by zhongzx on 2017/2/20 0020.
 */
@RpcBean(mvc_authentication = false)
public class DispensatoryService {

    @RpcService
    public Dispensatory getByDrugId(Integer drugId){
        DispensatoryDAO dispensatoryDAO = DAOFactory.getDAO(DispensatoryDAO.class);
        return dispensatoryDAO.getByDrugId(drugId);
    }
}
