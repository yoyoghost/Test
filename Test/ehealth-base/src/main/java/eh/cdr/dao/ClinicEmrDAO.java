package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.ClinicEmr;
import eh.entity.cdr.ClinicEmrDetail;
import eh.task.executor.SaveHisClinicEmrExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class ClinicEmrDAO extends HibernateSupportDelegateDAO<ClinicEmr> {

    private static final Logger logger = LoggerFactory.getLogger(ClinicEmrDAO.class);

    public ClinicEmrDAO() {
        super();
        this.setEntityName(ClinicEmr.class.getName());
        this.setKeyField("clinicEmrId");
    }

    /**
     * 门诊病历获取服务-从his导入到平台
     *
     * @author Qichengjian
     */
    @RpcService
    public void saveClinicEmr(List<ClinicEmr> list) {
        for (ClinicEmr clinicEmr : list) {
            Integer clinicId = clinicEmr.getClinicId();
            Integer clinicOrgan = clinicEmr.getClinicOrgan();
            if (clinicId != null && clinicOrgan != null){
                ClinicEmr ce = this.getByClinicIdAndClinicOrgan(clinicId,clinicOrgan);
                if (null == ce){
                    save(clinicEmr);
                }
            }
        }

    }

    /**
     * 患者的来自his的门诊病历
     *
     * @param mpiId       患者
     * @param clinicId    就诊序号
     * @param clinicOrgan 就诊机构
     */
    @DAOMethod(sql = "SELECT COUNT(*) FROM ClinicEmr where mpiId=:mpiId and clinicId=:clinicId and clinicOrgan =:clinicOrgan ")
    public abstract Long getClinicEmrCountByMpiAndClinicOrgan(@DAOParam("mpiId") String mpiId, @DAOParam("clinicId") Integer clinicId, @DAOParam("clinicOrgan") Integer clinicOrgan);

    /**
     * 判断患者的来自his的门诊病历是否存在
     *
     * @param clinicEmr 门诊病历Object
     * @return
     */
    public Boolean mpiExistClinicEmrByMpiAndClinicOrgan(ClinicEmr clinicEmr) {
        return this.getClinicEmrCountByMpiAndClinicOrgan(clinicEmr.getMpiId(), clinicEmr.getClinicId(), clinicEmr.getClinicOrgan()) > 0;
    }


    /**
     * 保存门诊记录列表
     *
     * @param clinicEmr
     * @param clinicEmrDetails
     * @author Qichengjian
     */
    @RpcService
    public void saveClinicEmrs(final ClinicEmr clinicEmr, final List<ClinicEmrDetail> clinicEmrDetails) throws DAOException {
        SaveHisClinicEmrExecutor executor = new SaveHisClinicEmrExecutor(clinicEmr, clinicEmrDetails);
        executor.execute();
    }

    @RpcService
    @DAOMethod(sql = "from ClinicEmr where clinicId=:clinicId and clinicOrgan=:clinicOrgan")
    public abstract ClinicEmr getByClinicIdAndClinicOrgan(@DAOParam("clinicId")Integer clinicId, @DAOParam("clinicOrgan")Integer clinicOrgan);


}
