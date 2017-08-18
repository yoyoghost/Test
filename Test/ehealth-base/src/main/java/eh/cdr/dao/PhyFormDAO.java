package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.cdr.PhyForm;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Created by zhongzx on 2016/8/25 0025.
 * 体检单DAO
 */
public abstract class PhyFormDAO extends HibernateSupportDelegateDAO<PhyForm>{
    private static final Log logger = LogFactory.getLog(PhyFormDAO.class);

    public PhyFormDAO(){
        super();
        this.setEntityName(PhyForm.class.getName());
        this.setKeyField("phyId");
    }

    @DAOMethod(sql = "from PhyForm where organId=:organId and examNo=:examNo and mpiId=:mpiId")
    public abstract PhyForm getByOrganIdAndExamNoAndMpiId(@DAOParam("organId") Integer organId, @DAOParam("examNo")String examNo, @DAOParam("mpiId") String mpiId);

    @DAOMethod
    public abstract PhyForm getByPhyId(Integer phyId);
}
