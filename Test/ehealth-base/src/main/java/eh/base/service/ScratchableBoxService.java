package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.ScratchableBoxDAO;
import eh.entity.base.ScratchableBox;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-12-06 16:43
 **/
public class ScratchableBoxService {
    public static final Logger log = Logger.getLogger(ScratchableBoxService.class);

    private ScratchableBoxDAO scratchableBoxDAO;

    public ScratchableBoxService() {
        scratchableBoxDAO = DAOFactory.getDAO(ScratchableBoxDAO.class);
    }


    /**
     * 获取所有九宫格模板
     * @return
     */
    @RpcService
    public List<ScratchableBox> findAllBox(String configType){
        if(StringUtils.isEmpty(configType)){
            throw new DAOException(DAOException.VALUE_NEEDED,"configType is require");
        }
       return scratchableBoxDAO.findAllBox(configType);
    }




}
