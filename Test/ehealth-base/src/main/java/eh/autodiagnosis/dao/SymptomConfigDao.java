package eh.autodiagnosis.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.autodiagnosis.SymptomConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
@RpcSupportDAO("symptomConfigDao")
public abstract class SymptomConfigDao extends
        HibernateSupportDelegateDAO<SymptomConfig> {
    private static final Log logger = LogFactory.getLog(SymptomConfigDao.class);

    public SymptomConfigDao() {
        super();
        this.setEntityName(SymptomConfig.class.getName());
        this.setKeyField("id");
    }
    @DAOMethod(sql = "from SymptomConfig",limit = 0)
    public abstract List<SymptomConfig> findSymptomConfigs();




    @DAOMethod(sql = "from SymptomConfig where bodyPartId=:bodyPartId")
    public abstract List<SymptomConfig> findSymptomConfigByBodyPartId(Integer bodyPartId);
}

