package eh.autodiagnosis.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.autodiagnosis.DiseaseConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
@RpcSupportDAO("diseaseConfigDao")
public abstract class DiseaseConfigDao extends
        HibernateSupportDelegateDAO<DiseaseConfig> {
    private static final Log logger = LogFactory.getLog(DiseaseConfigDao.class);

    public DiseaseConfigDao() {
        super();
        this.setEntityName(DiseaseConfig.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "from DiseaseConfig",limit = 0)
    public abstract List<DiseaseConfig> findDiseaseConfigs();
    //ids最长为5个，没有性能问题
    @DAOMethod(sql = "select distinct(departNo) from DiseaseConfig where id in :ids")
    public abstract List<String> findDepartNos(@DAOParam("ids") List<Integer> ids);

}

