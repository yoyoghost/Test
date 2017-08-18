package eh.base.dao;


import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.base.CloudImageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by zhongzx on 2017/5/18 0018.
 */
@RpcSupportDAO
public abstract class CloudImageConfigDAO extends HibernateSupportDelegateDAO<CloudImageConfig> {

    private static final Logger log = LoggerFactory.getLogger(CloudImageConfigDAO.class);

    public CloudImageConfigDAO() {
        super();
        this.setEntityName(CloudImageConfig.class.getName());
        this.setKeyField("Id");
    }

    /**
     * 根据机构和客户端获取 云影像配置信息
     * @param organId
     * @param client
     * @return
     */
    @DAOMethod
    public abstract CloudImageConfig getByOrganIdAndClient(Integer organId, String client);

}
