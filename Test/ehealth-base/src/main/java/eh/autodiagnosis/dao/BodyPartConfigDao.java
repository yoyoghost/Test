package eh.autodiagnosis.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.autodiagnosis.BodyPartConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
@RpcSupportDAO("bodyPartConfigDao")
public abstract class BodyPartConfigDao extends
        HibernateSupportDelegateDAO<BodyPartConfig> {
    private static final Log logger = LogFactory.getLog(BodyPartConfigDao.class);

    public BodyPartConfigDao() {
        super();
        this.setEntityName(BodyPartConfig.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "from BodyPartConfig",limit = 0)
    public abstract List<BodyPartConfig> findBodyPartConfigs();

}

