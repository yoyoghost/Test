package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.ConfigData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hwg on 2017/2/20.
 */
public abstract class DetectConfigInfoDAO extends HibernateSupportDelegateDAO<ConfigData> {
    private static final Logger logger = LoggerFactory.getLogger(DetectConfigInfoDAO.class);

    public DetectConfigInfoDAO() {
        super();
        this.setEntityName(ConfigData.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    public boolean invokeInfo(String data, String type){
        logger.info("信息: " + data + "业务类型: " + type);
        boolean f = false;
        if (data != null && type != null){
            ConfigData cd = getDataAndType(data,type);
            if (cd != null){
                f = true;
                return f;
            }
        }
        return f;
    }

    @RpcService
    public ConfigData invokeInfoForObject(String data, String type){
        logger.info("信息: " + data + "业务类型: " + type);
        if (data != null && type != null){
            ConfigData cd = getDataAndType(data,type);
            return cd;
        }
        return null;
    }


    @RpcService
    @DAOMethod(sql = "from ConfigData where data =:data and type =:type")
    public abstract ConfigData getDataAndType(@DAOParam("data")String data, @DAOParam("type")String type);

    @RpcService
    @DAOMethod(sql = "from ConfigData where  type =:type")
    public abstract ConfigData getByType(@DAOParam("type")String type);

}
